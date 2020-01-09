package com.android.launcher3.customization;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.util.PackageManagerHelper;

/**
 * Bottom sheet showing app details for the "App info" long-press shortcut.
 *
 * A17 removed WidgetsBottomSheet, so this sheet sits on AbstractSlideInView directly and only
 * keeps the parts it used: a bottom aligned content view with the default open/close animation.
 */
public class InfoBottomSheet extends AbstractSlideInView<Launcher> implements Insettable {
    private static final int DEFAULT_CLOSE_DURATION = 200;

    private final FragmentManager mFragmentManager;
    protected static Rect mSourceBounds;
    protected static Context mViewContext;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
    }

    public void configureBottomSheet(Rect sourceBounds, Context context) {
        mSourceBounds = sourceBounds;
        mViewContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.widgets_bottom_sheet);
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
        ((TextView) findViewById(R.id.title)).setText(itemInfo.title);

        PrefsFragment fragment =
                (PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs);
        fragment.loadForApp(itemInfo);

        attachToContainer();
        mIsOpen = false;
        if (!mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            mIsOpen = true;
            setUpDefaultOpenAnimation().start();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentById(R.id.sheet_prefs);
        if (pf != null) {
            mFragmentManager.beginTransaction()
                    .remove(pf)
                    .commitAllowingStateLoss();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    public boolean isOfType(int type) {
        return (type & TYPE_WIDGETS_BOTTOM_SHEET) != 0;
    }

    public static class PrefsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private static final String KEY_ICON_PACK = "pref_app_info_icon_pack";
        private static final String KEY_SOURCE = "pref_app_info_source";
        private static final String KEY_LAST_UPDATE = "pref_app_info_last_update";
        private static final String KEY_VERSION = "pref_app_info_version";
        private static final String KEY_MORE = "pref_app_info_more";

        private Context mContext;

        private ItemInfo mItemInfo;

        private ComponentName mComponent;
        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContext = getActivity();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.app_info_preferences);
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView view = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return view;
        }

        public void loadForApp(ItemInfo itemInfo) {
            mComponent = itemInfo.getTargetComponent();
            mItemInfo = itemInfo;
            mKey = new ComponentKey(mComponent, itemInfo.user);
            MetadataExtractor extractor = new MetadataExtractor(mContext, mComponent);

            Preference iconPack = findPreference(KEY_ICON_PACK);
            iconPack.setOnPreferenceChangeListener(this);
            iconPack.setSummary(R.string.app_info_icon_pack_none);
            findPreference(KEY_SOURCE).setSummary(extractor.getSource());
            findPreference(KEY_LAST_UPDATE).setSummary(extractor.getLastUpdate());
            findPreference(KEY_VERSION).setSummary(mContext.getString(
                    R.string.app_info_version_value,
                    extractor.getVersionName(),
                    extractor.getVersionCode()));
            findPreference(KEY_MORE).setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (KEY_ICON_PACK.equals(preference.getKey())) {
                // Reload in launcher.
            }
            return false;
        }

        private void onMoreClick() {
            PackageManagerHelper.startDetailsActivityForInfo(InfoBottomSheet.mViewContext, mItemInfo,
                    InfoBottomSheet.mSourceBounds, ActivityOptions.makeBasic().toBundle());
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (KEY_MORE.equals(preference.getKey())) {
                  onMoreClick();
            }
            return false;
        }
    }
}
