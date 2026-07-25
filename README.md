# Call of Duty Zombies Clone for Android

A complete, playable COD Zombies clone written in pure Java using native OpenGL ES 3.0 (no external game engine dependencies).

## Architecture

### Core Modules
| File | Responsibility |
|------|----------------|
| `MainActivity.java` | Entry point, GLSurfaceView holder |
| `GameView.java` | GLSurfaceView with touch delegation |
| `GameRenderer.java` | Main loop (update/render), system wiring |
| `PlayerController.java` | FPS camera, movement, health/regen, points |
| `WeaponSystem.java` | Fire/ADS/reload/swap, hitscan + projectile logic |
| `Weapon.java` / `WeaponFactory.java` | Weapon data & presets (M1911, M14, Ray Gun) |
| `ZombieManager.java` / `Zombie.java` | Wave spawning, AI state machine, hit detection |
| `BulletPool.java` / `Bullet.java` | Object-pooled projectiles (Ray Gun) |
| `ParticleSystem.java` | GPU-updated particles (muzzle flash, hits, death) |
| `WorldMap.java` | Static geometry, barricades, doors, wall-buys, spawn nodes |
| `MeshCache.java` | All VAOs/VBOs (floor, walls, zombies, bullets, boxes, boards) |
| `ShaderCache.java` | 3 GLSL ES 3.0 programs (lit, flat, HUD) |
| `GameHUD.java` | Virtual joysticks, buttons, ammo/points/health/round text |

### Gameplay Features Implemented
- **Wave formula**: `floor(0.24*N² + 12*N + 6)` zombies/round
- **Health scaling**: 150 HP base, +10%/round after round 9
- **Player**: 100 HP, regen after 5s no-damage (18 HP/s)
- **Points**: 10/hit, 60/kill, 100/headshot, 10/board repair
- **Weapons**: M1911 (starter), M14 (wall-buy 500pts), Ray Gun (mystery box 950pts)
- **ADS**: Smooth FOV lerp 75° → 45°
- **Barricades**: 6 boards per window, rebuild for 10 pts
- **Door clear**: 750 pts unlocks new area
- **Mystery box**: Grants Ray Gun

### Mobile Controls (Dual Joystick + Buttons)
- **Left half**: virtual joystick (move/strafe)
- **Right half**: drag-to-look (yaw/pitch)
- **Buttons**: Fire (auto/semi), ADS, Reload, Swap, Interact/Buy

### Performance Guarantees
- **Zero per-frame allocations**: all arrays, matrices, pools pre-allocated
- **Object pools**: 32 zombies, 64 bullets, 256 particles
- **Single dynamic VBO update** per frame (particles only)
- **Vertex format**: pos3+nor3 (24 bytes/vert), all meshes baked at init
- **Target**: 60 FPS on mid-range Android (Mali G71 / Adreno 618+)

## Build
Requires Android SDK 34, Gradle 8.5, JDK 17.

```bash
./gradlew assembleDebug
```

APK outputs to `app/build/outputs/apk/debug/app-debug.apk`.

## Run
Install on device/emulator with OpenGL ES 3.0 support.

## Controls Reference
| Gesture | Action |
|---------|--------|
| Left drag | Move/strafe |
| Right drag | Look (yaw + pitch clamp ±89°) |
| Fire button (hold) | Auto-fire / semi tap |
| ADS button | Aim down sights (FOV 45°) |
| Reload | Manual reload |
| Swap | Cycle weapons |
| Interact (near barricade/door/box) | Rebuild / buy / open |

## Extensibility
- Add weapons: extend `WeaponFactory` + add to `inventory` array
- Add maps: extend `WorldMap` walls/spawns/barricades/doors
- Add zombie types: subclass `Zombie`, add state in `Zombie.State`
- Add perks/powerups: hook into `PlayerController.addPoints()` / `damage()`

## License
MIT — free to use, modify, distribute.