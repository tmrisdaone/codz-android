package com.codz.android;

public final class WeaponFactory {
    private WeaponFactory() {}

    public static Weapon m1911() {
        Weapon w = new Weapon();
        w.name = "M1911";
        w.damage = 30;
        w.fireRate = 5f;
        w.magCapacity = 8;
        w.reserveCapacity = 80;
        w.currentMag = 8;
        w.currentReserve = 80;
        w.reloadTime = 1.6f;
        w.recoilPattern = new float[]{ 1.6f, 0.4f };
        w.isAutomatic = false;
        w.projectile = false;
        w.price = 0;
        return w;
    }

    public static Weapon m14() {
        Weapon w = new Weapon();
        w.name = "M14";
        w.damage = 65;
        w.fireRate = 3.2f;
        w.magCapacity = 8;
        w.reserveCapacity = 96;
        w.currentMag = 8;
        w.currentReserve = 96;
        w.reloadTime = 2.1f;
        w.recoilPattern = new float[]{ 2.6f, 0.6f };
        w.isAutomatic = false;
        w.projectile = false;
        w.price = 500;
        return w;
    }

    public static Weapon rayGun() {
        Weapon w = new Weapon();
        w.name = "Ray Gun";
        w.damage = 1000;             // splash-heavy, lower direct
        w.fireRate = 2.5f;
        w.magCapacity = 20;
        w.reserveCapacity = 160;
        w.currentMag = 20;
        w.currentReserve = 160;
        w.reloadTime = 3.0f;
        w.recoilPattern = new float[]{ 3.0f, 0.2f };
        w.isAutomatic = true;
        w.projectile = true;
        w.projectileSpeed = 40f;
        w.projectileSplash = 4.5f;
        w.price = 950; // mystery box cost
        return w;
    }
}
