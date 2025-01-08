package com.android.launcher3.popup;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.OptionsPopupView;

public class Launcher3PopupDialog {

    public static class Launcher3OptionsPopUp<T extends Context & ActivityContext> extends OptionsPopupView<T> {

        public Launcher3OptionsPopUp(T context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean isShortcutOrWrapper(View view) {
            return view.getId() == R.id.wallpaper_container || super.isShortcutOrWrapper(view);
        }
    }
}
