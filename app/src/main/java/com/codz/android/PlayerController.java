package com.codz.android;

import android.opengl.Matrix;

/**
 * First-person 3D controller. Maintains position, yaw/pitch, health, points.
 * All math uses pre-allocated scratch arrays - no per-frame allocation.
 */
public class PlayerController {
    private final InputState input;

    // Position in world space (feet). Camera sits at pos + eyeHeight.
    public float posX, posY = 1.7f, posZ;
    public float yaw, pitch;
    public float eyeHeight = 1.7f;
    public float radius = 0.3f; // collision

    // Health
    public static final int MAX_HEALTH = 100;
    public int health = MAX_HEALTH;
    private float regenTimer;
    private static final float REGEN_DELAY = 5f;
    private static final float REGEN_RATE = 18f; // hp / sec after delay

    // Points
    public int points = 500;

    // Movement tuning
    private static final float MOVE_SPEED = 4.2f;
    private static final float SPRINT_MULT = 1.25f;
    private static final float PITCH_LIMIT = 89f;
    private static final float LOOK_SENS = 0.18f;

    // Recoil offsets applied to yaw/pitch, recover over time.
    private float recoilPitch, recoilYaw;
    private float recoilRecover;

    // Reusable scratch
    private final float[] fwd = new float[3];
    private final float[] right = new float[3];
    private final float[] view = new float[16];
    private final float[] tmpMat = new float[16];

    private WeaponSystem weapons;
    private ZombieManager zombies;

    private static final int MAX_EVENTS = 8;
    private final float[] damageEvents = new float[MAX_EVENTS * 3]; // dirX,dirZ,amount
    private int damageEventCount;

    public PlayerController(InputState in) { input = in; }
    public InputState getInput() { return input; }

    public void setWeaponSystem(WeaponSystem ws) { weapons = ws; }
    public void setZombieManager(ZombieManager zm) { zombies = zm; }

    public void addRecoil(float pPitch, float pYaw) {
        recoilPitch += pPitch;
        recoilYaw += pYaw;
        recoilRecover = 0.6f;
    }

    /** Inflict damage to player. dirX/dirZ = attacker direction (for HUD feedback). */
    public void damage(int amount, float dirX, float dirZ) {
        if (isDead()) return;
        health -= amount;
        regenTimer = 0;
        if (health < 0) health = 0;
        if (damageEventCount < MAX_EVENTS) {
            int i = damageEventCount++;
            damageEvents[i * 3] = dirX;
            damageEvents[i * 3 + 1] = dirZ;
            damageEvents[i * 3 + 2] = amount;
        }
    }

    public boolean isDead() { return health <= 0; }

    public void addPoints(int amt) { points += amt; }

    public boolean spendPoints(int amt) {
        if (points < amt) return false;
        points -= amt;
        return true;
    }

    public void update(float dt) {
        if (isDead()) return;

        // ----- Look -----
        // Apply queued look delta. Visible pitch/yaw include recoil; we keep base
        // and recoil separate so recovery functions as visual only.
        yaw += input.lookDX * LOOK_SENS;
        pitch += input.lookDY * LOOK_SENS;
        if (pitch > PITCH_LIMIT) pitch = PITCH_LIMIT;
        if (pitch < -PITCH_LIMIT) pitch = -PITCH_LIMIT;

        // Recoil recovery
        if (recoilRecover > 0) {
            recoilRecover -= dt;
            float f = Math.min(1, dt * 6f);
            recoilPitch += (0 - recoilPitch) * f;
            recoilYaw += (0 - recoilYaw) * f;
        }
        yaw += recoilYaw; pitch += recoilYaw * 0.05f;
        recoilYaw = 0;
        if (pitch > PITCH_LIMIT) pitch = PITCH_LIMIT;
        if (pitch < -PITCH_LIMIT) pitch = -PITCH_LIMIT;
        yaw = ((yaw % 360f) + 360f) % 360f;

        // ----- Movement vectors -----
        float yawRad = (float) Math.toRadians(yaw);
        fwd[0] = (float) -Math.sin(yawRad);
        fwd[1] = 0;
        fwd[2] = (float) -Math.cos(yawRad);
        right[0] = (float) Math.cos(yawRad);
        right[1] = 0;
        right[2] = (float) -Math.sin(yawRad);

        float mvX = input.moveX;
        float mvY = input.moveY;
        float speed = MOVE_SPEED * (input.ads ? 0.6f : 1f);
        if (Math.abs(mvX) > 0.95f && Math.abs(mvY) > 0.95f) speed *= SPRINT_MULT;

        float dx = (fwd[0] * mvY + right[0] * mvX) * speed * dt;
        float dz = (fwd[2] * mvY + right[2] * mvX) * speed * dt;

        // ----- Collision (simple AABB against world AABB walls) -----
        float nx = posX + dx;
        float nz = posZ + dz;
        if (!collides(nx, posZ)) posX = nx;
        if (!collides(posX, nz)) posZ = nz;

        // ----- Health regen -----
        regenTimer += dt;
        if (regenTimer >= REGEN_DELAY && health < MAX_HEALTH) {
            health = (int) Math.min(MAX_HEALTH, health + REGEN_RATE * dt);
        }
    }

    private boolean collides(float x, float z) {
        // Delegate to world if available
        if (zombies != null) {
            return zombies.worldCollides(x, z, radius);
        }
        // Out-of-map bounds
        return x < -40 || x > 40 || z < -40 || z > 40;
    }

    public void buildViewMatrix(float[] outView) {
        float cx = posX;
        float cy = posY;
        float cz = posZ;
        // Look target = position + forward
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float fX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float fY = (float) (-Math.sin(pitchRad));
        float fZ = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));
        Matrix.setLookAtM(outView, 0,
                cx, cy, cz,
                cx + fX, cy + fY, cz + fZ,
                0, 1, 0);
    }

    public float cameraX() { return posX; }
    public float cameraY() { return posY; }
    public float cameraZ() { return posZ; }

    /** Forward direction unit vector into out[0..2]. */
    public void forward(float[] out) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        out[0] = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        out[1] = (float) (-Math.sin(pitchRad));
        out[2] = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));
    }
}
