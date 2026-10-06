package com.limelight.ui.apollo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Where every popup with a dark veil goes: the top of the window (its decor view), over the whole
 * screen. Added to the settings or to the content of the activity instead, the veil leaves bands
 * uncovered: the status bar area, the top bar and the row of hints.
 */
public final class ScreenLayer {
    private ScreenLayer() {
    }

    /** The decor view of the window of this view (it must be attached). */
    public static FrameLayout of(View any) {
        View root = any.getRootView();
        if (root instanceof FrameLayout) {
            return (FrameLayout) root;
        }
        return (FrameLayout) root.findViewById(android.R.id.content);
    }

    /**
     * While a popup is open the D-pad stays in it: the app below takes no focus. The popup itself is a
     * child of the decor view, outside the content, so it is not blocked.
     */
    public static void blockApp(View any, boolean block) {
        ViewGroup content = any.getRootView().findViewById(android.R.id.content);
        if (content != null) {
            content.setDescendantFocusability(block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    /** Takes a closed popup off the layer, if it is still there. */
    public static void remove(View popup) {
        if (popup.getParent() instanceof ViewGroup) {
            ((ViewGroup) popup.getParent()).removeView(popup);
        }
    }

    /** The area of the content of the activity inside the decor view: top, right and bottom margins from its edges. */
    public static int[] contentInsets(View any) {
        View decor = any.getRootView();
        View content = decor.findViewById(android.R.id.content);
        if (content == null) {
            return new int[]{0, 0, 0};
        }
        int[] location = new int[2];
        content.getLocationInWindow(location);
        return new int[]{location[1], decor.getWidth() - location[0] - content.getWidth(),
                decor.getHeight() - location[1] - content.getHeight()};
    }
}
