package com.android.launcher3.wallpaper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;

import java.util.ArrayList;
import java.util.List;

public class WallpaperDatabase implements SafeCloseable {

    private static final String DATABASE_NAME = "wallpaper_database";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_WALLPAPER = "wallpapers";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_IMAGE_PATH = "imagePath";
    private static final String COLUMN_RANK = "rank";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    private final SQLiteDatabase database;

    public static final MainThreadInitializedObject<WallpaperDatabase> INSTANCE =
            new MainThreadInitializedObject<>(WallpaperDatabase::new);

    private WallpaperDatabase(Context context) {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE " + TABLE_WALLPAPER + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_IMAGE_PATH + " TEXT NOT NULL, " +
                        COLUMN_RANK + " INTEGER NOT NULL, " +
                        COLUMN_TIMESTAMP + " INTEGER NOT NULL)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_WALLPAPER);
                onCreate(db);
            }
        };

        this.database = helper.getWritableDatabase();
    }

    // Get Wallpaper by Image Path
    public Wallpaper getWallpaperByImagePath(String imagePath) {
        String query = "SELECT * FROM " + TABLE_WALLPAPER + " WHERE " + COLUMN_IMAGE_PATH + " = ?";
        try (Cursor cursor = database.rawQuery(query, new String[]{imagePath})) {
            if (cursor.moveToFirst()) {
                return new Wallpaper(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RANK)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if no wallpaper is found
    }

    // Insert or Update Wallpaper
    public void insertOrUpdate(Wallpaper wallpaper) {
        synchronized (this) {
            Wallpaper existingWallpaper = getWallpaperByImagePath(wallpaper.getImagePath());
            if (existingWallpaper != null) {
                // Update the timestamp of the existing wallpaper
                ContentValues values = new ContentValues();
                values.put(COLUMN_TIMESTAMP, wallpaper.getTimestamp());
                String whereClause = COLUMN_ID + " = ?";
                String[] whereArgs = {String.valueOf(existingWallpaper.getId())};
                database.update(TABLE_WALLPAPER, values, whereClause, whereArgs);
            } else {
                // Insert the new wallpaper
                ContentValues values = new ContentValues();
                values.put(COLUMN_IMAGE_PATH, wallpaper.getImagePath());
                values.put(COLUMN_RANK, wallpaper.getRank());
                values.put(COLUMN_TIMESTAMP, wallpaper.getTimestamp());
                database.insert(TABLE_WALLPAPER, null, values);
            }
        }
    }

    public List<Wallpaper> getTopWallpapers() {
        List<Wallpaper> wallpapers = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_WALLPAPER +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 4";

        try (Cursor cursor = database.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    Wallpaper wallpaper = new Wallpaper(
                            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RANK)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                    );
                    wallpapers.add(wallpaper);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wallpapers;
    }

    public void updateRank(int currentRank) {
        String query = "UPDATE " + TABLE_WALLPAPER +
                " SET " + COLUMN_RANK + " = " + COLUMN_RANK + " + 1" +
                " WHERE " + COLUMN_RANK + " >= ?";
        database.execSQL(query, new Object[]{currentRank});
    }

    public void deleteWallpaper(long id) {
        String whereClause = COLUMN_ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        database.delete(TABLE_WALLPAPER, whereClause, whereArgs);
    }

    public int checkpoint() {
        try (Cursor cursor = database.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void checkpointSync() {
        checkpoint();
    }

    @Override
    public void close() {
        if (database.isOpen()) {
            database.close();
        }
    }
}
