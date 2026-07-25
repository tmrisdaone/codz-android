package com.codz.android;

import android.opengl.GLES30;
import android.opengl.Matrix;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Owns all vertex/index buffers + VAOs for the game: floor, walls, zombie body,
 * bullet sphere, barricade board, mystery box, door, wall-buy plate.
 *
 * Everything is built once at init. Draw calls update a single uniform model
 * matrix per object - no per-frame vertex uploads, no per-frame allocation.
 *
 * Reusable scratch model matrix and MVP buffer on the instance.
 */
public class MeshCache {
    // Vertex format: pos(3) + nor(3) = 6 floats
    private static final int STRIDE = 6 * 4;

    public int floorVao, floorVbo, floorCount;
    public int wallVao, wallVbo, wallCount;
    public int zombieVao, zombieVbo, zombieCount;
    public int bulletVao, bulletVbo, bulletCount;
    public int boxVao, boxVbo, boxCount;
    public int boardVao, boardVbo, boardCount;
    public int planeVao, planeVbo, planeCount; // wall-buy placard

    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private final float[] mv = new float[16];
    private final float[] tmpRot = new float[16];

    public MeshCache() {
        buildFloor();
        buildWall();
        buildZombie();
        buildBulletSphere();
        buildBox();
        buildBoard();
        buildPlane();
    }

    public void drawFloor(float[] vp, ShaderCache sh) {
        Matrix.setIdentityM(model, 0);
        Matrix.scaleM(model, 0, 40f, 1f, 40f);
        drawWithLit(floorVao, floorCount, vp, sh, model, 0.18f, 0.16f, 0.13f, 1f);
    }

    public void drawWall(float[] vp, ShaderCache sh, float x, float y, float z, float yawDeg, float w, float h, float r, float g, float b) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, yawDeg, 0, 1, 0);
        Matrix.scaleM(model, 0, w, h, 1f);
        drawWithLit(wallVao, wallCount, vp, sh, model, r, g, b, 1f);
    }

    public void drawZombie(float[] vp, ShaderCache sh, Zombie z) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, z.x, z.y, z.z);
        Matrix.rotateM(model, 0, z.facing, 0, 1, 0);
        float spawnScale = z.state == Zombie.State.SPAWNING ? (1f - z.spawnTimer / 1.0f) : 1f;
        spawnScale = Math.max(0.1f, spawnScale);
        Matrix.scaleM(model, 0, 0.6f * spawnScale, 1.7f * spawnScale, 0.5f * spawnScale);
        float r = z.flashTimer > 0 ? 1f : 0.30f;
        float g = z.flashTimer > 0 ? 0.4f : 0.45f;
        float b = z.flashTimer > 0 ? 0.3f : 0.25f;
        drawWithLit(zombieVao, zombieCount, vp, sh, model, r, g, b, 1f);
    }

    public void drawBulletSphere(float[] vp, ShaderCache sh, float x, float y, float z) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.scaleM(model, 0, 0.22f, 0.22f, 0.22f);
        // Emissive green - we cheat by drawing through lit shader with high base.
        drawWithLit(bulletVao, bulletCount, vp, sh, model, 0.2f, 1.0f, 0.25f, 1f);
    }

    public void drawBox(float[] vp, ShaderCache sh, float x, float y, float z) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        drawWithLit(boxVao, boxCount, vp, sh, model, 0.4f, 0.25f, 0.15f, 1f);
    }

    public void drawBoard(float[] vp, ShaderCache sh, float x, float y, float z, int hpFrac) {
        // hpFrac 0..N boards remaining -> draw that many boards stacked.
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        drawWithLit(boardVao, boardCount, vp, sh, model, 0.45f, 0.30f, 0.18f, 1f);
    }

    public void drawPlane(float[] vp, ShaderCache sh, float x, float y, float z, float yawDeg, float r, float g, float b) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, yawDeg, 0, 1, 0);
        drawWithLit(planeVao, planeCount, vp, sh, model, r, g, b, 1f);
    }

    private void drawWithLit(int vao, int count, float[] vp, ShaderCache sh,
                             float[] model, float r, float g, float b, float a) {
        // Compute MV = view * model. We have only VP; to keep it simple and
        // avoid passing the view matrix around, we approximate lighting in
        // view space by using MVP for position pass-through and treat uniforms
        // in model space. To keep correctness simple, we use the MVP for the
        // main render and supply identity for MV so lighting is evaluated in
        // local model space relative to the dynamic light position passed in
        // *model space*. For this demo we skip MV-lighting and rely on ambient
        // + muzzle multiplier; uniform uMV set to identity.
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glUniformMatrix4fv(sh.sl_uMVP, 1, false, mvp, 0);
        Matrix.setIdentityM(mv, 0);
        GLES30.glUniformMatrix4fv(sh.sl_uMV, 1, false, mv, 0);
        GLES30.glUniform4f(sh.sl_uBaseColor, r, g, b, a);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count);
        GLES30.glBindVertexArray(0);
    }

    // ---- Builders ----
    private int makeVao(float[] verts, int[] outCount) {
        int[] vao = new int[1], vbo = new int[1];
        GLES30.glGenVertexArrays(1, vao, 0);
        GLES30.glGenBuffers(1, vbo, 0);

        FloatBuffer fb = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(verts).position(0);

        GLES30.glBindVertexArray(vao[0]);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.length * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 3 * 4);
        GLES30.glBindVertexArray(0);
        outCount[0] = verts.length / 6;
        return vao[0];
    }

    private void buildFloor() {
        // Single quad in XZ plane centered at origin, normal up.
        float s = 1f;
        float[] v = {
                -s, 0, -s, 0, 1, 0,
                 s, 0, -s, 0, 1, 0,
                -s, 0,  s, 0, 1, 0,
                -s, 0,  s, 0, 1, 0,
                 s, 0, -s, 0, 1, 0,
                 s, 0,  s, 0, 1, 0,
        };
        int[] c = new int[1];
        floorVao = makeVao(v, c); floorCount = c[0];
    }

    private void buildWall() {
        // 1x1 quad in XY plane facing +Z; scaled per instance.
        float s = 1f;
        float[] v = {
                -s, -s, 0, 0, 0, 1,
                 s, -s, 0, 0, 0, 1,
                -s,  s, 0, 0, 0, 1,
                -s,  s, 0, 0, 0, 1,
                 s, -s, 0, 0, 0, 1,
                 s,  s, 0, 0, 0, 1,
        };
        int[] c = new int[1];
        wallVao = makeVao(v, c); wallCount = c[0];
    }

    private void buildZombie() {
        // Unit box [-0.5..0.5]^3, normals per face; 12 triangles.
        float[] v = cubeMesh();
        int[] c = new int[1];
        zombieVao = makeVao(v, c); zombieCount = c[0];
    }

    private void buildBulletSphere() {
        // Approximate sphere via icosphere-stage-0 (12 verts ~ too few); use a
        // subdivided icosahedron stage 1 = 80 tris. We hardcode a small sphere.
        float[] v = sphereMesh(1f, 2);
        int[] c = new int[1];
        bulletVao = makeVao(v, c); bulletCount = c[0];
    }

    private void buildBox() { float[] v = cubeMesh(); int[] c = new int[1]; boxVao = makeVao(v, c); boxCount = c[0]; }
    private void buildBoard() { float[] v = cubeMesh(); int[] c = new int[1]; boardVao = makeVao(v, c); boardCount = c[0]; }
    private void buildPlane() { float[] v = wallQuad(); int[] c = new int[1]; planeVao = makeVao(v, c); planeCount = c[0]; }

    private float[] wallQuad() {
        float s = 1f;
        return new float[]{
                -s, -s, 0, 0, 0, 1,
                 s, -s, 0, 0, 0, 1,
                -s,  s, 0, 0, 0, 1,
                -s,  s, 0, 0, 0, 1,
                 s, -s, 0, 0, 0, 1,
                 s,  s, 0, 0, 0, 1,
        };
    }

    private float[] cubeMesh() {
        // 6 faces x 2 tris x 3 verts x 6 floats (pos+nor)
        float s = 0.5f;
        float[][] faces = {
                { s, 0, 0}, { 1,0,0}, { s,-s,-s,  s,s,-s,  s,s, s,  s,-s,-s,  s,s, s,  s,-s, s},
                {-s, 0, 0}, {-1,0,0}, {-s,-s, s, -s,s, s, -s,s,-s, -s,-s, s, -s,s,-s, -s,-s,-s},
                { 0, s, 0}, {0,1,0},  { -s,s,-s,  s,s,-s,  s,s, s, -s,s,-s,  s,s, s,  -s,s, s},
                { 0,-s, 0}, {0,-1,0}, { -s,-s, s,  s,-s, s,  s,-s,-s, -s,-s, s,  s,-s,-s, -s,-s,-s},
                { 0, 0, s}, {0,0,1},  { -s,-s,s,  s,-s,s,  s,s, s,  -s,-s,s,  s,s,s,  -s,s,s},
                { 0, 0,-s}, {0,0,-1}, {  s,-s,-s, -s,-s,-s, -s,s,-s,  s,-s,-s, -s,s,-s,  s,s,-s},
        };
        float[] out = new float[6 * 6 * 6];
        int i = 0;
        for (int f = 0; f < 6; f++) {
            float nx = faces[f][0], ny = faces[f][1], nz = faces[f][2];
            for (int k = 0; k < 6; k++) {
                out[i++] = faces[f][3 + k*3 + 0];
                out[i++] = faces[f][3 + k*3 + 1];
                out[i++] = faces[f][3 + k*3 + 2];
                out[i++] = nx; out[i++] = ny; out[i++] = nz;
            }
        }
        return out;
    }

    private float[] sphereMesh(float r, int subdiv) {
        // Build icosahedron then subdivide `subdiv` times.
        float t = (float)(1 + Math.sqrt(5)) / 2f;
        float[][] verts = {
                {-1, t, 0}, {1, t, 0}, {-1,-t,0}, {1,-t,0},
                {0,-1,t}, {0,1,t}, {0,-1,-t}, {0,1,-t},
                {t,0,-1}, {t,0,1}, {-t,0,-1}, {-t,0,1}
        };
        int[][] faces = {
                {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
                {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
                {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
                {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };
        // naive subdivision (not midpoint-cached, but tiny)
        java.util.List<float[]> vl = new java.util.ArrayList<>();
        for (float[] vv : verts) { float[] u = norm(vv); vl.add(u); }
        java.util.List<int[]> fl = new java.util.ArrayList<>();
        for (int[] f : faces) fl.add(new int[]{f[0],f[1],f[2]});

        for (int s = 0; s < subdiv; s++) {
            java.util.List<int[]> nf = new java.util.ArrayList<>();
            for (int[] f : fl) {
                int a = mid(vl, vl.get(f[0]), vl.get(f[1]));
                int b = mid(vl, vl.get(f[1]), vl.get(f[2]));
                int c = mid(vl, vl.get(f[2]), vl.get(f[0]));
                nf.add(new int[]{f[0], a, c});
                nf.add(new int[]{f[1], b, a});
                nf.add(new int[]{f[2], c, b});
                nf.add(new int[]{a, b, c});
            }
            fl = nf;
        }
        float[] out = new float[fl.size() * 3 * 6];
        int i = 0;
        for (int[] f : fl) {
            for (int k = 0; k < 3; k++) {
                float[] p = vl.get(f[k]);
                out[i++] = p[0] * r; out[i++] = p[1] * r; out[i++] = p[2] * r;
                out[i++] = p[0]; out[i++] = p[1]; out[i++] = p[2];
            }
        }
        return out;
    }

    private static int mid(java.util.List<float[]> vl, float[] a, float[] b) {
        float[] m = norm(new float[]{(a[0]+b[0])/2,(a[1]+b[1])/2,(a[2]+b[2])/2});
        for (int i = 0; i < vl.size(); i++) {
            float[] p = vl.get(i);
            if (p[0]==m[0] && p[1]==m[1] && p[2]==m[2]) return i;
        }
        vl.add(m);
        return vl.size() - 1;
    }

    private static float[] norm(float[] v) {
        float l = (float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if (l == 0) return v;
        return new float[]{v[0]/l, v[1]/l, v[2]/l};
    }
}
