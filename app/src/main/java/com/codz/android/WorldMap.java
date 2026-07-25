package com.codz.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Static map definition: walls (AABBs), barricades/windows, doors, wall-buys,
 * mystery box, and spawn nodes. All data is built once at construction - no
 * per-frame allocation. Lighting/material rendering delegated to MeshCache.
 */
public class WorldMap {
    private final MeshCache meshes;
    private final ShaderCache shaders;

    // Wall AABBs (min/max xz, height) - simple collision against these.
    private final float[][] walls = {
            // outer perimeter
            {-40, -40, 40, -38, 4},
            {-40,  38, 40,  40, 4},
            {-40, -40, -38,  40, 4},
            {  38, -40,  40,  40, 4},
            // interior partition
            { -4, -10, -3,  10, 3},
            {  10,   0,  11,  15, 3},
    };

    // Barricade/window definitions
    private static final class Barricade {
        float x, z;            // board center (player-facing)
        float yawDeg;
        int maxBoards = 6;
        int boards = 6;        // current board count
        float respawnTimer;
    }
    private final List<Barricade> barricades = new ArrayList<>();
    private final List<Barricade> spawnLink = new ArrayList<>(); // barricades w/ spawn nodes behind them

    // Spawn nodes (world positions)
    private final float[][] spawns = {
            {-30, 0, -30}, { 30, 0, -30}, {-30, 0,  30}, { 30, 0,  30},
            {   0, 0, -35}, {  0, 0,  35}, {-35, 0, 0}, { 35, 0, 0},
    };

    // Interactables
    public float[] mysteryBoxPos = { 12, 0, 12 };
    public float[] wallBuyM14Pos = { -8, 1.4f, -10 };
    public float[] doorClearPos = { 10, 1f, 7 }; // 750 pts
    public boolean doorUnlocked = false;

    private final Random rng = new Random();
    private final float[] tmpSpawn = new float[3];

    public WorldMap(MeshCache mm, ShaderCache sh) {
        meshes = mm;
        shaders = sh;
        for (int i = 0; i < 6; i++) {
            Barricade b = new Barricade();
            b.x = -20 + i * 8;
            b.z = -39;
            b.yawDeg = 0;
            barricades.add(b);
        }
    }

    public boolean collides(float x, float z, float r) {
        if (x < -39 || x > 39 || z < -39 || z > 39) return true;
        for (float[] w : walls) {
            if (x + r > w[0] && x - r < w[2] && z + r > w[1] && z - r < w[3]) return true;
        }
        // Door collision unless unlocked
        if (!doorUnlocked) {
            if (x > doorClearPos[0] - 1.5 && x < doorClearPos[0] + 1.5 &&
                z > doorClearPos[2] - 0.5 && z < doorClearPos[2] + 0.5) return true;
        }
        return false;
    }

    /** Returns a spawn point as far as reasonable from the player. */
    public float[] randomSpawnAwayFrom(float px, float pz) {
        // MeshCache.render does not need this; reuse a tmp slot
        for (int attempts = 0; attempts < 8; attempts++) {
            int idx = rng.nextInt(spawns.length);
            tmpSpawn[0] = spawns[idx][0];
            tmpSpawn[1] = spawns[idx][1];
            tmpSpawn[2] = spawns[idx][2];
            float dx = tmpSpawn[0] - px, dz = tmpSpawn[2] - pz;
            if (dx*dx + dz*dz > 15*15) return tmpSpawn;
        }
        tmpSpawn[0] = spawns[0][0]; tmpSpawn[1] = spawns[0][1]; tmpSpawn[2] = spawns[0][2];
        return tmpSpawn;
    }

    public void update(float dt, PlayerController player) {
        // Player within barricade proximity: rebuild on interact press.
        for (Barricade b : barricades) {
            if (b.boards < b.maxBoards) b.respawnTimer += dt;
            float dx = b.x - player.posX, dz = b.z - player.posZ;
            boolean near = (dx*dx + dz*dz) < 3*3;
            if (near && player.getInput().interactPressed && b.boards < b.maxBoards) {
                b.boards++;
                b.respawnTimer = 0;
                player.addPoints(10);
            }
        }
    }

    /** Zombies breach barricades: drop one board when a zombie is near. */
    public boolean zombieAtBarricade(Zombie z, float dt) {
        for (Barricade b : barricades) {
            float dx = b.x - z.x, dz = b.z - z.z;
            if (dx*dx + dz*dz < 2*2) {
                if (b.boards > 0) {
                    b.respawnTimer += dt;
                    if (b.respawnTimer > 1.0f) {
                        b.boards--;
                        b.respawnTimer = 0;
                    }
                } else {
                    return true; // fully breached
                }
                return false;
            }
        }
        return true;
    }

    public void render(float[] vp) {
        // Floor
        meshes.drawFloor(vp, shaders);

        // Walls
        for (float[] w : walls) {
            float cx = (w[0] + w[2]) * 0.5f;
            float cz = (w[1] + w[3]) * 0.5f;
            float ww = w[2] - w[0];
            float hh = w[4];
            // Wall quad mesh is XY-facing; rotate to align along X depending.
            float yaw = (ww > 4) ? 0 : 90;
            meshes.drawWall(vp, shaders, cx, w[4] * 0.5f, cz, yaw, ww, hh, 0.35f, 0.28f, 0.24f);
        }

        // Barricades
        for (Barricade b : barricades) {
            for (int i = 0; i < b.boards; i++) {
                float y = 0.3f + i * 0.35f + 0.2f;
                meshes.drawBoard(vp, shaders, b.x, y, b.z, i);
            }
        }

        // Mystery Box
        meshes.drawBox(vp, shaders, mysteryBoxPos[0], 0.5f, mysteryBoxPos[2]);
        // Wall buy placard
        meshes.drawPlane(vp, shaders, wallBuyM14Pos[0], wallBuyM14Pos[1], wallBuyM14Pos[2], 0, 0.5f, 0.35f, 0.2f);

        // Door
        if (!doorUnlocked) {
            meshes.drawWall(vp, shaders, doorClearPos[0], 1f, doorClearPos[2], 90, 1f, 2f, 0.3f, 0.20f, 0.15f);
        }
    }

    public void tryInteract(PlayerController p) {
        // Mystery Box
        float dx = mysteryBoxPos[0] - p.posX;
        float dz = mysteryBoxPos[2] - p.posZ;
        if (dx*dx + dz*dz < 2*2) {
            if (p.spendPoints(950)) {
                // give ray gun handled by WeaponSystem via callback
                if (mysteryBoxCallback != null) mysteryBoxCallback.onMysteryBox();
            }
            return;
        }
        // Wall buy M14
        dx = wallBuyM14Pos[0] - p.posX; dz = wallBuyM14Pos[2] - p.posZ;
        if (dx*dx + dz*dz < 2*2) {
            if (p.spendPoints(500)) {
                if (wallBuyCallback != null) wallBuyCallback.onBuyM14();
            }
            return;
        }
        // Door clear
        dx = doorClearPos[0] - p.posX; dz = doorClearPos[2] - p.posZ;
        if (dx*dx + dz*dz < 2*2 && !doorUnlocked) {
            if (p.spendPoints(750)) doorUnlocked = true;
        }
    }

    public interface MysteryBoxCallback { void onMysteryBox(); }
    public interface WallBuyCallback { void onBuyM14(); }
    public MysteryBoxCallback mysteryBoxCallback;
    public WallBuyCallback wallBuyCallback;
}
