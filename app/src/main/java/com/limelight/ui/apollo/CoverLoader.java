package com.limelight.ui.apollo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.limelight.grid.assets.DiskAssetLoader;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the box art of a game from the disk cache that the library fills, for the cards
 * outside the library. Nothing is downloaded here: a game without cached art keeps its placeholder.
 */
public final class CoverLoader {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private CoverLoader() {
    }

    public interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    public static void load(ImageView view, String computerUuid, int appId, int targetWidthPx, Callback callback) {
        final String key = computerUuid + "/" + appId;
        view.setTag(key);
        final DiskAssetLoader diskLoader = new DiskAssetLoader(view.getContext());

        EXECUTOR.execute(() -> {
            File file = diskLoader.getFile(computerUuid, appId);
            if (file == null || !file.exists()) {
                return;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            options.inSampleSize = DiskAssetLoader.calculateInSampleSize(options, targetWidthPx, targetWidthPx * 4 / 3);
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return;
            }

            view.post(() -> {
                // The view may have been reused for another game meanwhile
                if (key.equals(view.getTag())) {
                    callback.onLoaded(bitmap);
                }
            });
        });
    }
}
