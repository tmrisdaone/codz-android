package com.codz.android;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private final InputState input;

    // Camera / matrices - re-used every frame, zero per-frame allocation.
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];

    // Core systems
    private PlayerController player;
    private WeaponSystem weapons;
    private ZombieManager zombies;
    private GameHUD hud;
    private WorldMap world;
    private ShaderCache shaders;
    private MeshCache meshes;
    private ParticleSystem particles;
    private BulletPool bullets;

    private long lastTimeNanos;
    private float fpsAccum;
    private int fpsFrames;

    public GameRenderer(Context ctx, InputState in) {
        context = ctx;
        input = in;
    }

    public void onPause() { /* pause flag */ }
    public void onResume() { lastTimeNanos = 0; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.04f, 0.04f, 0.06f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);

        shaders = new ShaderCache(context);
        GameHUDShaders.set(shaders);
        meshes = new MeshCache();
        particles = new ParticleSystem(256);
        particles.initGL(shaders);
        bullets = new BulletPool(64);
        bullets.setMeshes(meshes, shaders);
        world = new WorldMap(meshes, shaders);
        player = new PlayerController(input);
        weapons = new WeaponSystem(player, particles, bullets, shaders, meshes);
        zombies = new ZombieManager(player, world, meshes, shaders, particles);
        hud = new GameHUD(context);
        player.setWeaponSystem(weapons);
        player.setZombieManager(zombies);
        weapons.setZombieManager(zombies);
        world.mysteryBoxCallback = () -> weapons.giveRayGun();
        world.wallBuyCallback = () -> weapons.refillM14();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        float aspect = (float) width / (float) height;
        Matrix.perspectiveM(projection, 0, 75f, aspect, 0.1f, 200f);
        hud.resize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = (lastTimeNanos == 0) ? 1f / 60f : (now - lastTimeNanos) * 1e-9f;
        lastTimeNanos = now;
        if (dt > 0.05f) dt = 0.05f; // clamp to avoid spiral after hitches

        // ---- UPDATE ----
        player.update(dt);
        weapons.update(dt);
        zombies.update(dt);
        bullets.update(dt, zombies);
        particles.update(dt);
        world.update(dt, player);

        // ---- RENDER ----
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // ADS FOV interpolation is driven on weapon system; adjust projection.
        float fov = weapons.getCurrentFov();
        float aspect = (projection[0] != 0f) ? (1f / projection[0]) * (float) Math.tan(Math.toRadians(75f) * 0.5) : 1f;
        // Rebuild projection to reflect FOV (cheap; once per frame).
        int[] vpSize = new int[4];
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, vpSize, 0);
        Matrix.perspectiveM(projection, 0, fov, (float) vpSize[2] / vpSize[3], 0.1f, 200f);

        player.buildViewMatrix(view);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);

        world.render(vp);
        bullets.render(vp, shaders);
        zombies.render(vp);
        particles.render(vp, shaders);

        // HUD pass - disable depth.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        hud.render(player, weapons, zombies);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }
}
