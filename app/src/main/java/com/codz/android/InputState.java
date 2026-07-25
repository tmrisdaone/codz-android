package com.codz.android;

import android.view.MotionEvent;

/**
 * Single shared input state. The TouchHandler writes raw pointers into here;
 * gameplay systems read from it. No allocations on hot path.
 */
public final class InputState {
    // Left virtual joystick (movement) - normalized -1..1
    public float moveX, moveY;
    public boolean moveActive;
    public int movePointerId = -1;
    public float moveOriginX, moveOriginY;

    // Right look region drag
    public float lookDX, lookDY;
    public boolean lookActive;
    public int lookPointerId = -1;
    public float lookLastX, lookLastY;

    // Buttons - indexed by identifier
    public boolean fire, firePressed;
    public boolean ads;
    public boolean reload, reloadPressed;
    public boolean swap, swapPressed;
    public boolean interact, interactPressed;

    // Screen metrics populated at touch time
    public int screenWidth, screenHeight;

    // Bitfield of button hit-test rectangles, populated by GameHUD each frame.
    public float fireBtnX, fireBtnY, fireBtnR;
    public float adsBtnX, adsBtnY, adsBtnR;
    public float reloadBtnX, reloadBtnY, reloadBtnR;
    public float swapBtnX, swapBtnY, swapBtnR;
    public float interactBtnX, interactBtnY, interactBtnR;

    private static final int LEFT_ZONE = 0;
    private static final int RIGHT_ZONE = 1;

    public void onMotionEvent(MotionEvent e, int w, int h) {
        screenWidth = w;
        screenHeight = h;
        int actionMasked = e.getActionMasked();
        int idx = e.getActionIndex();
        int pid = e.getPointerId(idx);
        float x = e.getX(idx);
        float y = e.getY(idx);

        // Buttons override zones - check all buttons for any pointer down.
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (inside(x, y, fireBtnX, fireBtnY, fireBtnR)) { fire = true; firePressed = true; return; }
                if (inside(x, y, adsBtnX, adsBtnY, adsBtnR)) { ads = true; return; }
                if (inside(x, y, reloadBtnX, reloadBtnY, reloadBtnR)) { reload = true; reloadPressed = true; return; }
                if (inside(x, y, swapBtnX, swapBtnY, swapBtnR)) { swap = true; swapPressed = true; return; }
                if (inside(x, y, interactBtnX, interactBtnY, interactBtnR)) { interact = true; interactPressed = true; return; }
                // Otherwise assign to joystick or look region
                if (x < w * 0.5f && !moveActive) {
                    moveActive = true; movePointerId = pid;
                    moveOriginX = x; moveOriginY = y; moveX = 0; moveY = 0;
                } else if (x >= w * 0.5f && !lookActive) {
                    lookActive = true; lookPointerId = pid;
                    lookLastX = x; lookLastY = y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int p = e.getPointerId(i);
                    float px = e.getX(i);
                    float py = e.getY(i);
                    if (p == movePointerId && moveActive) {
                        float dx = px - moveOriginX;
                        float dy = py - moveOriginY;
                        float max = Math.min(w, h) * 0.18f;
                        moveX = clamp(dx / max, -1f, 1f);
                        moveY = clamp(-dy / max, -1f, 1f);
                    }
                    if (p == lookPointerId && lookActive) {
                        lookDX += px - lookLastX;
                        lookDY += py - lookLastY;
                        lookLastX = px;
                        lookLastY = py;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pid == movePointerId) { moveActive = false; moveX = 0; moveY = 0; movePointerId = -1; }
                if (pid == lookPointerId) { lookActive = false; lookPointerId = -1; }
                if (inside(x, y, fireBtnX, fireBtnY, fireBtnR)) fire = false;
                if (inside(x, y, adsBtnX, adsBtnY, adsBtnR)) ads = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                moveActive = false; lookActive = false;
                fire = ads = reload = swap = interact = false;
                movePointerId = lookPointerId = -1;
                break;
        }
    }

    /** Called by gameplay at end of frame to consume one-shot presses. */
    public void postFrame() {
        firePressed = reloadPressed = swapPressed = interactPressed = false;
        lookDX = 0; lookDY = 0;
    }

    private static boolean inside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
