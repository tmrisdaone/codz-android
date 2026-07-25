package com.codz.android;

/**
 * Single projectile entity. Rendered as a glowing green sprite-like cube.
 */
public class Bullet {
    public boolean active;
    public float x, y, z;
    public float vx, vy, vz;
    public float damage;
    public float splash;
    public float life;
    public float maxLife = 3f;
    public boolean fromPlayer;

    public void init(float ox, float oy, float oz, float dx, float dy, float dz,
                     float dmg, float splashRadius, float speed, boolean fromPlayer) {
        active = true;
        x = ox; y = oy; z = oz;
        vx = dx * speed;
        vy = dy * speed;
        vz = dz * speed;
        damage = dmg;
        splash = splashRadius;
        life = maxLife;
        this.fromPlayer = fromPlayer;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
        life -= dt;
        if (life <= 0) active = false;
    }

    public void render(float[] vp, ShaderCache shaders) {
        shaders.drawBullet(vp, x, y, z);
    }
}
