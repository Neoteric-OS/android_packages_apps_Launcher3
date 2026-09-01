/*
 * Copyright (C) 2026 Neoteric OS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.preview;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.deviceprofile.AllAppsProfile;
import com.android.launcher3.deviceprofile.WorkspaceProfile;
import com.android.settingslib.widget.GroupSectionDividerMixin;
import com.android.settingslib.widget.SliderPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A swipeable preview of sample app icons at the sizes the workspace and the app drawer will use,
 * so icon and label size can be judged without leaving Settings. This follows the preview on
 * Settings > Display > Display size and text, which likewise pages through real content rather
 * than mirroring the screen.
 *
 * <p>Sizes come from the live {@link DeviceProfile}, so anything feeding into
 * {@link InvariantDeviceProfile} is reflected here. Two things drive a redraw:
 *
 * <ul>
 *   <li>{@link InvariantDeviceProfile.OnIDPChangeListener}, which fires once a value has been
 *       persisted and the profile recomputed. The label toggles arrive this way too.
 *   <li>{@link SliderPreference#setExtraChangeListener}, which reports every step of a drag. A
 *       slider only persists when the finger lifts, so without this the preview would lag behind
 *       the gesture. Until the value lands, the profile's sizes are scaled by the pending
 *       percentage - the same split Settings uses, where the preview moves live but the value is
 *       only committed on release.
 * </ul>
 *
 * <p>Paging uses a {@link RecyclerView} with a {@link PagerSnapHelper} rather than a ViewPager, as
 * RecyclerView is already a Launcher dependency and there are only ever two pages.
 */
public class IconPreviewPreference extends Preference implements GroupSectionDividerMixin {

    private static final int PAGE_HOME = 0;
    private static final int PAGE_DRAWER = 1;
    private static final int PAGE_COUNT = 2;

    /** Rows shown per page. */
    private static final int PREVIEW_ROWS = 2;
    /** Enough sample apps to fill two rows of the widest grid either surface is likely to use. */
    private static final int MAX_SAMPLE_APPS = 24;

    /** Rough line box of a label, as a multiple of its text size. */
    private static final float LABEL_LINE_HEIGHT = 1.4f;

    private final InvariantDeviceProfile.OnIDPChangeListener mIdpListener =
            modelPropertiesChanged -> {
                // The value landed, so the profile now carries it and the pending one is stale.
                mPendingIconPct = -1;
                mPendingFontPct = -1;
                refresh(true);
            };

    @Nullable private RecyclerView mPager;
    @Nullable private TextView mPageTitle;
    @Nullable private LinearLayout mDots;
    @Nullable private List<SampleApp> mApps;

    private boolean mListening;
    private int mCurrentPage = PAGE_HOME;

    /** Largest percentage the sliders allow; pages are sized for it so the card never resizes. */
    private int mMaxPct = 200;

    /** Percentages being dragged towards but not yet persisted; -1 means "use the stored value". */
    private int mPendingIconPct = -1;
    private int mPendingFontPct = -1;

    public IconPreviewPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_icon_preview);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);

        mPageTitle = (TextView) holder.itemView.findViewById(R.id.icon_preview_page_title);
        mDots = (LinearLayout) holder.itemView.findViewById(R.id.icon_preview_dots);
        mPager = (RecyclerView) holder.itemView.findViewById(R.id.icon_preview_pager);

        if (mPager.getAdapter() == null) {
            mPager.setLayoutManager(
                    new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            new PagerSnapHelper().attachToRecyclerView(mPager);
            mPager.setItemViewCacheSize(0);
            mPager.setAdapter(new PageAdapter());
            mPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                    LinearLayoutManager lm = (LinearLayoutManager) view.getLayoutManager();
                    int page = lm == null ? -1 : lm.findFirstCompletelyVisibleItemPosition();
                    if (page != RecyclerView.NO_POSITION && page != mCurrentPage) {
                        mCurrentPage = page;
                        updateChrome();
                    }
                }
            });
        }
        buildDots();
        updateChrome();
        refresh();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (!mListening) {
            InvariantDeviceProfile.INSTANCE.get(getContext()).addOnChangeListener(mIdpListener);
            watchSlider(LauncherPrefs.ICON_SIZE.getSharedPrefKey(), pct -> mPendingIconPct = pct);
            watchSlider(LauncherPrefs.FONT_SIZE.getSharedPrefKey(), pct -> mPendingFontPct = pct);
            mListening = true;
        }
    }

    @Override
    public void onDetached() {
        if (mListening) {
            InvariantDeviceProfile.INSTANCE.get(getContext()).removeOnChangeListener(mIdpListener);
            watchSlider(LauncherPrefs.ICON_SIZE.getSharedPrefKey(), null);
            watchSlider(LauncherPrefs.FONT_SIZE.getSharedPrefKey(), null);
            mListening = false;
        }
        mPager = null;
        mPageTitle = null;
        mDots = null;
        super.onDetached();
    }

    private void refresh() {
        refresh(false);
    }

    /**
     * Re-lays out the pages, keeping the card at the height the largest sizes need.
     *
     * @param rebindAll rebinds pages that are off screen too. Off by default so that dragging a
     *                  slider only touches the visible page, which is the hot path.
     */
    private void refresh(boolean rebindAll) {
        RecyclerView pager = mPager;
        if (pager == null) {
            return;
        }
        if (pager.getWidth() == 0) {
            pager.post(() -> refresh(rebindAll));
            return;
        }

        int height = 0;
        for (int page = 0; page < PAGE_COUNT; page++) {
            height = Math.max(height, measurePage(page, pager.getWidth()));
        }
        if (pager.getLayoutParams().height != height) {
            pager.getLayoutParams().height = height;
            pager.requestLayout();
        }

        RecyclerView.Adapter<?> adapter = pager.getAdapter();
        if (rebindAll && adapter != null) {
            // notifyDataSetChanged throws if the pager is mid-layout or still settling.
            if (pager.isComputingLayout() || pager.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                pager.post(() -> refresh(true));
            } else {
                adapter.notifyDataSetChanged();
            }
            return;
        }
        for (int i = 0; i < pager.getChildCount(); i++) {
            View child = pager.getChildAt(i);
            int page = pager.getChildAdapterPosition(child);
            if (page != RecyclerView.NO_POSITION) {
                bindPage((LinearLayout) child.findViewById(R.id.icon_preview_grid), page,
                        pager.getWidth());
            }
        }
    }

    /** Height a page needs with every size pushed to the slider maximum. */
    private int measurePage(int page, int pagerWidth) {
        PageSpec spec = new PageSpec(getContext(), page, pagerWidth, mMaxPct, mMaxPct);
        int rowSpacing = getContext().getResources()
                .getDimensionPixelSize(R.dimen.icon_preview_row_spacing);
        int rowHeight = spec.iconPx + spec.drawablePaddingPx
                + Math.round(spec.textPx * LABEL_LINE_HEIGHT);
        return PREVIEW_ROWS * rowHeight + (PREVIEW_ROWS - 1) * rowSpacing;
    }

    private void bindPage(@Nullable LinearLayout grid, int page, int pagerWidth) {
        if (grid == null) {
            return;
        }
        Context context = getContext();
        PageSpec spec = new PageSpec(context, page, pagerWidth,
                mPendingIconPct < 0 ? LauncherPrefs.ICON_SIZE.get(context) : mPendingIconPct,
                mPendingFontPct < 0 ? LauncherPrefs.FONT_SIZE.get(context) : mPendingFontPct);

        if (mApps == null) {
            mApps = loadSampleApps(context, MAX_SAMPLE_APPS);
        }
        int rowSpacing = context.getResources()
                .getDimensionPixelSize(R.dimen.icon_preview_row_spacing);

        grid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int row = 0; row < PREVIEW_ROWS; row++) {
            LinearLayout rowView = new LinearLayout(context);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) {
                rowLp.topMargin = rowSpacing;
            }
            grid.addView(rowView, rowLp);

            for (int column = 0; column < spec.columns; column++) {
                // Every cell is inflated, so a short row keeps the others aligned.
                View item = inflater.inflate(R.layout.icon_preview_app, rowView, false);
                item.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                int index = row * spec.columns + column;
                if (index < mApps.size()) {
                    SampleApp app = mApps.get(index);

                    ImageView icon = item.findViewById(android.R.id.icon1);
                    ViewGroup.LayoutParams iconLp = icon.getLayoutParams();
                    iconLp.width = spec.iconPx;
                    iconLp.height = spec.iconPx;
                    // setLayoutParams, not requestLayout, so the icon is marked for measure.
                    icon.setLayoutParams(iconLp);
                    icon.setImageDrawable(app.icon());

                    TextView label = item.findViewById(android.R.id.text1);
                    label.setVisibility(spec.showLabels ? View.VISIBLE : View.INVISIBLE);
                    label.setTextSize(TypedValue.COMPLEX_UNIT_PX, spec.textPx);
                    label.setPadding(0, spec.drawablePaddingPx, 0, 0);
                    label.setText(app.label());
                } else {
                    item.setVisibility(View.INVISIBLE);
                }
                rowView.addView(item);
            }
        }
    }

    /** Everything a page needs to draw itself, resolved for one set of percentages. */
    private static final class PageSpec {
        final int columns;
        final int iconPx;
        final float textPx;
        final int drawablePaddingPx;
        final boolean showLabels;

        PageSpec(Context context, int page, int pagerWidth, int iconPct, int fontPct) {
            InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
            DeviceProfile dp = idp.getDeviceProfile(context);

            int storedIconPct = Math.max(1, LauncherPrefs.ICON_SIZE.get(context));
            int storedFontPct = Math.max(1, LauncherPrefs.FONT_SIZE.get(context));

            int baseIconPx;
            float baseTextPx;
            int cellWidthPx;
            if (page == PAGE_DRAWER) {
                AllAppsProfile allApps = dp.getAllAppsProfile();
                columns = Math.max(1, idp.numAllAppsColumns);
                baseIconPx = allApps.getIconSizePx();
                baseTextPx = allApps.getIconTextSizePx();
                cellWidthPx = Math.max(1, allApps.getCellWidthPx());
                drawablePaddingPx = allApps.getIconDrawablePaddingPx();
                showLabels = LauncherPrefs.SHOW_DRAWER_LABELS.get(context);
            } else {
                WorkspaceProfile workspace = dp.getWorkspaceProfile();
                columns = Math.max(1, idp.numColumns);
                baseIconPx = workspace.getIconSizePx();
                baseTextPx = workspace.getIconTextSizePx();
                cellWidthPx = Math.max(1, workspace.getCellWidthPx());
                drawablePaddingPx = workspace.getIconDrawablePaddingPx();
                showLabels = LauncherPrefs.SHOW_DESKTOP_LABELS.get(context);
            }

            // Divide out the stored percentage, then apply the one being shown. The workspace
            // never lets an icon outgrow its cell, so neither can the preview.
            float unscaledIconPx = baseIconPx * 100f / storedIconPct;
            float unscaledTextPx = baseTextPx * 100f / storedFontPct;
            float wantIconPx = Math.min(unscaledIconPx * iconPct / 100f, cellWidthPx);
            float wantTextPx = unscaledTextPx * fontPct / 100f;

            // The card is narrower than the screen, so its cells are narrower than real ones.
            // Shrinking by that ratio keeps the gaps between icons true to the real surface.
            float fit = Math.min(1f, (pagerWidth / (float) columns) / cellWidthPx);
            iconPx = Math.round(wantIconPx * fit);
            textPx = wantTextPx * fit;
        }
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View page = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.icon_preview_page, parent, false);
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PageHolder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            RecyclerView pager = mPager;
            bindPage(holder.grid, position, pager == null ? 0 : pager.getWidth());
        }

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final LinearLayout grid;

        PageHolder(@NonNull View itemView) {
            super(itemView);
            grid = itemView.findViewById(R.id.icon_preview_grid);
        }
    }

    private void buildDots() {
        LinearLayout dots = mDots;
        if (dots == null || dots.getChildCount() == PAGE_COUNT) {
            return;
        }
        dots.removeAllViews();
        int size = getContext().getResources()
                .getDimensionPixelSize(R.dimen.icon_preview_dot_size);
        int spacing = getContext().getResources()
                .getDimensionPixelSize(R.dimen.icon_preview_dot_spacing);
        for (int i = 0; i < PAGE_COUNT; i++) {
            View dot = new View(getContext());
            dot.setBackgroundResource(R.drawable.icon_preview_dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? 0 : spacing);
            dots.addView(dot, lp);
        }
    }

    /** Keeps the page title and the dots in step with the visible page. */
    private void updateChrome() {
        if (mPageTitle != null) {
            mPageTitle.setText(mCurrentPage == PAGE_DRAWER
                    ? R.string.icon_preview_drawer_label
                    : R.string.icon_preview_home_label);
        }
        if (mDots != null) {
            for (int i = 0; i < mDots.getChildCount(); i++) {
                mDots.getChildAt(i).setAlpha(i == mCurrentPage ? 1f : 0.3f);
            }
        }
    }

    /** Follows a slider's live value so the preview tracks the drag; pass null to stop. */
    private void watchSlider(String key, @Nullable IntConsumer onValue) {
        Preference pref = getPreferenceManager() == null
                ? null : getPreferenceManager().findPreference(key);
        if (!(pref instanceof SliderPreference slider)) {
            return;
        }
        mMaxPct = Math.max(mMaxPct, slider.getMax());
        slider.setExtraChangeListener(onValue == null ? null : (s, value, fromUser) -> {
            onValue.accept((int) value);
            refresh();
        });
    }

    /** The first few launchable apps, ordered by label, as sample content. */
    private static List<SampleApp> loadSampleApps(Context context, int count) {
        PackageManager pm = context.getPackageManager();
        Intent launchables = new Intent(Intent.ACTION_MAIN, null)
                .addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> infos = pm.queryIntentActivities(launchables, 0);
        infos.sort((a, b) -> String.valueOf(a.loadLabel(pm))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));

        List<SampleApp> apps = new ArrayList<>();
        for (ResolveInfo info : infos) {
            CharSequence label = info.loadLabel(pm);
            if (label != null) {
                apps.add(new SampleApp(label.toString(), info.loadIcon(pm)));
            }
            if (apps.size() >= count) {
                break;
            }
        }
        return apps;
    }

    private record SampleApp(String label, Drawable icon) { }
}
