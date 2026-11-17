package com.tsunamimod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class TsunamiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(TsunamiMod.TSUNAMI_ENTITY, TsunamiRenderer::new);
    }
}
