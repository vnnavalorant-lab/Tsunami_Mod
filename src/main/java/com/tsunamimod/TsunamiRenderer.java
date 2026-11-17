package com.tsunamimod;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Desenha a onda como uma malha 3D lisa (superficie curva) usando a textura de agua do jogo. */
public class TsunamiRenderer extends EntityRenderer<TsunamiEntity> {
    private static final Identifier TEX = new Identifier("minecraft", "textures/block/water_still.png");

    public TsunamiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(TsunamiEntity entity) {
        return TEX;
    }

    @Override
    public boolean shouldRender(TsunamiEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void render(TsunamiEntity e, float yaw, float td, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        Direction dir = e.getWaveDir();
        Direction lat = dir.rotateYClockwise();
        int width = e.getWaveWidth();
        float h = e.getWaveHeight();
        float thick = h * WaveProfile.THICK; // profundidade da onda
        float half = width / 2f;
        int cols = MathHelper.clamp(width / 2, 16, 140);
        int ps = WaveProfile.Z.length;
        World world = e.getWorld();
        Vec3d pos = e.getLerpedPos(td);
        float time = e.ticks + td;

        // altura do chao em cada coluna lateral (a onda acompanha o relevo)
        float[] ground = new float[cols + 1];
        for (int i = 0; i <= cols; i++) {
            float lx = -half + width * i / (float) cols;
            double wx = pos.x + lat.getOffsetX() * lx - dir.getOffsetX() * 1.0 * thick;
            double wz = pos.z + lat.getOffsetZ() * lx - dir.getOffsetZ() * 1.0 * thick;
            int bx = MathHelper.floor(wx), bz = MathHelper.floor(wz);
            float g = (float) (pos.y);
            if (world.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)) {
                g = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            }
            ground[i] = g - (float) pos.y;
        }
        for (int pass = 0; pass < 6; pass++) { // suaviza
            float[] s = ground.clone();
            for (int i = 1; i < cols; i++) ground[i] = (s[i - 1] + 2 * s[i] + s[i + 1]) / 4f;
        }

        float[][] vx = new float[cols + 1][ps];
        float[][] vy = new float[cols + 1][ps];
        float[][] vz = new float[cols + 1][ps];
        float[] lxs = new float[cols + 1];
        for (int i = 0; i <= cols; i++) {
            float lx = -half + width * i / (float) cols;
            lxs[i] = lx;
            float lf = Math.abs(lx) / Math.max(half, 1f);
            float edge = (float) WaveProfile.edge(lf);
            // topo com ondulacao suave
            float wob = 1f + 0.035f * MathHelper.sin(lx * 0.10f + time * 0.05f)
                    + 0.02f * MathHelper.sin(lx * 0.31f - time * 0.09f);
            float hh = h * edge * wob;
            // a frente da onda e levemente curva (pontas ficam para tras)
            float bow = 0.25f * thick * lf * lf;
            // a crista ondula para frente e para tras ao longo da largura
            float lipWave = (0.10f * MathHelper.sin(lx * 0.07f + time * 0.04f)
                    + 0.04f * MathHelper.sin(lx * 0.19f - time * 0.07f)) * thick;
            for (int j = 0; j < ps; j++) {
                float w = smooth(0.45f, 0.85f, WaveProfile.Y[j]);
                float zz = WaveProfile.Z[j] * thick * (0.55f + 0.45f * edge) - bow + lipWave * w;
                vx[i][j] = lat.getOffsetX() * lx + dir.getOffsetX() * zz;
                vz[i][j] = lat.getOffsetZ() * lx + dir.getOffsetZ() * zz;
                vy[i][j] = ground[i] + WaveProfile.Y[j] * hh - 0.8f;
            }
        }

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        Matrix4f pm = matrices.peek().getPositionMatrix();
        Matrix3f nm = matrices.peek().getNormalMatrix();
        int frame = ((int) (time / 2f)) % 32; // anima a textura (32 frames)
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < ps - 1; j++) {
                quadVertex(vc, pm, nm, vx[i][j], vy[i][j], vz[i][j], lxs[i], j, frame, fullBright, thick);
                quadVertex(vc, pm, nm, vx[i + 1][j], vy[i + 1][j], vz[i + 1][j], lxs[i + 1], j, frame, fullBright, thick);
                quadVertex(vc, pm, nm, vx[i + 1][j + 1], vy[i + 1][j + 1], vz[i + 1][j + 1], lxs[i + 1], j + 1, frame, fullBright, thick);
                quadVertex(vc, pm, nm, vx[i][j + 1], vy[i][j + 1], vz[i][j + 1], lxs[i], j + 1, frame, fullBright, thick);
            }
        }
    }

    private static void quadVertex(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float x, float y, float z,
                                   float lx, int j, int frame, int light, float thick) {
        float u = lx / 6f;
        float v = (WaveProfile.LEN[j] * thick / 6f + frame) / 32f;
        // espuma branca na crista, e sombreamento assado (embaixo da crista fica mais escuro)
        float f = smooth(0.88f, 1.0f, WaveProfile.Y[j]) * 0.8f;
        float sh = WaveProfile.SHADE[j];
        int r = (int) (MathHelper.lerp(f, 63, 225) * sh);
        int g = (int) (MathHelper.lerp(f, 118, 240) * sh);
        int b = (int) (MathHelper.lerp(f, 228, 255) * (0.6f + 0.4f * sh));
        vc.vertex(pm, x, y, z)
                .color(r, g, b, 235)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nm, 0f, 1f, 0f)
                .next();
    }
}
