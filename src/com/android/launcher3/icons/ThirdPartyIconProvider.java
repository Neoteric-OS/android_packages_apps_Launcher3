package com.android.launcher3.icons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DaggerSingletonObject;

import com.android.launcher3.icons.pack.IconResolver;

import javax.inject.Inject;

import static com.android.launcher3.icons.BaseIconFactory.CONFIG_HINT_NO_WRAP;

@SuppressWarnings("unused")
@LauncherAppSingleton
public class ThirdPartyIconProvider extends LauncherIconProvider {
    private final Context mContext;

    public static final DaggerSingletonObject<ThirdPartyIconProvider> INSTANCE =
            new DaggerSingletonObject<>(LauncherAppComponent::getThirdPartyIconProvider);

    @Inject
    public ThirdPartyIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager,
            ApiWrapper apiWrapper) {
        super(context, themeManager, apiWrapper);
        mContext = context;
    }

    @SuppressLint("WrongConstant")
    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentKey key = new ComponentKey(
                info.getComponentName(), UserHandle.getUserHandleForUid(info.applicationInfo.uid));

        IconResolver.DefaultDrawableProvider fallback =
                () -> super.getIcon(info, iconDpi);
        Drawable icon = ThirdPartyIconUtils.getByKey(mContext, key, iconDpi, fallback);

        if (icon == null) {
            return fallback.get();
        }
        icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        return icon;
    }
}
