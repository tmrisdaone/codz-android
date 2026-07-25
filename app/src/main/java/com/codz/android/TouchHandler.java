package com.codz.android;

import android.view.MotionEvent;
import android.view.View;

public class TouchHandler implements View.OnTouchListener {
    private final InputState state;
    public TouchHandler(InputState s) { state = s; }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        v.getLocationOnScreen(new int[2]);
        state.onMotionEvent(e, v.getWidth(), v.getHeight());
        return true;
    }
}
