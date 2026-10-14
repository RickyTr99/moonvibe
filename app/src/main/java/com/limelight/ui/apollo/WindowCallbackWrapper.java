package com.limelight.ui.apollo;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/** Passes everything to the window's own callback; extended to look at the events on their way. */
class WindowCallbackWrapper implements Window.Callback {
    private final Window.Callback wrapped;

    WindowCallbackWrapper(Window.Callback wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return wrapped.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return wrapped.dispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return wrapped.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return wrapped.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return wrapped.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return wrapped.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public View onCreatePanelView(int featureId) {
        return wrapped.onCreatePanelView(featureId);
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return wrapped.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return wrapped.onPreparePanel(featureId, view, menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        return wrapped.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return wrapped.onMenuItemSelected(featureId, item);
    }

    @Override
    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        wrapped.onWindowAttributesChanged(attrs);
    }

    @Override
    public void onContentChanged() {
        wrapped.onContentChanged();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        wrapped.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onAttachedToWindow() {
        wrapped.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        wrapped.onDetachedFromWindow();
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        wrapped.onPanelClosed(featureId, menu);
    }

    @Override
    public boolean onSearchRequested() {
        return wrapped.onSearchRequested();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public boolean onSearchRequested(SearchEvent searchEvent) {
        return wrapped.onSearchRequested(searchEvent);
    }

    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return wrapped.onWindowStartingActionMode(callback);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return wrapped.onWindowStartingActionMode(callback, type);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        wrapped.onActionModeStarted(mode);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        wrapped.onActionModeFinished(mode);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        wrapped.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        wrapped.onPointerCaptureChanged(hasCapture);
    }
}
