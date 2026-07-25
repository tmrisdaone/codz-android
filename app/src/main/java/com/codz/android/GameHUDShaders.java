package com.codz.android;

/**
 * Statically-held ShaderCache reference so the HUD shader state can be accessed
 * without re-passing it through every draw call. Populated by GameRenderer at
 * init via set().
 */
public final class GameHUDShaders {
    private static ShaderCache shaders;
    public static void set(ShaderCache sh) { shaders = sh; }
    public static ShaderCache get() { return shaders; }
}
