package com.android.launcher3.icons;

import static com.android.launcher3.icons.BaseIconFactory.CONFIG_HINT_NO_WRAP;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.calendar.DateChangeReceiver;
import com.android.launcher3.icons.calendar.DynamicCalendar;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.PluginManagerWrapper;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Icon provider which resolves icons from the selected icon pack before falling back to the
 * system provided icon.
 */
@LauncherAppSingleton
public class ThirdPartyIconProvider extends LauncherIconProviderImpl {
    private final Context mContext;
    private final DateChangeReceiver mCalendars;

    @Inject
    public ThirdPartyIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager,
            Provider<LauncherModel> modelProvider,
            IconChangeTracker iconChangeTracker,
            Provider<IconCache> iconCacheProvider,
            PluginManagerWrapper pluginManagerWrapper,
            DaggerSingletonTracker lifecycle) {
        super(context, themeManager, modelProvider, iconChangeTracker, iconCacheProvider,
                pluginManagerWrapper, lifecycle);
        mContext = context;
        mCalendars = new DateChangeReceiver(context);
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentKey key = new ComponentKey(
                info.getComponentName(), UserHandle.getUserHandleForUid(info.applicationInfo.uid));

        IconResolver resolver = IconPackManager.get(mContext).resolve(key);
        mCalendars.setIsDynamic(key, (resolver != null && resolver.isCalendar())
                || key.componentName.getPackageName().equals(DynamicCalendar.CALENDAR));

        IconResolver.DefaultDrawableProvider fallback = () -> super.getIcon(info, iconDpi);
        Drawable icon = ThirdPartyIconUtils.getByKey(mContext, key, iconDpi, fallback);

        if (icon == null) {
            return fallback.get();
        }
        icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        return icon;
    }
}
