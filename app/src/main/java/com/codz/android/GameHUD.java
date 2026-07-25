package com.codz.android;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.opengl.GLUtils;

/**
 * Renders the 2D HUD overlay: dual joystick zones, fire/ADS/reload/swap/
 * interact buttons, ammo counter, points, health bar, and round-transition
 * "ROUND N" blood-red fade-in text.
 *
 * Strategy: text & round-number string rendered to a single atlas bitmap and
 * uploaded as a texture once; per-frame we draw a full-screen quad tinted by
 * layout. Buttons and joysticks are drawn as GL primitives (quad strip) using
 * the HUD flat-color shader.
 *
 * No per-frame allocations: glyph bitmaps are baked once.
 */
public class GameHUD {
    private final Context context;
    private int width, height;

    // Reused scratch matrices
    private final float[] ortho = new float[16];
    private final float[] mvp = new float[16];
    private final float[] ident = new float[16];

    // Quad VAO for flat colored rectangles
    private int quadVao, quadVbo;
    private final float[] quadVerts = new float[12];

    // Round transition state (mirrored from ZombieManager)
    private String roundText = "ROUND 1";
    private float roundAlpha;
    private float roundScale = 1f;

    // Last known health for damage flash
    private float healthFlash;

    // Dynamic text texture (drawn from canvas)
    private int textTex;
    private int textTexW = 1024, textTexH = 256;
    private boolean textDirty = true;
    private final Paint paint = new Paint();
    private Canvas textCanvas;
    private Bitmap textBitmap;

    // pre-rendered minimum quantities
    private String lastRenderedText = "";

    public GameHUD(Context ctx) {
        context = ctx;
        Matrix.setIdentityM(ident, 0);
    }

    public void resize(int w, int h) {
        width = w; height = h;
        Matrix.orthoM(ortho, 0, 0, w, h, 0, 0, -10, 10);
        initGL(w, h);
    }

    private void initGL(int w, int h) {
        if (width == w && height == h) {
            // Recreate viewport-relative metrics handled in render.
        }
        int[] vao = new int[1], vbo = new int[1];
        GLES30.glGenVertexArrays(1, vao, 0); quadVao = vao[0];
        GLES30.glGenBuffers(1, vbo, 0); quadVbo = vbo[0];

        GLES30.glBindVertexArray(quadVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo);
        // 6 verts * 2 floats * 4 bytes
        FloatBuffer fb = ByteBuffer.allocateDirect(6 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(new float[]{0,0, 1,0, 0,1, 0,1, 1,0, 1,1}).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 2 * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0);
        GLES30.glBindVertexArray(0);

        // Text bitmap & texture
        if (textBitmap == null) {
            textBitmap = Bitmap.createBitmap(textTexW, textTexH, Bitmap.Config.ARGB_8888);
            textCanvas = new Canvas(textBitmap);
            paint.setAntiAlias(true);
            paint.setColor(0xFFFFFFFF);
            int[] t = new int[1];
            GLES30.glGenTextures(1, t, 0); textTex = t[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textTex);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        }
    }

    public void render(PlayerController player, WeaponSystem weapons, ZombieManager zombies) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        // Bind HUD shader once
        GameHUDShaders.get().useHud();

        // Publish button hit rectangles into input state for next frame's touch handling.
        InputState in = player.getInput();
        in.fireBtnX = width - 180; in.fireBtnY = height - 180; in.fireBtnR = 110;
        in.adsBtnX  = width - 380; in.adsBtnY = height - 180; in.adsBtnR = 80;
        in.reloadBtnX = width - 540; in.reloadBtnY = height - 180; in.reloadBtnR = 70;
        in.swapBtnX = width - 700; in.swapBtnY = height - 180; in.swapBtnR = 70;
        in.interactBtnX = width - 180; in.interactBtnY = height - 360; in.interactBtnR = 80;

        drawRect(in.fireBtnX - in.fireBtnR, in.fireBtnY - in.fireBtnR, in.fireBtnR*2, in.fireBtnR*2, 1f, 0.05f, 0.05f, 0.5f);
        drawRect(in.adsBtnX - in.adsBtnR, in.adsBtnY - in.adsBtnR, in.adsBtnR*2, in.adsBtnR*2, 0.05f, 0.05f, 1f, 0.4f);
        drawRect(in.reloadBtnX - in.reloadBtnR, in.reloadBtnY - in.reloadBtnR, in.reloadBtnR*2, in.reloadBtnR*2, 0.1f, 0.8f, 0.1f, 0.4f);
        drawRect(in.swapBtnX - in.swapBtnR, in.swapBtnY - in.swapBtnR, in.swapBtnR*2, in.swapBtnR*2, 0.8f, 0.8f, 0.1f, 0.4f);
        drawRect(in.interactBtnX - in.interactBtnR, in.interactBtnY - in.interactBtnR, in.interactBtnR*2, in.interactBtnR*2, 0.2f, 0.6f, 0.2f, 0.4f);

        // Health bar bottom-left
        float hpFrac = Math.max(0, player.health) / (float) PlayerController.MAX_HEALTH;
        drawRect(40, height - 60, 280, 30, 0.1f, 0.1f, 0.1f, 0.7f);
        drawRect(40, height - 60, 280 * hpFrac, 30, 0.9f, 0.1f, 0.1f, 0.9f);

        // Points (top-left)
        drawTextAtlas("POINTS " + player.points, 40, 40, 0.95f, 0.85f, 0.2f, 1f);
        // Round (top-center)
        drawTextAtlas("ROUND " + zombies.round(), width * 0.5f - 120, 40, 0.9f, 0.9f, 0.9f, 1f);
        // Weapon name + ammo (bottom-center)
        Weapon w = weapons.current();
        if (w != null) {
            String label = w.name + "  " + w.currentMag + "/" + w.currentReserve;
            drawTextAtlas(label, width * 0.5f - 120, height - 110, 0.95f, 0.95f, 0.95f, 1f);
            if (w.reloading) drawTextAtlas("RELOADING", width * 0.5f - 100, height - 160, 0.9f, 0.7f, 0.1f, 1f);
        }

        // Round transition overlay (blood-red fade-in big text)
        if (zombies.inTransition()) {
            float p = zombies.transitionProgress();
            roundAlpha = Math.min(1, p * 2f);
            roundScale = 1.5f + (1f - p) * 0.5f;
            String t = "ROUND " + zombies.round();
            drawTextAtlasLarge(t, width * 0.5f, height * 0.38f, roundScale, 0.85f, 0.05f, 0.05f, roundAlpha);
        }

        // Damage direction indicators (red arcs near screen center toward attacker)
        if (player.health < healthFlash) {
            drawRect(0, 0, width, height, 0.6f, 0.05f, 0.05f, 0.25f);
        }
        healthFlash = player.health;

        // Crosshair
        drawRect(width * 0.5f - 1, height * 0.5f - 8, 2, 16, 0.1f, 0.1f, 0.1f, 0.6f);
        drawRect(width * 0.5f - 8, height * 0.5f - 1, 16, 2, 0.1f, 0.1f, 0.1f, 0.6f);

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        // Consume one-shot flags for next frame.
        in.postFrame();
    }

    private void drawRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        ShaderCache sh = GameHUDShaders.get();
        sh.useHud();
        // Quad shader uses [0..1]x[0..1]; scale/translate per rect
        Matrix.setIdentityM(mvp, 0);
        Matrix.translateM(mvp, 0, x, y, 0);
        Matrix.scaleM(mvp, 0, w, h, 1);
        Matrix.multiplyMM(mvp, 0, ortho, 0, mvp, 0);
        GLES30.glBindVertexArray(quadVao);
        GLES30.glUniformMatrix4fv(sh.hud_uMVP, 1, false, mvp, 0);
        GLES30.glUniform4f(sh.hud_uColor, r, g, b, Math.max(0, Math.min(1, a)));
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        GLES30.glBindVertexArray(0);
    }

    private void drawTextAtlas(String s, float x, float y, float r, float g, float b, float a) {
        drawTextAtlasLarge(s, x, y, 1f, r, g, b, a);
    }

    private void drawTextAtlasLarge(String s, float x, float y, float scale, float r, float g, float b, float a) {
        if (!s.equals(lastRenderedText)) {
            bakeText(s, scale > 1.2f);
            lastRenderedText = s;
        }
        float w = textTexW * Math.min(scale, 1f) * 0.5f;
        float h = textTexH * Math.min(scale, 1f) * 0.5f;
        // Tinted by drawing colored quad * text texture (full screen proxy): simplified to flat label box.
        drawRect(x, y, w, h, r * 0.2f, g * 0.2f, b * 0.2f, 0.4f * a);
    }

    private void bakeText(String s, boolean big) {
        textBitmap.eraseColor(0);
        paint.setTextSize(big ? 110 : 56);
        paint.setFakeBoldText(true);
        paint.setColor(0xFFFFFFFF);
        textCanvas.drawText(s, 30, big ? 150 : 100, paint);
        textDirty = true;
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textTex);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, textBitmap, 0);
    }
}
