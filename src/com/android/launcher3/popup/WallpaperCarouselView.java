package com.android.launcher3.popup;

import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.views.IconFrame;
import com.android.launcher3.R;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.wallpaper.Wallpaper;
import com.android.launcher3.wallpaper.WallpaperDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

public class WallpaperCarouselView extends LinearLayout {
    private final DeviceProfile deviceProfile;
    private final ProgressBar loadingView;
    private int currentItemIndex = 0;
    private final IconFrame iconFrame;
    private Wallpaper currentWallpaper;

    public WallpaperCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        deviceProfile = ActivityContext.lookupContext(context).getDeviceProfile();
        iconFrame = new IconFrame(context);
        iconFrame.setIcon(R.drawable.ic_tick);
        loadingView = new ProgressBar(context);
        loadingView.setIndeterminate(true);
        loadingView.setVisibility(VISIBLE);
        addView(loadingView);
        fetchWallpapers();
    }

    private void fetchWallpapers() {
        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                List<Wallpaper> wallpapers = WallpaperDatabase.INSTANCE.get(getContext()).getTopWallpapers();

                // Deduplicate wallpapers by imagePath
                Set<String> seenImagePaths = new HashSet<>();
                List<Wallpaper> uniqueWallpapers = new ArrayList<>();
                for (Wallpaper wallpaper : wallpapers) {
                    if (!seenImagePaths.contains(wallpaper.getImagePath())) {
                        seenImagePaths.add(wallpaper.getImagePath());
                        uniqueWallpapers.add(wallpaper);
                    }
                }

                MAIN_EXECUTOR.execute(() -> {
                    loadingView.setVisibility(GONE);
                    setVisibility(uniqueWallpapers.isEmpty() ? GONE : VISIBLE);
                    if (!uniqueWallpapers.isEmpty()) {
                        displayWallpapers(uniqueWallpapers);
                    }
                });
            } catch (Exception e) {
                Log.e("WallpaperCarouselView", "Error fetching wallpapers: " + e.getMessage());
                MAIN_EXECUTOR.execute(() -> {
                    loadingView.setVisibility(GONE);
                    setVisibility(GONE);
                });
            }
        });
    }

    private void displayWallpapers(List<Wallpaper> wallpapers) {
        // Remove the ProgressBar if it exists
        if (getChildAt(0) instanceof ProgressBar) {
            removeViewAt(0);
        }

        // Clear existing views only if there's a change in the wallpaper list
        if (getChildCount() != wallpapers.size() || isWallpaperListChanged(wallpapers)) {
            removeAllViews();
            int totalWidth = getWidth() > 0 ? getWidth() : (int) (deviceProfile.widthPx * 0.8);
            double firstItemWidth = totalWidth * 0.5;
            double remainingWidth = totalWidth - firstItemWidth;
            double marginBetweenItems = totalWidth * 0.02;
            double itemWidth = (remainingWidth - (marginBetweenItems * (wallpapers.size() - 1))) / (wallpapers.size() - 1);

            for (int index = 0; index < wallpapers.size(); index++) {
                Wallpaper wallpaper = wallpapers.get(index);
                if (isWallpaperInvalid(wallpaper)) continue;
                CardView cardView = createWallpaperCard(wallpaper, index, firstItemWidth, itemWidth, marginBetweenItems);
                addView(cardView);
                loadWallpaperBitmapAsync(wallpaper, cardView);
            }
        }
    }

    private boolean isWallpaperListChanged(List<Wallpaper> wallpapers) {
        int cardViewIndex = 0; // Track index in the wallpaper list
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            // Skip non-CardView children, like ProgressBar
            if (!(child instanceof CardView)) {
                continue;
            }

            CardView existingCard = (CardView) child;
            Wallpaper existingWallpaper = (Wallpaper) existingCard.getTag();

            // Handle index mismatch or null cases
            if (cardViewIndex >= wallpapers.size() || existingWallpaper == null) {
                return true;
            }

            Wallpaper newWallpaper = wallpapers.get(cardViewIndex);
            if (!existingWallpaper.equals(newWallpaper)) {
                return true;
            }

            cardViewIndex++;
        }

        // Check if all wallpapers are accounted for
        return cardViewIndex != wallpapers.size();
    }

    private boolean isWallpaperInvalid(Wallpaper wallpaper) {
        return wallpaper == null || wallpaper.getImagePath() == null || wallpaper.getImagePath().isEmpty();
    }

    private CardView createWallpaperCard(Wallpaper wallpaper, int index, double firstItemWidth, double itemWidth, double marginBetweenItems) {
        CardView cardView = new CardView(getContext());
        cardView.setRadius(Themes.getDialogCornerRadius(getContext()) / 2);
        cardView.setCardElevation(0); // Removed shadow by setting elevation to 0

        LayoutParams layoutParams = new LayoutParams(
                index == currentItemIndex ? (int) firstItemWidth : (int) itemWidth,
                LayoutParams.MATCH_PARENT
        );
        layoutParams.setMargins(index > 0 ? (int) marginBetweenItems : 0, 0, 0, 0);
        cardView.setLayoutParams(layoutParams);

        // Assign the wallpaper as the tag for comparison later
        cardView.setTag(wallpaper);

        cardView.setOnClickListener(v -> {
            if (index != currentItemIndex) {
                animateWidthTransition(index, firstItemWidth, itemWidth);
            }
            setWallpaper(wallpaper);
        });

        ImageView placeholder = new ImageView(getContext());
        placeholder.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_deepshortcut_placeholder));
        placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cardView.addView(placeholder);

        return cardView;
    }

    private void loadWallpaperBitmapAsync(Wallpaper wallpaper, CardView cardView) {
        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                File imageFile = new File(wallpaper.getImagePath());
                if (imageFile.exists() && imageFile.canRead()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2; // Scale down bitmap to reduce memory usage
                    Bitmap bitmap = BitmapFactory.decodeFile(wallpaper.getImagePath(), options);
                    if (bitmap != null) {
                        post(() -> {
                            ImageView imageView = (ImageView) cardView.getChildAt(0);
                            if (imageView != null) imageView.setImageBitmap(bitmap);
                            if (cardView == getChildAt(currentItemIndex)) addIconFrameToCard(cardView);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("WallpaperCarouselView", "Error loading wallpaper bitmap: " + e.getMessage());
            }
        });
    }

    private void setWallpaper(Wallpaper wallpaper) {
        if (wallpaper.equals(currentWallpaper)) return;

        ProgressBar loadingSpinner = new ProgressBar(getContext());
        loadingSpinner.setIndeterminate(true);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER;
        loadingSpinner.setLayoutParams(spinnerParams);

        CardView currentCardView = (CardView) getChildAt(currentItemIndex);
        currentCardView.removeView(iconFrame);
        currentCardView.addView(loadingSpinner);

        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
                Bitmap bitmap = BitmapFactory.decodeFile(wallpaper.getImagePath());
                if (bitmap != null) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);

                    // Update the database with the new wallpaper
                    wallpaper.setTimestamp(System.currentTimeMillis());
                    WallpaperDatabase.INSTANCE.get(getContext()).insertOrUpdate(wallpaper);

                    currentWallpaper = wallpaper;

                    // Refresh the carousel with the updated database state
                    fetchWallpapers();

                    MAIN_EXECUTOR.execute(() -> {
                        currentCardView.removeView(loadingSpinner);
                        addIconFrameToCard(currentCardView);
                    });
                }
            } catch (Exception e) {
                Log.e("WallpaperCarouselView", "Error setting wallpaper: " + e.getMessage());
                MAIN_EXECUTOR.execute(() -> {
                    currentCardView.removeView(loadingSpinner);
                    addIconFrameToCard(currentCardView);
                });
            }
        });
    }

    private void addIconFrameToCard(CardView cardView) {
        if (iconFrame.getParent() != null) {
            ((ViewGroup) iconFrame.getParent()).removeView(iconFrame);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        iconFrame.setBackgroundWithRadius(Themes.getColorAccent(getContext()), 100F);
        cardView.addView(iconFrame, params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int adjustedWidth = (int) (deviceProfile.widthPx * 0.8);
        int width = MeasureSpec.makeMeasureSpec(adjustedWidth, MeasureSpec.EXACTLY);
        super.onMeasure(width, heightMeasureSpec);
    }

    private void animateWidthTransition(int newIndex, double firstItemWidth, double itemWidth) {
        currentItemIndex = newIndex;
        for (int i = 0; i < getChildCount(); i++) {
            CardView cardView = (CardView) getChildAt(i);
            int targetWidth = (i == currentItemIndex) ? (int) firstItemWidth : (int) itemWidth;
            LayoutParams layoutParams = (LayoutParams) cardView.getLayoutParams();
            if (layoutParams.width != targetWidth) {
                ValueAnimator animator = ValueAnimator.ofInt(layoutParams.width, targetWidth);
                animator.setDuration(300L);
                animator.addUpdateListener(animation -> {
                    layoutParams.width = (int) animation.getAnimatedValue();
                    cardView.setLayoutParams(layoutParams);
                });
                animator.start();
            }
            if (i == currentItemIndex) addIconFrameToCard(cardView);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        UI_HELPER_EXECUTOR.execute(() -> {
            // Perform background cleanup
            currentWallpaper = null;

            // Schedule UI updates on the main thread using MAIN_EXECUTOR
            MAIN_EXECUTOR.execute(() -> {
                iconFrame.setImageBitmap(null);
                removeAllViews();
            });
        });
    }
}
