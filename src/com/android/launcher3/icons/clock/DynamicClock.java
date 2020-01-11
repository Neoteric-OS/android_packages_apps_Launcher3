package com.android.launcher3.icons.clock;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;


import com.android.launcher3.util.Preconditions;


@TargetApi(26)
public class DynamicClock {
    public static final ComponentName DESK_CLOCK = new ComponentName(
            "com.google.android.deskclock",
            "com.android.deskclock.DeskClock");

    private DynamicClock() { }

    public static Drawable getClock(Context context, int iconDpi) {
        ClockLayers clone = getClockLayers(context, iconDpi).clone();
        if (clone != null) {
            clone.updateAngles();
            return clone.mDrawable;
        }
        return null;
    }

    private static ClockLayers getClockLayers(Context context, int iconDpi) {
        Preconditions.assertWorkerThread();
        ClockLayers layers = new ClockLayers();
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo("com.google.android.deskclock", PackageManager.GET_META_DATA | PackageManager.GET_UNINSTALLED_PACKAGES);
            Bundle metaData = applicationInfo.metaData;
            if (metaData != null) {
                int levelPerTickIcon = metaData.getInt("com.google.android.apps.nexuslauncher.LEVEL_PER_TICK_ICON_ROUND", 0);
                if (levelPerTickIcon != 0) {
                    Drawable drawableForDensity = packageManager.getResourcesForApplication(applicationInfo).getDrawableForDensity(levelPerTickIcon, iconDpi);
                    layers.setDrawable(drawableForDensity.mutate());
                    layers.mHourIndex = metaData.getInt("com.google.android.apps.nexuslauncher.HOUR_LAYER_INDEX", -1);
                    layers.mMinuteIndex = metaData.getInt("com.google.android.apps.nexuslauncher.MINUTE_LAYER_INDEX", -1);
                    layers.mSecondIndex = metaData.getInt("com.google.android.apps.nexuslauncher.SECOND_LAYER_INDEX", -1);
                    layers.mDefaultHour = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_HOUR", 0);
                    layers.mDefaultMinute = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_MINUTE", 0);
                    layers.mDefaultSecond = metaData.getInt("com.google.android.apps.nexuslauncher.DEFAULT_SECOND", 0);
                    LayerDrawable layerDrawable = layers.mLayerDrawable;
                    int numberOfLayers = layerDrawable.getNumberOfLayers();

                    if (layers.mHourIndex < 0 || layers.mHourIndex >= numberOfLayers) {
                        layers.mHourIndex = -1;
                    }
                    if (layers.mMinuteIndex < 0 || layers.mMinuteIndex >= numberOfLayers) {
                        layers.mMinuteIndex = -1;
                    }
                    if (layers.mSecondIndex < 0 || layers.mSecondIndex >= numberOfLayers) {
                        layers.mSecondIndex = -1;
                    } else {
                        layerDrawable.setDrawable(layers.mSecondIndex, null);
                        layers.mSecondIndex = -1;
                    }
                }
            }
        } catch (Exception e) {
            layers.mDrawable = null;
        }
        return layers;
    }
}
