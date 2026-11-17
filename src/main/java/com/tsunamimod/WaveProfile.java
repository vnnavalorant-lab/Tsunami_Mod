package com.tsunamimod;

/**
 * Perfil (corte lateral) da onda, copiado do formato da referencia:
 * plato largo no topo, crista saliente (lip) e uma face frontal concava (cavidade) embaixo dela.
 * z = frente da onda (+), y = altura. Valores normalizados (y = 1 e a altura da onda).
 */
public final class WaveProfile {
    /** Profundidade da onda = altura * THICK. */
    public static final float THICK = 0.8f;

    // Caminho: base de tras -> plato -> ponta da crista -> embaixo da crista -> face concava -> base da frente.
    private static final float[][] CTRL = {
            {-2.30f, 0.00f}, {-2.00f, 0.10f}, {-1.50f, 0.42f}, {-1.00f, 0.80f}, {-0.55f, 0.98f},
            {-0.15f, 1.04f}, {0.20f, 1.02f}, {0.36f, 0.94f}, {0.30f, 0.86f}, {0.05f, 0.82f},
            {-0.25f, 0.76f}, {-0.48f, 0.60f}, {-0.52f, 0.38f}, {-0.40f, 0.17f}, {-0.18f, 0.05f},
            {0.05f, 0.00f}
    };
    private static final int PER_SEG = 6;

    public static final float[] Z;
    public static final float[] Y;
    /** Comprimento acumulado da curva (mapeamento da textura). */
    public static final float[] LEN;
    /** Brilho (0..1) de cada ponto: topo claro, parte de baixo da crista escura. */
    public static final float[] SHADE;

    static {
        int n = (CTRL.length - 1) * PER_SEG + 1;
        Z = new float[n];
        Y = new float[n];
        LEN = new float[n];
        SHADE = new float[n];
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
        // sombreamento assado: normal para fora = (-dy, dz); luz vem de cima e um pouco da frente
        for (int i = 0; i < n; i++) {
            int a = Math.max(i - 1, 0), b = Math.min(i + 1, n - 1);
            double dz = (Z[b] - Z[a]) * THICK;
            double dy = (Y[b] - Y[a]);
            double len = Math.sqrt(dz * dz + dy * dy);
            if (len < 1e-6) { SHADE[i] = 0.8f; continue; }
            double ny = dz / len;   // componente vertical da normal
            double nz = -dy / len;  // componente para frente
            double dot = ny * 0.9 + nz * 0.4;
            SHADE[i] = (float) (0.50 + 0.50 * Math.max(0.0, Math.min(1.0, dot)));
        }
    }

    private static float cr(float a, float b, float c, float d, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }

    /** Ponto (zn, yn) esta dentro do corpo de agua (ray casting no poligono do perfil)? */
    public static boolean contains(double zn, double yn) {
        if (yn < 0) return false;
        boolean in = false;
        int n = Z.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double zi = Z[i], yi = Y[i], zj = Z[j], yj = Y[j];
            if ((yi > yn) != (yj > yn) && zn < (zj - zi) * (yn - yi) / (yj - yi) + zi) in = !in;
        }
        return in;
    }

    /** Fator de altura nas bordas laterais: 1 no meio, cai suavemente ate quase 0 nas pontas. */
    public static double edge(double lf) {
        double a = Math.min(Math.abs(lf), 1.0);
        double t = Math.max(0.0, Math.min(1.0, (a - 0.45) / 0.55));
        double s = t * t * (3 - 2 * t);
        return Math.max(0.03, 1.0 - s);
    }

    private WaveProfile() {}
}
