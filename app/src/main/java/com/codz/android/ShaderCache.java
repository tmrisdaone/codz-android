package com.codz.android;

import android.content.Context;
import android.opengl.GLES30;

/**
 * Compiles and caches all GLSL ES 3.00 shaders used by the game.
 * Programs are built once on the GL thread (onSurfaceCreated).
 *
 * Three shader programs:
 *  - STATIC_LIT: ambient + 1 dynamic point light (muzzle flash), used by world + zombies.
 *  - FLAT_COLOR: unlit flat color, used for bullets/particles overlays.
 *  - HUD: 2D orthographic UI (points/ammo/round text quads).
 */
public class ShaderCache {
    public int staticLit;
    public int flatColor;
    public int hud;

    // Attribute/uniform locations cached on first use.
    public int sl_aPos, sl_aNor, sl_uMVP, sl_uMV, sl_uLightPos, sl_uLightColor, sl_uBaseColor, sl_uMuzzle;
    public int fc_aPos, fc_uMVP, fc_uColor;
    public int hud_aPos, hud_uMVP, hud_uColor, hud_uTex;

    private final Context context;

    public ShaderCache(Context ctx) {
        context = ctx;
        compile();
    }

    private void compile() {
        staticLit = program(vertStaticLit(), fragStaticLit());
        sl_aPos = GLES30.glGetAttribLocation(staticLit, "aPos");
        sl_aNor = GLES30.glGetAttribLocation(staticLit, "aNor");
        sl_uMVP = GLES30.glGetUniformLocation(staticLit, "uMVP");
        sl_uMV = GLES30.glGetUniformLocation(staticLit, "uMV");
        sl_uLightPos = GLES30.glGetUniformLocation(staticLit, "uLightPos");
        sl_uLightColor = GLES30.glGetUniformLocation(staticLit, "uLightColor");
        sl_uBaseColor = GLES30.glGetUniformLocation(staticLit, "uBaseColor");
        sl_uMuzzle = GLES30.glGetUniformLocation(staticLit, "uMuzzle");

        flatColor = program(vertFlat(), fragFlat());
        fc_aPos = GLES30.glGetAttribLocation(flatColor, "aPos");
        fc_uMVP = GLES30.glGetUniformLocation(flatColor, "uMVP");
        fc_uColor = GLES30.glGetUniformLocation(flatColor, "uColor");

        hud = program(vertHud(), fragHud());
        hud_aPos = GLES30.glGetAttribLocation(hud, "aPos");
        hud_uMVP = GLES30.glGetUniformLocation(hud, "uMVP");
        hud_uColor = GLES30.glGetUniformLocation(hud, "uColor");
        hud_uTex = GLES30.glGetUniformLocation(hud, "uTex");
    }

    private int program(String v, String f) {
        int vs = compile(GLES30.GL_VERTEX_SHADER, v);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, f);
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);
        int[] link = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, link, 0);
        if (link[0] != GLES30.GL_TRUE) {
            throw new RuntimeException("Link failed: " + GLES30.glGetProgramInfoLog(p));
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] status = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] != GLES30.GL_TRUE) {
            throw new RuntimeException("Compile failed: " + GLES30.glGetShaderInfoLog(s));
        }
        return s;
    }

    private String vertStaticLit() {
        return "#version 300 es\n" +
                "layout(location=0) in vec3 aPos;\n" +
                "layout(location=1) in vec3 aNor;\n" +
                "uniform mat4 uMVP;\n" +
                "uniform mat4 uMV;\n" +
                "out vec3 vPos;\n" +
                "out vec3 vNor;\n" +
                "void main(){\n" +
                "  vPos = (uMV * vec4(aPos,1.0)).xyz;\n" +
                "  vNor = mat3(uMV) * aNor;\n" +
                "  gl_Position = uMVP * vec4(aPos,1.0);\n" +
                "}\n";
    }

    private String fragStaticLit() {
        return "#version 300 es\n" +
                "precision mediump float;\n" +
                "in vec3 vPos;\n" +
                "in vec3 vNor;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec4 uBaseColor;\n" +
                "uniform float uMuzzle;\n" +
                "out vec4 frag;\n" +
                "void main(){\n" +
                "  vec3 N = normalize(vNor);\n" +
                "  vec3 ambient = vec3(0.10,0.09,0.13) * uBaseColor.rgb;\n" +
                "  vec3 L = normalize(uLightPos - vPos);\n" +
                "  float diff = max(dot(N,L),0.0);\n" +
                "  float dist = length(uLightPos - vPos);\n" +
                "  float atten = 1.0 / (1.0 + 0.6*dist + 0.07*dist*dist);\n" +
                "  vec3 lit = ambient + uLightColor * diff * atten * (0.4 + 0.6*uMuzzle);\n" +
                "  frag = vec4(lit, uBaseColor.a);\n" +
                "}\n";
    }

    private String vertFlat() {
        return "#version 300 es\n" +
                "layout(location=0) in vec3 aPos;\n" +
                "uniform mat4 uMVP;\n" +
                "void main(){ gl_Position = uMVP * vec4(aPos,1.0); }\n";
    }

    private String fragFlat() {
        return "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "out vec4 frag;\n" +
                "void main(){ frag = uColor; }\n";
    }

    private String vertHud() {
        return "#version 300 es\n" +
                "layout(location=0) in vec2 aPos;\n" +
                "uniform mat4 uMVP;\n" +
                "void main(){ gl_Position = uMVP * vec4(aPos,0.0,1.0); }\n";
    }

    private String fragHud() {
        return "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "out vec4 frag;\n" +
                "void main(){ frag = uColor; }\n";
    }

    public void useStaticLit() { GLES30.glUseProgram(staticLit); }
    public void useFlat() { GLES30.glUseProgram(flatColor); }
    public void useHud() { GLES30.glUseProgram(hud); }

    /** Drives the single dynamic point light slot. Pass muzzle world-space position. */
    public void setLight(float lx, float ly, float lz, float r, float g, float b, float muzzle) {
        GLES30.glUniform3f(sl_uLightPos, lx, ly, lz);
        GLES30.glUniform3f(sl_uLightColor, r, g, b);
        GLES30.glUniform1f(sl_uMuzzle, muzzle);
    }

    public void drawBullet(float[] vp, float x, float y, float z) {
        // Simple flat billboardish cube handled in MeshCache.drawBulletSphere.
        // Here just bind flat; MeshCache owns vertex data.
    }
}
