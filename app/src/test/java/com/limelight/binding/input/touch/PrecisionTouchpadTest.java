package com.limelight.binding.input.touch;

import android.content.Context;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PrecisionTouchpadTest {
    private TouchpadAreaView area;
    private PrecisionTouchpadContext input;
    private final ArrayList<int[]> frames = new ArrayList<>();
    private final ArrayList<Integer> commands = new ArrayList<>();

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("TouchpadArea", Context.MODE_PRIVATE).edit().clear().commit();
        area = new TouchpadAreaView(context);
        area.layout(0, 0, 1000, 1000);
        input = new PrecisionTouchpadContext((s, t, w, h, command, points) -> {
            commands.add(command);
            frames.add(points);
            return 0;
        }, area, () -> fail("Unexpected transmission failure"));
        input.setEnabled(true);
    }

    private void touch(int action, int[] ids, float... xy) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = ids[i];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xy[i * 2];
            coords[i].y = xy[i * 2 + 1];
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, ids.length, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        input.handle(area, event);
        event.recycle();
    }

    @Test public void consecutiveTapsKeepTheHostDeviceAlive() {
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, 400, 800);
        touch(MotionEvent.ACTION_UP, new int[]{0}, 400, 800);
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, 400, 800);
        touch(MotionEvent.ACTION_UP, new int[]{0}, 400, 800);
        assertEquals(4, commands.size());
        for (int command : commands) assertEquals(1, command);
        assertEquals(0, frames.get(3).length);
    }

    @Test public void outsideStartNeverBecomesContact() {
        touch(MotionEvent.ACTION_DOWN, new int[]{7}, 50, 50);
        touch(MotionEvent.ACTION_MOVE, new int[]{7}, 500, 800);
        touch(MotionEvent.ACTION_UP, new int[]{7}, 500, 800);
        assertTrue(frames.isEmpty());
    }

    @Test public void preservesIdsWhenPointerIndicesChangeAndReleasesLastFinger() {
        touch(MotionEvent.ACTION_DOWN, new int[]{7}, 400, 800);
        touch(MotionEvent.ACTION_POINTER_DOWN | (1 << 8), new int[]{7, 2}, 400, 800, 500, 800);
        assertArrayEquals(new int[]{2, 7}, new int[]{frames.get(1)[0], frames.get(1)[3]});
        touch(MotionEvent.ACTION_POINTER_UP, new int[]{7, 2}, 400, 800, 500, 800);
        assertEquals(2, frames.get(2)[0]);
        assertEquals(3, frames.get(2).length);
        touch(MotionEvent.ACTION_MOVE, new int[]{2}, 600, 800);
        assertEquals(2, frames.get(3)[0]);
        touch(MotionEvent.ACTION_UP, new int[]{2}, 600, 800);
        assertEquals(0, frames.get(4).length);
    }

    @Test public void leavingBoundsClampsPositionWithoutReleasingContact() {
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, 400, 800);
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, 0, 0);
        assertArrayEquals(new int[]{0, 0, 0}, frames.get(1));
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, 500, 800);
        assertTrue(frames.get(2)[1] > 0);
        input.cancel();
        assertEquals(Integer.valueOf(2), commands.get(3));
        assertEquals(0, frames.get(3).length);
    }

    @Test public void editApplyCancelAndOrientationSettingsRemainIndependent() {
        RectF original = area.getAreaBounds();
        area.setEditing(true);
        area.resetToFullscreen();
        area.cancelEdit();
        assertEquals(original, area.getAreaBounds());
        area.setEditing(true);
        area.resetToFullscreen();
        area.applyEdit();
        TouchpadAreaView restored = new TouchpadAreaView(area.getContext());
        restored.layout(0, 0, 1000, 1000);
        assertEquals(new RectF(0, 0, 1000, 1000), restored.getAreaBounds());
        restored.layout(0, 0, 1000, 500);
        assertTrue(restored.getAreaBounds().left > 0);
    }

    @Test public void failedSendStopsSubsequentInputAndReportsOnce() {
        int[] failures = {0};
        input = new PrecisionTouchpadContext((s, t, w, h, c, p) -> -1, area, () -> failures[0]++);
        input.setEnabled(true);
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, 400, 800);
        touch(MotionEvent.ACTION_MOVE, new int[]{0}, 500, 800);
        input.cancel();
        assertEquals(1, failures[0]);
    }
}
