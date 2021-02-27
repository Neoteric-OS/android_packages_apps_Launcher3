/*
 * Copyright (C) 2026 Neoteric OS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.widget.SliderPreference;

/**
 * A percentage {@link SliderPreference}, styled like the sliders on
 * Settings > Display > Display size and text: discrete, with a tick per step.
 *
 * Upstream exposes neither the ticks nor the label format as XML attributes, so both are set
 * here. Everything else - bounds, step size, the value bubble and the decrement/increment
 * buttons - comes from the usual attributes in the preference XML.
 */
public class PercentSliderPreference extends SliderPreference {

    public PercentSliderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setTickVisible(true);
        setLabelFormater(value -> ((int) value) + "%");
    }
}
