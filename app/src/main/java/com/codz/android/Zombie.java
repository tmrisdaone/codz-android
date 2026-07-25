package com.codz.android;

/**
 * Single zombie entity - state machine + steering, no allocations in update().
 */
public class Zombie {
    public enum State { SPAWNING, PURSUING, ATTACKING, DEAD }

    public State state = State.DEAD;
    public float x, y, z;       // feet position
    public float vx, vz;        // current velocity
    public float facing;
    public float hp;
    public float maxHp;
    public float spawnTimer;
    public float attackTimer;
    public float attackCooldown = 1.5f;
    public int attackDamage = 25;
    public float flashTimer;
    public float animPhase;

    private static final float MOVE_SPEED = 2.0f;
    private static final float SPAWN_DURATION = 1.0f;
    private static final float ATTACK_RANGE = 1.5f;
    private static final float ATTACK_ARC = 0.45f; // horizontal tolerance

    public boolean isAlive() { return state != State.DEAD; }

    public void spawn(float sx, float sy, float sz, float hp) {
        x = sx; y = sy; z = sz;
        vx = vz = 0;
        facing = 0;
        this.hp = hp;
        this.maxHp = hp;
        state = State.SPAWNING;
        spawnTimer = SPAWN_DURATION;
        attackTimer = 0;
        flashTimer = 0;
        animPhase = (float) Math.random() * 6.28f;
    }

    public void update(float dt, float px, float pz, PlayerController player) {
        flashTimer = Math.max(0, flashTimer - dt);
        animPhase += dt * 6f;
        attackTimer = Math.max(0, attackTimer - dt);

        if (state == State.SPAWNING) {
            spawnTimer -= dt;
            if (spawnTimer <= 0) state = State.PURSUING;
            return;
        }
        if (state == State.DEAD) return;

        float dx = px - x;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist <= ATTACK_RANGE) {
            state = State.ATTACKING;
            // attack is resolved by manager when attackTimer <= 0
        } else if (state == State.ATTACKING && dist > ATTACK_RANGE + 0.2f) {
            state = State.PURSUING;
        }

        if (state == State.PURSUING) {
            if (dist > 0.0001f) {
                float nx = dx / dist;
                float nz = dz / dist;
                // Simple obstacle avoidance: lateral probe via world
                vx = nx * MOVE_SPEED;
                vz = nz * MOVE_SPEED;
                facing = (float) Math.toDegrees(Math.atan2(-nx, -nz));
            }
            x += vx * dt;
            z += vz * dt;
        } else if (state == State.ATTACKING) {
            if (dist > 0.0001f) {
                facing = (float) Math.toDegrees(Math.atan2(-dx / dist, -dz / dist));
            }
            // Strike lands when cooldown elapses; manager applies the hit.
            // attackTimer is decremented above; do NOT reset here.
        }
    }
}
