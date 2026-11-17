package com.tsunamimod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class TsunamiMod implements ModInitializer {
    public static final String MOD_ID = "tsunami";
    public static EntityType<TsunamiEntity> TSUNAMI_ENTITY;

    @Override
    public void onInitialize() {
        TSUNAMI_ENTITY = Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, "tsunami"),
                FabricEntityTypeBuilder.<TsunamiEntity>create(SpawnGroup.MISC, TsunamiEntity::new)
                        .dimensions(EntityDimensions.fixed(3.0F, 3.0F))
                        .trackRangeChunks(32)
                        .trackedUpdateRate(1)
                        .forceTrackedVelocityUpdates(true)
                        .fireImmune()
                        .build());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("tsunami")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("start")
                                .executes(c -> start(c, 64, 28, 200))
                                .then(CommandManager.argument("largura", IntegerArgumentType.integer(8, 300))
                                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "largura"), 28, 200))
                                        .then(CommandManager.argument("altura", IntegerArgumentType.integer(4, 100))
                                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "largura"),
                                                        IntegerArgumentType.getInteger(c, "altura"), 200))
                                                .then(CommandManager.argument("distancia", IntegerArgumentType.integer(10, 1000))
                                                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "largura"),
                                                                IntegerArgumentType.getInteger(c, "altura"),
                                                                IntegerArgumentType.getInteger(c, "distancia")))))))
                        .then(CommandManager.literal("stop").executes(c -> {
                            int n = 0;
                            for (ServerWorld w : c.getSource().getServer().getWorlds()) {
                                for (TsunamiEntity t : w.getEntitiesByType(TSUNAMI_ENTITY, e -> true)) {
                                    t.discard();
                                    n++;
                                }
                            }
                            final int count = n;
                            c.getSource().sendFeedback(() -> Text.literal("Tsunamis removidos: " + count), true);
                            return count;
                        }))));
    }

    private static int start(CommandContext<ServerCommandSource> ctx, int width, int height, int distance) {
        try {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            ServerWorld world = p.getServerWorld();
            Direction facing = p.getHorizontalFacing();
            // A onda nasce 80 blocos a sua frente e vem na sua direcao.
            BlockPos o = p.getBlockPos().offset(facing, 80);
            TsunamiEntity t = new TsunamiEntity(TSUNAMI_ENTITY, world);
            t.setup(width, height, facing.getOpposite(), distance);
            t.setPosition(o.getX() + 0.5, p.getY(), o.getZ() + 0.5);
            world.spawnEntity(t);
            ctx.getSource().sendFeedback(() -> Text.literal("Tsunami chegando! Largura " + width
                    + ", altura " + height + ", distancia " + distance), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Use este comando como jogador."));
            return 0;
        }
    }
}
