package com.codz.android;

/**
 * Manages wave spawning, a pooled zombie array, AI state updates, and
 * hit-detection raycasts. Zero per-frame allocation - all zombies live in
 * a fixed-capacity pool; dead ones are recycled at spawn time.
 *
 * Round N zombie count: floor(0.24*N^2 + 12*N + 6)
 * Zombie HP: 150 base, +10% per round past R9.
 */
public class ZombieManager {
    private final PlayerController player;
    private final WorldMap world;
    private final MeshCache meshes;
    private final ShaderCache shaders;
    private final ParticleSystem particles;

    private final Zombie[] pool;
    private final int capacity;
    private int aliveCount;

    private int round = 0;
    private int toSpawnThisRound;
    private int spawnedThisRound;
    private int killedThisRound;
    private float spawnTimer;
    private static final float SPAWN_INTERVAL = 1.6f;

    private boolean roundTransitioning;
    private float roundTransitionTimer;
    private static final float ROUND_TRANSITION_DURATION = 4f;

    // Hit result scratch - reused across raycast calls (single-threaded render).
    public final HitResult hitResult = new HitResult();

    private final float[] vA = new float[3];
    private final float[] vB = new float[3];

    public ZombieManager(PlayerController p, WorldMap w, MeshCache mm,
                         ShaderCache sh, ParticleSystem ps) {
        player = p;
        world = w;
        meshes = mm;
        shaders = sh;
        particles = ps;
        capacity = 32;
        pool = new Zombie[capacity];
        for (int i = 0; i < capacity; i++) pool[i] = new Zombie();
        startNextRound();
    }

    public static int zombieCountForRound(int n) {
        return (int) Math.floor(0.24 * n * n + 12 * n + 6);
    }

    public static int zombieHpForRound(int n) {
        if (n <= 9) return 150;
        double scale = Math.pow(1.1, n - 9);
        return (int) (150 * scale);
    }

    public void startNextRound() {
        round++;
        toSpawnThisRound = zombieCountForRound(round);
        spawnedThisRound = 0;
        killedThisRound = 0;
        spawnTimer = 0;
        aliveCount = 0;
        for (Zombie z : pool) z.state = Zombie.State.DEAD;
        roundTransitioning = true;
        roundTransitionTimer = ROUND_TRANSITION_DURATION;
    }

    public int round() { return round; }
    public boolean inTransition() { return roundTransitioning; }
    public float transitionProgress() {
        return 1f - (roundTransitionTimer / ROUND_TRANSITION_DURATION);
    }

    public void update(float dt) {
        if (roundTransitioning) {
            roundTransitionTimer -= dt;
            if (roundTransitionTimer <= 0) roundTransitioning = false;
        }

        // Do not spawn during transition.
        if (!roundTransitioning) {
            spawnTimer -= dt;
            if (spawnTimer <= 0 && spawnedThisRound < toSpawnThisRound) {
                spawnFromPool();
                spawnTimer = SPAWN_INTERVAL;
            }
        }

        float hp = zombieHpForRound(round);
        float px = player.posX, pz = player.posZ;
        aliveCount = 0;
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD) continue;
            aliveCount++;
            updateZombie(z, dt, hp, px, pz, i);
        }

        // Round complete?
        if (killedThisRound >= toSpawnThisRound && spawnedThisRound >= toSpawnThisRound
                && aliveCount == 0 && !roundTransitioning) {
            startNextRound();
        }
    }

    private void spawnFromPool() {
        // Find a dead slot
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD) {
                float[] sp = world.randomSpawnAwayFrom(player.posX, player.posZ);
                z.spawn(sp[0], sp[1], sp[2], zombieHpForRound(round));
                spawnedThisRound++;
                return;
            }
        }
    }

    private void updateZombie(Zombie z, float dt, float hp, float px, float pz, int idx) {
        z.update(dt, px, pz, player);

        // Attack resolution: when attacking and cooldown elapsed, apply hit & reset.
        if (z.state == Zombie.State.ATTACKING && z.attackTimer <= 0) {
            float dx = px - z.x;
            float dz = pz - z.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist <= 1.8f) { // still in melee range
                if (dist > 0.0001f) { dx /= dist; dz /= dist; }
                else { dx = 1; dz = 0; }
                player.damage(z.attackDamage, dx, dz);
            }
            z.attackTimer = z.attackCooldown;
        }
    }

    /** Raycast against all zombies. Returns shared hitResult. */
    public HitResult raycastZombie(PlayerController p, float[] dir, float maxDist) {
        hitResult.hit = false;
        float bestT = maxDist;
        int bestIdx = -1;
        float ox = p.cameraX(), oy = p.cameraY(), oz = p.cameraZ();
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD || z.state == Zombie.State.SPAWNING) continue;
            // Sphere intersection centered at (z.x, z.y + upperChest, z.z), radius ~0.45
            float cx = z.x, cy = z.y + 1.2f, cz = z.z;
            float ox2 = ox - cx, oy2 = oy - cy, oz2 = oz - cz;
            float b = ox2 * dir[0] + oy2 * dir[1] + oz2 * dir[2];
            float c = ox2 * ox2 + oy2 * oy2 + oz2 * oz2 - 0.5f * 0.5f;
            float disc = b * b - c;
            if (disc < 0) continue;
            float t = -b - (float) Math.sqrt(disc);
            if (t < 0) continue;
            if (t < bestT) {
                bestT = t;
                bestIdx = i;
                // Headshot if hit point near top of bounding volume
                float hy = oy + dir[1] * t;
                hitResult.headshot = (hy - z.y) >= 1.5f;
            }
        }
        if (bestIdx >= 0) {
            hitResult.hit = true;
            hitResult.index = bestIdx;
            hitResult.x = ox + dir[0] * bestT;
            hitResult.y = oy + dir[1] * bestT;
            hitResult.z = oz + dir[2] * bestT;
        }
        return hitResult;
    }

    public void damageZombie(int idx, float dmg, boolean headshot, PlayerController p) {
        Zombie z = pool[idx];
        if (z.state == Zombie.State.DEAD) return;
        float dmgFinal = headshot ? dmg * 4f : dmg;
        z.hp -= dmgFinal;
        p.addPoints(headshot ? 100 : 10);
        if (z.hp <= 0) {
            z.state = Zombie.State.DEAD;
            killedThisRound++;
            p.addPoints(60);
            particles.spawn(z.x, z.y + 1f, z.z, 0.6f, 0.1f, 0.1f, 16);
        } else {
            // Hit flash
            z.flashTimer = 0.08f;
        }
    }

    public HitResult raycastZombiePoint(float x, float y, float z) {
        // Returns nearest zombie within 0.5f radius of the point.
        hitResult.hit = false;
        float bestD2 = 0.25f * 0.25f;
        int bestIdx = -1;
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD || z.state == Zombie.State.SPAWNING) continue;
            float dx = z.x - x, dy = (z.y + 1.2f) - y, dz = z.z - z;
            float d2 = dx*dx + dy*dy + dz*dz;
            if (d2 < bestD2) { bestD2 = d2; bestIdx = i; }
        }
        if (bestIdx >= 0) {
            hitResult.hit = true;
            hitResult.index = bestIdx;
            hitResult.x = x; hitResult.y = y; hitResult.z = z;
            hitResult.headshot = false;
        }
        return hitResult;
    }

    /** AI splash + projectile hitscan. Called by BulletPool for projectile bullets. */
    public void damageZombieSplash(float x, float y, float z, float radius, float dmg, PlayerController p) {
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD) continue;
            float dx = z.x - x, dy = (z.y + 1f) - y, dz = z.z - z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= radius * radius) {
                float falloff = 1f - (float) Math.sqrt(d2) / radius;
                damageZombie(i, dmg * Math.max(0.3f, falloff), false, p);
            }
        }
    }

    public boolean worldCollides(float x, float z, float r) {
        return world.collides(x, z, r);
    }

    public void render(float[] vp) {
        for (int i = 0; i < capacity; i++) {
            Zombie z = pool[i];
            if (z.state == Zombie.State.DEAD) continue;
            meshes.drawZombie(vp, shaders, z);
        }
    }

    public int aliveCount() { return aliveCount; }

    /** Reusable hit result. */
    public static final class HitResult {
        public boolean hit;
        public int index;
        public float x, y, z;
        public boolean headshot;
    }
}
