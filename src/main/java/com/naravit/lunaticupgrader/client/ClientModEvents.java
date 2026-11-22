package com.naravit.lunaticupgrader.client;

import com.naravit.lunaticupgrader.LunaticUpgrader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** mod bus ฝั่ง client */
@EventBusSubscriber(modid = LunaticUpgrader.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(LunaticUpgrader.SPARK.get(), sprites ->
                (options, level, x, y, z, dx, dy, dz) -> new SparkParticle(level, x, y, z, dx, dy, dz, options, sprites));
    }
}
