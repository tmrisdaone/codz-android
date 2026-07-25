package com.codz.android;

/**
 * Base weapon definition. Per-instance mutable state lives on the instance
 * (mag, reserve, reload timer) so weapon objects can be pooled in the player
 * inventory without per-frame allocation. Immutable stats are read-only fields.
 */
public class Weapon {
    public String name;
    public int damage;
    public float fireRate;      // rounds per second
    public int magCapacity;
    public int reserveCapacity;
    public int currentMag;
    public int currentReserve;
    public float reloadTime;
    public float[] recoilPattern; // [pitchKick, yawKick]
    public boolean isAutomatic;
    public boolean projectile;
    public float projectileSpeed;
    public float projectileSplash;
    public float price;

    // Transient runtime
    public float fireCooldown;
    public boolean reloading;
    public float reloadTimer;

    public boolean canReload() {
        return currentMag < magCapacity && currentReserve > 0;
    }

    public void startReload() {
        reloading = true;
        reloadTimer = 0;
    }

    public void finishReload() {
        int need = magCapacity - currentMag;
        int take = Math.min(need, currentReserve);
        currentMag += take;
        currentReserve -= take;
        reloading = false;
    }
}
