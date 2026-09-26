package com.limelight.binding.input.touch;

import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Build;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.nvstream.NvConnection;

/** Owns raw contacts on an indirect touch surface; Windows recognizes the gestures. */
public final class PrecisionTouchpadContext {
    interface FrameSender {
        int send(int sequence, int time, int width, int height, int command, int[] contacts);
    }
    private final FrameSender sender;
    private final TouchpadAreaView areaView;
    private final Runnable onFailure;
    private final SparseArray<PointF> contacts = new SparseArray<>();
    private final int[] sourceLocation = new int[2];
    private final int[] areaLocation = new int[2];
    private final long startTime = SystemClock.uptimeMillis();
    private int sequence;
    private boolean enabled;
    private boolean failed;
    private boolean sentFrame;

    public PrecisionTouchpadContext(NvConnection connection, TouchpadAreaView areaView, Runnable onFailure) {
        this(connection::sendPrecisionTouchpadFrame, areaView, onFailure);
    }

    PrecisionTouchpadContext(FrameSender sender, TouchpadAreaView areaView, Runnable onFailure) {
        this.sender = sender;
        this.areaView = areaView;
        this.onFailure = onFailure;
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) cancel();
        this.enabled = enabled;
    }

    public void cancel() {
        contacts.clear();
        if (enabled && !failed && sentFrame) {
            send(SystemClock.uptimeMillis(), 2);
        }
        sentFrame = false;
    }

    public boolean handle(View source, MotionEvent event) {
        if (!enabled || failed || areaView.isEditing()) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            cancel();
            return true;
        }
        if (source == null) return true;
        source.getLocationOnScreen(sourceLocation);
        areaView.getLocationOnScreen(areaLocation);
        float offsetX = sourceLocation[0] - areaLocation[0];
        float offsetY = sourceLocation[1] - areaLocation[1];
        RectF area = areaView.getAreaBounds();
        if (area.width() <= 0 || area.height() <= 0) return true;

        // A normal new gesture must keep the host device alive for double-taps and inertia.
        if (action == MotionEvent.ACTION_DOWN && contacts.size() != 0) cancel();
        if (action == MotionEvent.ACTION_MOVE) {
            for (int h = 0; h < event.getHistorySize(); h++) {
                updateContacts(event, h, offsetX, offsetY, area);
                if (contacts.size() != 0 && !send(event.getHistoricalEventTime(h), 1)) return true;
            }
        }
        updateContacts(event, -1, offsetX, offsetY, area);
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(index) + offsetX;
            float y = event.getY(index) + offsetY;
            if (contacts.size() < 5 && event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER && area.contains(x, y)) {
                contacts.put(id, new PointF(x - area.left, y - area.top));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                cancel();
                return true;
            }
            contacts.remove(id);
        } else if (action != MotionEvent.ACTION_MOVE) {
            return true;
        }
        if (contacts.size() != 0 || sentFrame) send(event.getEventTime(), 1);
        return true;
    }

    private void updateContacts(MotionEvent event, int history, float offsetX, float offsetY, RectF area) {
        for (int i = 0; i < contacts.size(); i++) {
            int index = event.findPointerIndex(contacts.keyAt(i));
            if (index < 0) continue;
            float x = history < 0 ? event.getX(index) : event.getHistoricalX(index, history);
            float y = history < 0 ? event.getY(index) : event.getHistoricalY(index, history);
            contacts.valueAt(i).set(Math.max(0, Math.min(area.width(), x + offsetX - area.left)),
                    Math.max(0, Math.min(area.height(), y + offsetY - area.top)));
        }
    }

    private boolean send(long time, int command) {
        if (failed) return false;
        RectF area = areaView.getAreaBounds();
        float unitsPerPixel = 2540f / (160f * areaView.getResources().getDisplayMetrics().density);
        int width = Math.max(100, Math.min(100000, Math.round(area.width() * unitsPerPixel)));
        int height = Math.max(100, Math.min(100000, Math.round(area.height() * unitsPerPixel)));
        int[] points = new int[contacts.size() * 3];
        for (int i = 0; i < contacts.size(); i++) {
            points[i * 3] = contacts.keyAt(i);
            points[i * 3 + 1] = Math.min(width, Math.round(contacts.valueAt(i).x * unitsPerPixel));
            points[i * 3 + 2] = Math.min(height, Math.round(contacts.valueAt(i).y * unitsPerPixel));
        }
        int result = sender.send(++sequence, (int)(time - startTime + 1), width, height, command, points);
        if (result != 0) {
            failed = true;
            contacts.clear();
            onFailure.run();
            return false;
        }
        sentFrame = command == 1;
        return true;
    }
}
