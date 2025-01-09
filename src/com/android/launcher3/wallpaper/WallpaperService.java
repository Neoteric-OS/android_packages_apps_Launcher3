package com.android.launcher3.wallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.List;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.Utilities;

public class WallpaperService implements SafeCloseable {

    private final Context context;

    public WallpaperService(Context context) {
        this.context = context;
    }

    public void saveWallpaper(WallpaperManager wallpaperManager) {
        try {
            BitmapDrawable wallpaperDrawable = (BitmapDrawable) wallpaperManager.getDrawable();
            Bitmap currentBitmap = wallpaperDrawable.getBitmap();

            byte[] byteArray = Utilities.bitmapToByteArray(currentBitmap);

            saveWallpaper(byteArray);
        } catch (Exception e) {
            Log.e("WallpaperChange", "Error detecting wallpaper change: " + e.getMessage());
        }
    }

    private void saveWallpaper(byte[] imageData) {
        long timestamp = System.currentTimeMillis();

        List<Wallpaper> topWallpapers = WallpaperDatabase.INSTANCE.get(context).getTopWallpapers();
        String imagePath = saveImageToAppStorage(imageData);

        if (topWallpapers.size() < 4) {
            Wallpaper wallpaper = new Wallpaper(0, imagePath, topWallpapers.size(), timestamp);
            WallpaperDatabase.INSTANCE.get(context).insertOrUpdate(wallpaper);
        } else {
            Wallpaper lowestRankedWallpaper = topWallpapers.stream()
                .min(Comparator.comparingLong(Wallpaper::getTimestamp))
                .orElse(null);

            if (lowestRankedWallpaper != null) {
                WallpaperDatabase.INSTANCE.get(context).deleteWallpaper(lowestRankedWallpaper.getId());
                deleteWallpaperFile(lowestRankedWallpaper.getImagePath());

                int lowestRank = lowestRankedWallpaper.getRank();
                WallpaperDatabase.INSTANCE.get(context).updateRank(lowestRank);
            }

            Wallpaper newWallpaper = new Wallpaper(0, imagePath, 0, timestamp);
            WallpaperDatabase.INSTANCE.get(context).insertOrUpdate(newWallpaper);
        }
    }

    public List<Wallpaper> getTopWallpapers() {
        List<Wallpaper> wallpapers = WallpaperDatabase.INSTANCE.get(context).getTopWallpapers();
        return wallpapers.isEmpty() ? List.of() : wallpapers;
    }

    private void deleteWallpaperFile(String imagePath) {
        File file = new File(imagePath);
        if (file.exists()) {
            file.delete();
        }
    }

    private String saveImageToAppStorage(byte[] imageData) {
        File storageDir = new File(context.getFilesDir(), "wallpapers");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String imageHash = String.valueOf(imageData.hashCode());
        File imageFile = new File(storageDir, "wallpaper_" + imageHash + ".jpg");

        if (!imageFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageData);
            } catch (Exception e) {
                Log.e("WallpaperService", "Error saving image: " + e.getMessage());
            }
        }

        return imageFile.getAbsolutePath();
    }

    @Override
    public void close() {}

    public static final MainThreadInitializedObject<WallpaperService> INSTANCE =
        new MainThreadInitializedObject<>(WallpaperService::new);
}
