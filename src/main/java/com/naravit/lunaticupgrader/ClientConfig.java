package com.naravit.lunaticupgrader;

import net.neoforged.neoforge.common.ModConfigSpec;

/** ตั้งค่าฝั่งเครื่องผู้เล่น: config/lunaticupgrader-client.toml */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.BooleanValue SCREEN_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("effects");
        PARTICLES = b.comment("Show the Star seal particles when upgrading at an anvil")
                .define("particles", true);
        SCREEN_EFFECTS = b.comment("Screen flash and camera shake when YOU unseal a +10 item")
                .define("screenEffects", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean particles() {
        return SPEC.isLoaded() ? PARTICLES.get() : true;
    }

    public static boolean screenEffects() {
        return SPEC.isLoaded() ? SCREEN_EFFECTS.get() : true;
    }

    private ClientConfig() {}
}
