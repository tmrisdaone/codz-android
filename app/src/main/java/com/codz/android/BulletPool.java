package com.codz.android;

/**
 * Pooled projectile system for Ray Gun and similar projectile weapons.
 * Fixed capacity; obtain() returns null if pool exhausted (drop the shot).
 */
public class BulletPool {
    private final Bullet[] pool;
    private final int capacity;
    private MeshCache meshes;
    private ShaderCache shaders;

    public BulletPool(int cap) {
        capacity = cap;
        pool = new Bullet[cap];
        for (int i = 0; i < cap; i++) pool[i] = new Bullet();
    }

    public void setMeshes(MeshCache mm, ShaderCache sh) { meshes = mm; shaders = sh; }

    public Bullet obtain() {
        for (int i = 0; i < capacity; i++) {
            if (!pool[i].active) return pool[i];
        }
        return null;
    }

    public void update(float dt, ZombieManager zombies) {
        for (int i = 0; i < capacity; i++) {
            Bullet b = pool[i];
            if (!b.active) continue;
            b.update(dt);
            // World bounds
            if (b.life <= 0 || Math.abs(b.x) > 60 || Math.abs(b.z) > 60 || b.y < 0) {
                b.active = false;
                continue;
            }
            // Cheap hit check against zombie spheres
            ZombieManager.HitResult hr = zombies.raycastZombiePoint(b.x, b.y, b.z);
            if (hr != null && hr.hit) {
                zombies.damageZombieSplash(b.x, b.y, b.z, b.splash, b.damage, b.fromPlayer);
                b.active = false;
            }
        }
    }

    public void render(float[] vp, ShaderCache shaders) {
        MeshCache mm = this.meshes;
        for (int i = 0; i < capacity; i++) {
            if (pool[i].active) mm.drawBulletSphere(vp, shaders, pool[i].x, pool[i].y, pool[i].z);
        }
    }
}
