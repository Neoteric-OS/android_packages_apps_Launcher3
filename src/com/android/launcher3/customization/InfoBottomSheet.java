package com.android.launcher3.customization;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.AppReloader;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.views.AbstractSlideInView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Material 3 bottom sheet showing app details for the "App info" long-press shortcut.
 *
 * <p>Rides on {@link AbstractSlideInView}, which already provides the bottom sheet interaction
 * (scrim, slide in, drag to dismiss). The rows are plain views: Launcher is not a
 * FragmentActivity, so androidx preferences cannot be hosted here, and five fixed rows do not
 * need a RecyclerView behind them.
 */
public class InfoBottomSheet extends AbstractSlideInView<Launcher> implements Insettable {
    private static final int DEFAULT_CLOSE_DURATION = 200;

    private ViewGroup mRows;
    private View mIconPackRow;

    private Context mViewContext;
    private Rect mSourceBounds;

    private ItemInfo mItemInfo;
    private ComponentName mComponent;
    private ComponentKey mKey;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
    }

    /** Records where the sheet was opened from, for the "More" details activity transition. */
    public void configureBottomSheet(Rect sourceBounds, Context context) {
        mSourceBounds = sourceBounds;
        mViewContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.app_info_sheet);
        mRows = findViewById(R.id.app_info_rows);
        setContentBackgroundWithParent(
                getContext().getDrawable(R.drawable.bg_rounded_corner_bottom_sheet), mContent);
    }

    @Override
    public void setInsets(Rect insets) {
        // Consume the insets here rather than letting the drag layer turn them into a bottom
        // margin: the sheet has to reach the bottom of the screen so its background runs under
        // the navigation bar, with the content padded clear of it instead.
        if (mContent != null) {
            mContent.setPadding(mContent.getPaddingLeft(), mContent.getPaddingTop(),
                    mContent.getPaddingRight(), insets.bottom);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Content is laid out as center bottom aligned.
        int contentWidth = mContent.getMeasuredWidth();
        int contentLeft = (r - l - contentWidth) / 2;
        mContent.layout(contentLeft, b - t - mContent.getMeasuredHeight(),
                contentLeft + contentWidth, b - t);

        setTranslationShift(mTranslationShift);
    }

    public void populateAndShow(ItemInfo itemInfo) {
        mItemInfo = itemInfo;
        mComponent = itemInfo.getTargetComponent();
        mKey = new ComponentKey(mComponent, itemInfo.user);

        ((TextView) findViewById(R.id.title)).setText(itemInfo.title);

        View version = addRow(R.drawable.ic_app_info_version, R.string.app_info_version);
        View lastUpdate = addRow(R.drawable.ic_app_info_last_update, R.string.app_info_last_update);
        View source = addRow(R.drawable.ic_app_info_source, R.string.app_info_source);
        mIconPackRow = addRow(R.drawable.ic_app_info_icon_pack, R.string.icon_pack_title);
        View more = addRow(R.drawable.ic_info_no_shadow, R.string.app_info_more);

        setSummary(mIconPackRow, currentIconPackLabel());
        mIconPackRow.setOnClickListener(v -> showIconPackPicker());
        more.setOnClickListener(v -> {
            PackageManagerHelper.startDetailsActivityForInfo(
                    mViewContext != null ? mViewContext : getContext(), mItemInfo, mSourceBounds,
                    ActivityOptions.makeBasic().toBundle());
            // Leaves the launcher, so close for the same reason openSource does.
            close(false);
        });

        THREAD_POOL_EXECUTOR.execute(() -> {
            MetadataExtractor extractor = new MetadataExtractor(getContext(), mComponent);
            CharSequence sourceLabel = extractor.getSource();
            CharSequence lastUpdateLabel = extractor.getLastUpdate();
            CharSequence versionLabel = getContext().getString(R.string.app_info_version_value,
                    extractor.getVersionName(), extractor.getVersionCode());
            Intent marketIntent = extractor.getMarketIntent();

            MAIN_EXECUTOR.execute(() -> {
                setSummary(version, versionLabel);
                setSummary(lastUpdate, lastUpdateLabel);
                setSummary(source, sourceLabel);
                if (marketIntent != null) {
                    source.setOnClickListener(v -> openSource(v, marketIntent));
                }
            });
        });

        attachToContainer();
        mIsOpen = false;
        if (!mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            mIsOpen = true;
            setUpDefaultOpenAnimation().start();
        }
    }

    private View addRow(int iconRes, int titleRes) {
        View row = LayoutInflater.from(getContext())
                .inflate(R.layout.app_info_row, mRows, false);
        ((ImageView) row.findViewById(android.R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(android.R.id.title)).setText(titleRes);
        row.findViewById(android.R.id.summary).setVisibility(GONE);
        mRows.addView(row);
        return row;
    }

    private void setSummary(View row, CharSequence summary) {
        TextView view = row.findViewById(android.R.id.summary);
        view.setText(summary);
        view.setVisibility(VISIBLE);
    }

    /**
     * Opens the store the app was installed from. The sheet closes on the way out, otherwise it
     * is still up behind the app when the launch animation is reversed on the way back.
     */
    private void openSource(View row, Intent intent) {
        try {
            Launcher.getLauncher(getContext()).startActivity(intent,
                    getAppTransitionManager().getActivityLaunchOptions(row, mItemInfo).toBundle());
            close(false);
        } catch (Exception ignored) {
        }
    }

    /**
     * The sheet only ever shows over the launcher, so it reuses the launcher's transition manager
     * rather than building (and registering remote animations for) a second one.
     */
    private QuickstepTransitionManager getAppTransitionManager() {
        return ((QuickstepLauncher) Launcher.getLauncher(getContext())).getAppTransitionManager();
    }

    private String currentIconPackLabel() {
        String pack = IconDatabase.getByComponent(getContext(), mKey);
        if (IconDatabase.VALUE_DEFAULT.equals(pack)) {
            return getContext().getString(R.string.icon_pack_default_label);
        }
        CharSequence name = IconPackManager.get(getContext()).getProviderNames().get(pack);
        return name != null ? name.toString()
                : getContext().getString(R.string.icon_pack_default_label);
    }

    /** Packs that provide an icon for this app, plus the "system default" entry at the top. */
    private void showIconPackPicker() {
        IconPackManager manager = IconPackManager.get(getContext());
        Map<String, CharSequence> packs = manager.getProviderNames();
        for (String pkg : new HashSet<>(packs.keySet())) {
            if (!manager.packContainsActivity(pkg, mComponent)) {
                packs.remove(pkg);
            }
        }

        String global = IconDatabase.getGlobal(getContext());
        List<Map.Entry<String, CharSequence>> sorted = new ArrayList<>(packs.entrySet());
        sorted.sort((a, b) -> a.getValue().toString().toLowerCase()
                .compareTo(b.getValue().toString().toLowerCase()));

        CharSequence[] labels = new CharSequence[sorted.size() + 1];
        String[] values = new String[labels.length];
        labels[0] = getContext().getString(R.string.icon_pack_default_label);
        values[0] = packs.containsKey(global) ? IconDatabase.VALUE_DEFAULT : global;
        for (int i = 0; i < sorted.size(); i++) {
            labels[i + 1] = sorted.get(i).getValue();
            values[i + 1] = sorted.get(i).getKey();
        }

        String current = IconDatabase.getByComponent(getContext(), mKey);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.icon_pack_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setIconPack(values[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void setIconPack(String pack) {
        if (pack.equals(IconDatabase.getGlobal(getContext()))) {
            IconDatabase.resetForComponent(getContext(), mKey);
        } else {
            IconDatabase.setForComponent(getContext(), mKey, pack);
        }
        AppReloader.get(getContext()).reload(mKey);
        setSummary(mIconPackRow, currentIconPackLabel());
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    public boolean isOfType(int type) {
        return (type & TYPE_WIDGETS_BOTTOM_SHEET) != 0;
    }
}
