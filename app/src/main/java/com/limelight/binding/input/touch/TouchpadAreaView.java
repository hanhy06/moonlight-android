package com.limelight.binding.input.touch;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.os.Build;

/** A transparent overlay that displays and edits the touchpad input region. */
public class TouchpadAreaView extends View {
    private static final float MIN_WIDTH_FRACTION = 0.18f;
    private static final float MIN_HEIGHT_FRACTION = 0.12f;
    private static final float HANDLE_RADIUS_DP = 14f;
    private static final float EDGE_HIT_DP = 28f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF normalizedBounds = new RectF();
    private final RectF startArea = new RectF();
    private final RectF area = new RectF();
    private final float density;
    private boolean editing;
    private boolean borderVisible;
    private float backgroundOpacity;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private int gestureMode;
    private float downX;
    private float downY;
    private Runnable areaChangeListener;
    private int insetLeft, insetTop, insetRight, insetBottom;

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_MOVE = 1;
    private static final int GESTURE_RESIZE_TOP_LEFT = 2;
    private static final int GESTURE_RESIZE_TOP_RIGHT = 3;
    private static final int GESTURE_RESIZE_BOTTOM_LEFT = 4;
    private static final int GESTURE_RESIZE_BOTTOM_RIGHT = 5;

    public TouchpadAreaView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * density);
        borderPaint.setColor(0xFFEAF4FF);
        handlePaint.setColor(0xFF58A6FF);
        handlePaint.setStyle(Paint.Style.FILL);
        borderVisible = TouchpadAreaSettings.isBorderVisible(context);
        backgroundOpacity = TouchpadAreaSettings.getBackgroundOpacity(context);
        normalizedBounds.set(TouchpadAreaSettings.loadBounds(context, isLandscape()));
    }

    public void setEditing(boolean editing) {
        if (this.editing != editing) {
            this.editing = editing;
            activePointerId = MotionEvent.INVALID_POINTER_ID;
            gestureMode = GESTURE_NONE;
            invalidate();
        }
    }

    public boolean isEditing() {
        return editing;
    }

    /** Save the current edit for this orientation. */
    public void applyEdit() {
        if (getWidth() > 0 && getHeight() > 0) {
            TouchpadAreaSettings.saveBounds(getContext(), isLandscape(), normalizedBounds);
        }
        setEditing(false);
    }

    /** Restore the last saved bounds for this orientation. */
    public void cancelEdit() {
        normalizedBounds.set(TouchpadAreaSettings.loadBounds(getContext(), isLandscape()));
        setEditing(false);
        invalidate();
        notifyAreaChanged();
    }

    public void resetToDefault() {
        normalizedBounds.set(TouchpadAreaSettings.defaultBounds());
        invalidate();
        notifyAreaChanged();
    }

    public void resetToFullscreen() {
        normalizedBounds.set(0, 0, 1, 1);
        invalidate();
        notifyAreaChanged();
    }

    /** Returns a copy in the view's current pixel coordinate space. */
    public RectF getAreaBounds() {
        updatePixelBounds();
        return new RectF(area);
    }

    public void setAreaChangeListener(Runnable listener) {
        areaChangeListener = listener;
    }

    public void setAppearance(boolean borderVisible, float opacity) {
        this.borderVisible = borderVisible;
        backgroundOpacity = Math.max(0, Math.min(1, opacity));
        TouchpadAreaSettings.saveAppearance(getContext(), borderVisible, backgroundOpacity);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 && oldh == 0 || isLandscapeFor(w, h) != isLandscapeFor(oldw, oldh)) {
            normalizedBounds.set(TouchpadAreaSettings.loadBounds(getContext(), isLandscapeFor(w, h)));
        }
        clampBounds();
        notifyAreaChanged();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insetLeft = insets.getSystemWindowInsetLeft();
        insetTop = insets.getSystemWindowInsetTop();
        insetRight = insets.getSystemWindowInsetRight();
        insetBottom = insets.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            insetLeft = Math.max(insetLeft, insets.getDisplayCutout().getSafeInsetLeft());
            insetTop = Math.max(insetTop, insets.getDisplayCutout().getSafeInsetTop());
            insetRight = Math.max(insetRight, insets.getDisplayCutout().getSafeInsetRight());
            insetBottom = Math.max(insetBottom, insets.getDisplayCutout().getSafeInsetBottom());
        }
        notifyAreaChanged();
        invalidate();
        return insets;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updatePixelBounds();
        fillPaint.setColor(((int) (backgroundOpacity * 255) << 24) | 0x003B82C4);
        canvas.drawRoundRect(area, 10 * density, 10 * density, fillPaint);
        if (borderVisible || editing) {
            canvas.drawRoundRect(area, 10 * density, 10 * density, borderPaint);
        }
        if (editing) {
            float radius = HANDLE_RADIUS_DP * density;
            canvas.drawCircle(area.left, area.top, radius, handlePaint);
            canvas.drawCircle(area.right, area.top, radius, handlePaint);
            canvas.drawCircle(area.left, area.bottom, radius, handlePaint);
            canvas.drawCircle(area.right, area.bottom, radius, handlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editing) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                updatePixelBounds();
                activePointerId = event.getPointerId(0);
                downX = event.getX();
                downY = event.getY();
                startArea.set(area);
                float edge = EDGE_HIT_DP * density;
                if (nearCorner(downX, downY, area.left, area.top, edge)) {
                    gestureMode = GESTURE_RESIZE_TOP_LEFT;
                } else if (nearCorner(downX, downY, area.right, area.top, edge)) {
                    gestureMode = GESTURE_RESIZE_TOP_RIGHT;
                } else if (nearCorner(downX, downY, area.left, area.bottom, edge)) {
                    gestureMode = GESTURE_RESIZE_BOTTOM_LEFT;
                } else if (nearCorner(downX, downY, area.right, area.bottom, edge)) {
                    gestureMode = GESTURE_RESIZE_BOTTOM_RIGHT;
                } else {
                    gestureMode = area.contains(downX, downY) ? GESTURE_MOVE : GESTURE_NONE;
                }
                return gestureMode != GESTURE_NONE;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(activePointerId);
                if (index < 0 || gestureMode == GESTURE_NONE) {
                    return true;
                }
                float dx = event.getX(index) - downX;
                float dy = event.getY(index) - downY;
                float minWidth = Math.min(getWidth(), MIN_WIDTH_FRACTION * getWidth());
                float minHeight = Math.min(getHeight(), MIN_HEIGHT_FRACTION * getHeight());
                if (gestureMode == GESTURE_MOVE) {
                    float left = clamp(startArea.left + dx, insetLeft, getWidth() - insetRight - startArea.width());
                    float top = clamp(startArea.top + dy, insetTop, getHeight() - insetBottom - startArea.height());
                    area.set(left, top, left + startArea.width(), top + startArea.height());
                } else {
                    boolean resizeLeft = gestureMode == GESTURE_RESIZE_TOP_LEFT || gestureMode == GESTURE_RESIZE_BOTTOM_LEFT;
                    boolean resizeTop = gestureMode == GESTURE_RESIZE_TOP_LEFT || gestureMode == GESTURE_RESIZE_TOP_RIGHT;
                    float left = resizeLeft
                            ? clamp(startArea.left + dx, insetLeft, startArea.right - minWidth) : startArea.left;
                    float right = resizeLeft ? startArea.right
                            : clamp(startArea.right + dx, startArea.left + minWidth, getWidth() - insetRight);
                    float top = resizeTop
                            ? clamp(startArea.top + dy, insetTop, startArea.bottom - minHeight) : startArea.top;
                    float bottom = resizeTop ? startArea.bottom
                            : clamp(startArea.bottom + dy, startArea.top + minHeight, getHeight() - insetBottom);
                    area.set(left, top, right, bottom);
                }
                updateNormalizedBounds();
                invalidate();
                notifyAreaChanged();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                gestureMode = GESTURE_NONE;
                return true;
            default:
                return true;
        }
    }

    private void updatePixelBounds() {
        int width = Math.max(0, getWidth() - insetLeft - insetRight);
        int height = Math.max(0, getHeight() - insetTop - insetBottom);
        area.set(insetLeft + normalizedBounds.left * width, insetTop + normalizedBounds.top * height,
                insetLeft + normalizedBounds.right * width, insetTop + normalizedBounds.bottom * height);
    }

    private void updateNormalizedBounds() {
        int width = getWidth() - insetLeft - insetRight;
        int height = getHeight() - insetTop - insetBottom;
        if (width > 0 && height > 0) {
            normalizedBounds.set((area.left - insetLeft) / width, (area.top - insetTop) / height,
                    (area.right - insetLeft) / width, (area.bottom - insetTop) / height);
            clampBounds();
        }
    }

    private void clampBounds() {
        normalizedBounds.left = clamp(normalizedBounds.left, 0, 1);
        normalizedBounds.top = clamp(normalizedBounds.top, 0, 1);
        normalizedBounds.right = clamp(normalizedBounds.right, normalizedBounds.left, 1);
        normalizedBounds.bottom = clamp(normalizedBounds.bottom, normalizedBounds.top, 1);
    }

    private boolean isLandscape() {
        if (getWidth() > 0 && getHeight() > 0) {
            return isLandscapeFor(getWidth(), getHeight());
        }
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static boolean isLandscapeFor(int width, int height) {
        return width > height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean nearCorner(float x, float y, float cornerX, float cornerY, float radius) {
        return Math.abs(x - cornerX) <= radius && Math.abs(y - cornerY) <= radius;
    }

    private void notifyAreaChanged() {
        if (areaChangeListener != null) {
            areaChangeListener.run();
        }
    }
}
