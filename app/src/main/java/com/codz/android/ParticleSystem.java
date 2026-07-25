package com.codz.android;

import android.opengl.GLES30;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Pooled GPU-updated particle system. Fixed capacity, ring buffer of
 * particle records. Each render: rebuild a single interleaved VBO with the
 * active particles and draw as GL_POINTS. Allocations only at construction.
 *
 * Particle layout per-particle (6 floats): x,y,z, r,g,b
 * Velocity & life tracked in parallel CPU arrays.
 */
public class ParticleSystem {
    private final int capacity;
    private final float[] px, py, pz, vx, vy, vz, life, r, g, b;
    private final float[] renderData;
    private final FloatBuffer renderBuf;

    private int vao, vbo;
    private ShaderCache sh;
    private final float[] mvp = new float[16];
    private final float[] ident = new float[16];

    public ParticleSystem(int cap) {
        capacity = cap;
        px = new float[cap]; py = new float[cap]; pz = new float[cap];
        vx = new float[cap]; vy = new float[cap]; vz = new float[cap];
        life = new float[cap];
        r = new float[cap]; g = new float[cap]; b = new float[cap];
        renderData = new float[cap * 6];
        renderBuf = ByteBuffer.allocateDirect(cap * 6 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        Matrix.setIdentityM(ident, 0);
    }

    public void initGL(ShaderCache sh) {
        this.sh = sh;
        int[] va = new int[1], b = new int[1];
        GLES30.glGenVertexArrays(1, va, 0); vao = va[0];
        GLES30.glGenBuffers(1, b, 0); vbo = b[0];

        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity * 6 * 4, null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 6 * 4, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 6 * 4, 3 * 4);
        GLES30.glBindVertexArray(0);
    }

    public void spawn(float x, float y, float z, float cr, float cg, float cb, int count) {
        for (int i = 0; i < count; i++) {
            int slot = findFree();
            if (slot < 0) return;
            px[slot] = x; py[slot] = y; pz[slot] = z;
            float dir = (float)(Math.random() * 6.28f);
            float pitch = (float)(Math.random() * 1.5f - 0.4f);
            float speed = 1.5f + (float)Math.random() * 1.5f;
            vx[slot] = (float)Math.cos(dir) * speed;
            vy[slot] = (float)Math.sin(pitch) * speed;
            vz[slot] = (float)Math.sin(dir) * speed;
            life[slot] = 0.6f + (float)Math.random() * 0.4f;
            r[slot] = cr; g[slot] = cg; b[slot] = cb;
        }
    }

    private int findFree() {
        for (int i = 0; i < capacity; i++) if (life[i] <= 0) return i;
        return -1;
    }

    public void update(float dt) {
        for (int i = 0; i < capacity; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            vy[i] -= 4f * dt; // gravity
        }
    }

    public void render(float[] vp, ShaderCache sh) {
        if (this.sh == null) initGL(sh);
        int n = 0;
        for (int i = 0; i < capacity; i++) {
            if (life[i] <= 0) continue;
            renderData[n*6+0] = px[i]; renderData[n*6+1] = py[i]; renderData[n*6+2] = pz[i];
            renderData[n*6+3] = r[i]; renderData[n*6+4] = g[i]; renderData[n*6+5] = b[i];
            n++;
        }
        if (n == 0) return;
        renderBuf.clear();
        renderBuf.put(renderData, 0, n * 6);
        renderBuf.position(0);

        sh.useFlat();
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * 6 * 4, renderBuf);
        GLES30.glUniformMatrix4fv(sh.fc_uMVP, 1, false, vp, 0);
        GLES30.glUniform4f(sh.fc_uColor, 1f, 1f, 1f, 1f);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, n);
        GLES30.glBindVertexArray(0);
    }
}
