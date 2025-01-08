package com.android.launcher3.views;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public class IconFrame extends FrameLayout {

    private final ImageView imageView;

    public IconFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setLayoutParams(new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ));

        imageView = new ImageView(context);
        imageView.setLayoutParams(new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ));
        int padding = dpToPx(context, 12);
        imageView.setPadding(padding, padding, padding, padding);
        addView(imageView);

        setBackgroundWithRadius(
            ContextCompat.getColor(context, R.color.accent_primary_device_default),
            Themes.getDialogCornerRadius(context)
        );
    }

    public IconFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconFrame(Context context) {
        this(context, null, 0);
    }

    /**
     * Convert dp to pixels for consistent padding across devices.
     */
    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Set the vector drawable for the ImageView.
     *
     * @param drawableRes The resource ID of the vector drawable.
     */
    public void setIcon(@DrawableRes int drawableRes) {
        imageView.setImageResource(drawableRes);
    }

    /**
     * Set the background color and corner radius of the FrameLayout.
     *
     * @param bgColor The background color.
     * @param cornerRadius The corner radius in pixels.
     */
    public void setBackgroundWithRadius(int bgColor, float cornerRadius) {
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
        backgroundDrawable.setColor(bgColor);
        backgroundDrawable.setCornerRadius(cornerRadius);
        setBackground(backgroundDrawable);
    }
}
