package com.naravit.lunaticupgrader;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LunaticUpgrader.MODID)
public class LunaticUpgrader {
    public static final String MODID = "lunaticupgrader";

    /** ขั้นสูงสุดของการตีบวก */
    public static final int MAX_LEVEL = 10;

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** เก็บข้อมูลขั้นตีบวกไว้ใน ItemStack (บันทึกลงเซฟ + sync ไป client) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UpgradeData>> UPGRADE =
            COMPONENTS.registerComponentType("upgrade", builder -> builder
                    .persistent(UpgradeData.CODEC)
                    .networkSynchronized(UpgradeData.STREAM_CODEC));

    /** แต้ม XP ของการตีบวก ใส่ไว้ชั่วคราวในช่องผลลัพธ์ ให้หน้าจอ Anvil แสดง "Upgrade Cost: N XP" (ไม่บันทึกลงเซฟ) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> UPGRADE_XP =
            COMPONENTS.registerComponentType("upgrade_xp", builder -> builder
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** จำนวนเลเวลที่ Anvil หัก ใส่ไว้ชั่วคราวในช่องผลลัพธ์ เพื่อคืนให้ตอนหยิบ (ไม่บันทึกลงเซฟ) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> UPGRADE_COST =
            COMPONENTS.registerComponentType("upgrade_cost", builder -> builder
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- particle ประกายของ Star seal ----
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<SparkOptions>> SPARK =
            PARTICLES.register("spark", () -> new ParticleType<SparkOptions>(false) {
                @Override
                public MapCodec<SparkOptions> codec() {
                    return SparkOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, SparkOptions> streamCodec() {
                    return SparkOptions.STREAM_CODEC;
                }
            });

    // ---- เสียง Lunar choir ----
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_SUCCESS = sound("upgrade_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_UNSEAL = sound("upgrade_unseal");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_FAIL = sound("upgrade_fail");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    public LunaticUpgrader(IEventBus modBus, ModContainer container) {
        COMPONENTS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(UpgradeFxPayload::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(UpgradeFx.class);
        NeoForge.EVENT_BUS.register(UpgradeEvents.class);
        NeoForge.EVENT_BUS.register(UpgradeCommand.class);
    }
}
