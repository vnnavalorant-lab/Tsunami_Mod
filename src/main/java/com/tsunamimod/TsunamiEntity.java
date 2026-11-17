package com.tsunamimod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;

/** O tsunami: uma entidade invisivel por si so; o modelo 3D liso e desenhado pelo TsunamiRenderer. */
public class TsunamiEntity extends Entity {
    private static final TrackedData<Integer> WIDTH = DataTracker.registerData(TsunamiEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HEIGHT = DataTracker.registerData(TsunamiEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DIR = DataTracker.registerData(TsunamiEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Blocos por tick. */
    public static final double SPEED = 0.4;

    public int ticks = 0;
    private int maxTicks = 600;

    public TsunamiEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setup(int width, int height, Direction dir, int distance) {
        dataTracker.set(WIDTH, width);
        dataTracker.set(HEIGHT, height);
        dataTracker.set(DIR, dir.getHorizontal());
        this.maxTicks = (int) (distance / SPEED);
        this.setVelocity(dir.getOffsetX() * SPEED, 0, dir.getOffsetZ() * SPEED);
    }

    public int getWaveWidth() { return dataTracker.get(WIDTH); }
    public int getWaveHeight() { return dataTracker.get(HEIGHT); }
    public Direction getWaveDir() { return Direction.fromHorizontal(dataTracker.get(DIR)); }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(WIDTH, 64);
        dataTracker.startTracking(HEIGHT, 28);
        dataTracker.startTracking(DIR, 0);
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        Vec3d v = getVelocity();
        setPosition(getX() + v.x, getY(), getZ() + v.z);

        if (!getWorld().isClient) {
            if (ticks > maxTicks) {
                discard();
                return;
            }
            affectEntities();
        }
    }

    private void affectEntities() {
        Direction dir = getWaveDir();
        Direction lat = dir.rotateYClockwise();
        int half = getWaveWidth() / 2;
        double t = getWaveHeight();
        double ax = getX() + lat.getOffsetX() * half + dir.getOffsetX() * 0.3 * t;
        double az = getZ() + lat.getOffsetZ() * half + dir.getOffsetZ() * 0.3 * t;
        double bx = getX() - lat.getOffsetX() * half - dir.getOffsetX() * 1.3 * t;
        double bz = getZ() - lat.getOffsetZ() * half - dir.getOffsetZ() * 1.3 * t;
        Box box = new Box(ax, getWorld().getBottomY(), az, bx, getWorld().getTopY(), bz);

        List<Entity> list = getWorld().getOtherEntities(this, box, e -> !e.isSpectator() && !(e instanceof TsunamiEntity));
        for (Entity e : list) {
            if (!inside(e, dir, lat, half, t)) continue;
            Vec3d cur = e.getVelocity();
            e.setVelocity(dir.getOffsetX() * 0.55, Math.max(cur.y, 0.12), dir.getOffsetZ() * 0.55);
            e.velocityModified = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity le && ticks % 10 == 0) {
                le.damage(getWorld().getDamageSources().drown(), 2.0F);
            }
        }
    }

    private boolean inside(Entity e, Direction dir, Direction lat, int half, double t) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        double lx = dx * lat.getOffsetX() + dz * lat.getOffsetZ();
        double lz = dx * dir.getOffsetX() + dz * dir.getOffsetZ();
        if (Math.abs(lx) > half) return false;
        double top = WaveProfile.topN(lz / t);
        if (top <= 0) return false;
        double edge = WaveProfile.edge(lx / Math.max(half, 1));
        int ground = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                MathHelper.floor(e.getX()), MathHelper.floor(e.getZ()));
        double rel = e.getY() - ground;
        return rel >= -2 && rel <= t * top * edge;
    }

    @Override public boolean shouldRender(double distance) { return true; }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
