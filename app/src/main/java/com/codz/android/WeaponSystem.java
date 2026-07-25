package com.codz.android;

/**
 * Manages weapon firing, ADS FOV interpolation, reload timers, and weapon
 * swapping. Hitscan weapons register damage directly via ZombieManager.
 * Projectile weapons (Ray Gun) spawn Bullet entities from BulletPool.
 */
public class WeaponSystem {
    private final PlayerController player;
    private final ParticleSystem particles;
    private final BulletPool bullets;
    private final ShaderCache shaders;
    private final MeshCache meshes;
    private final InputState input;
    private ZombieManager zombies;

    private final Weapon[] inventory = new Weapon[3];
    private int current = 0;

    private float fov = 75f;
    private float muzzleFlash;

    // pooled raycast result
    private final float[] fwd = new float[3];
    private final float[] hitPos = new float[3];

    private static final float ADS_FOV = 45f;
    private static final float ADS_LERP = 10f;

    public WeaponSystem(PlayerController p, ParticleSystem ps, BulletPool bp,
                        ShaderCache sh, MeshCache mm) {
        player = p;
        particles = ps;
        bullets = bp;
        shaders = sh;
        meshes = mm;
        input = p.getInput();

        inventory[0] = WeaponFactory.m1911();
        inventory[1] = WeaponFactory.m14();
        inventory[2] = null; // Ray Gun via mystery box
    }

    public void update(float dt) {
        Weapon w = inventory[current];
        if (w == null) return;

        // ---- ADS interpolation ----
        float targetFov = input.ads ? ADS_FOV : 75f;
        fov += (targetFov - fov) * Math.min(1, dt * ADS_LERP);

        // ---- Reload ----
        if (input.reloadPressed && !w.reloading && w.canReload()) w.startReload();
        if (w.reloading) {
            w.reloadTimer += dt;
            if (w.reloadTimer >= w.reloadTime) w.finishReload();
        }

        // ---- Firing ----
        muzzleFlash = Math.max(0, muzzleFlash - dt * 12f);
        boolean wantFire = input.fire && (w.isAutomatic || input.firePressed);
        if (wantFire && !w.reloading && w.fireCooldown <= 0 && w.currentMag > 0) {
            fire(w);
            w.fireCooldown = 1f / w.fireRate;
            w.currentMag--;
        }
        w.fireCooldown = Math.max(0, w.fireCooldown - dt);

        // ---- Swap ----
        if (input.swap) {
            // Cycle to next non-null
            for (int i = 1; i <= 2; i++) {
                int next = (current + i) % 3;
                if (inventory[next] != null) { current = next; break; }
            }
            input.swap = false;
        }
    }

    private void fire(Weapon w) {
        player.forward(fwd);
        muzzleFlash = 1f;
        // Recoil into camera
        float rp = w.recoilPattern[0] * (input.ads ? 0.5f : 1f);
        float ry = w.recoilPattern[1] * (input.ads ? 0.5f : 1f);
        player.addRecoil(rp, ry);

        if (w.projectile) {
            // Ray Gun: spawn projectile
            Bullet b = bullets.obtain();
            if (b != null) {
                b.init(player.cameraX(), player.cameraY() - 0.1f, player.cameraZ(),
                        fwd[0], fwd[1], fwd[2], w.damage, w.projectileSplash, w.projectileSpeed, true);
            }
        } else {
            // Hitscan - ray against zombies
            ZombieManager.HitResult res = zombies.raycastZombie(player, fwd, 80f);
            if (res != null && res.hit) {
                zombies.damageZombie(res.index, w.damage, res.headshot, player);
                // Spark particle at hit
                particles.spawn(res.x, res.y, res.z, 0.9f, 0.85f, 0.3f, 8);
            } else {
                // Tracer end far-ish (optional particle)
            }
        }
    }

    public void setZombieManager(ZombieManager zm) { zombies = zm; }
    public float getCurrentFov() { return fov; }
    public float muzzleIntensity() { return muzzleFlash; }
    public Weapon current() { return inventory[current]; }
    public boolean giveRayGun() {
        if (inventory[2] == null) {
            inventory[2] = WeaponFactory.rayGun();
            current = 2;
            return true;
        }
        // Refill ammo
        inventory[2].currentMag = inventory[2].magCapacity;
        inventory[2].currentReserve = inventory[2].reserveCapacity;
        current = 2;
        return true;
    }

    public void refillM14() {
        if (inventory[1] == null) inventory[1] = WeaponFactory.m14();
        inventory[1].currentReserve = inventory[1].reserveCapacity;
        inventory[1].currentMag = inventory[1].magCapacity;
    }
    public void setInputFirePressedConsumed() { /* handled via postFrame */ }
}
