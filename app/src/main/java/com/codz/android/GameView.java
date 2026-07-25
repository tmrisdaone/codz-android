package com.codz.android;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends GLSurfaceView {
    private final GameRenderer renderer;
    private final InputState input;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 24, 0);
        input = new InputState();
        renderer = new GameRenderer(context, input);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setOnTouchListener(new TouchHandler(input));
    }

    @Override
    public void onPause() {
        super.onPause();
        renderer.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        renderer.onResume();
    }
}
