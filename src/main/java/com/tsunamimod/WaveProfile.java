package com.tsunamimod;

/** Perfil (corte lateral) da onda, com crista curvada. z = frente da onda (+), y = altura. Valores normalizados. */
public final class WaveProfile {
    // Caminho: base de tras -> topo -> ponta da crista -> embaixo da crista -> face -> base da frente.
    private static final float[][] CTRL = {
            {-1.20f, 0.00f}, {-0.90f, 0.12f}, {-0.55f, 0.45f}, {-0.25f, 0.82f}, {0.00f, 1.00f},
            {0.14f, 0.99f}, {0.21f, 0.88f}, {0.12f, 0.76f}, {-0.10f, 0.66f}, {-0.17f, 0.44f},
            {-0.10f, 0.20f}, {0.00f, 0.00f}
    };
    private static final int PER_SEG = 6;

    public static final float[] Z;
    public static final float[] Y;
    /** Comprimento acumulado da curva (para o mapeamento da textura). */
    public static final float[] LEN;

    static {
        int n = (CTRL.length - 1) * PER_SEG + 1;
        Z = new float[n];
        Y = new float[n];
        LEN = new float[n];
        int k = 0;
        for (int i = 0; i < CTRL.length - 1; i++) {
            float[] p0 = CTRL[Math.max(i - 1, 0)];
            float[] p1 = CTRL[i];
            float[] p2 = CTRL[i + 1];
            float[] p3 = CTRL[Math.min(i + 2, CTRL.length - 1)];
            for (int s = 0; s < PER_SEG; s++) {
                float t = s / (float) PER_SEG;
                Z[k] = cr(p0[0], p1[0], p2[0], p3[0], t);
                Y[k] = cr(p0[1], p1[1], p2[1], p3[1], t);
                k++;
            }
        }
        Z[k] = CTRL[CTRL.length - 1][0];
        Y[k] = CTRL[CTRL.length - 1][1];
        for (int i = 1; i < n; i++) {
            float dz = Z[i] - Z[i - 1];
            float dy = Y[i] - Y[i - 1];
            LEN[i] = LEN[i - 1] + (float) Math.sqrt(dz * dz + dy * dy);
        }
    }

    private static float cr(float a, float b, float c, float d, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }

    // Envelope do topo (usado para saber se uma entidade esta dentro da onda).
    private static final float[] TZ = {-1.2f, -0.9f, -0.55f, -0.25f, 0.0f, 0.14f, 0.21f};
    private static final float[] TY = {0.0f, 0.12f, 0.45f, 0.82f, 1.0f, 0.99f, 0.88f};

    public static double topN(double zn) {
        if (zn <= TZ[0] || zn >= TZ[TZ.length - 1]) return 0;
        for (int i = 0; i < TZ.length - 1; i++) {
            if (zn <= TZ[i + 1]) {
                double t = (zn - TZ[i]) / (TZ[i + 1] - TZ[i]);
                return TY[i] + (TY[i + 1] - TY[i]) * t;
            }
        }
        return 0;
    }

    /** Fator de altura nas bordas laterais (0 nas pontas, 1 no centro). */
    public static double edge(double lf) {
        double a = Math.min(Math.abs(lf), 1.0);
        return Math.max(0.03, 1.0 - a * a * a);
    }

    private WaveProfile() {}
}
