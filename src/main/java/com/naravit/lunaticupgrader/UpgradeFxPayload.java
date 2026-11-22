package com.naravit.lunaticupgrader;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * server -> client: เล่นเอฟเฟกต์ Star seal ที่ Anvil
 *
 * @param pos    ตำแหน่ง Anvil
 * @param packed ชนิด (บิต 0-3: 0 สำเร็จ / 1 ปลดผนึก +10 / 2 พลาด) | ขั้น (บิต 4+)
 * @param actor  entity id ของคนที่ตี (ใช้เปิดเอฟเฟกต์บนจอเฉพาะคนนั้น)
 * @param c0     สีหลัก
 * @param c1     สีรอง
 * @param c2     สีที่สาม
 */
public record UpgradeFxPayload(BlockPos pos, int packed, int actor, int c0, int c1, int c2) implements CustomPacketPayload {
    public static final Type<UpgradeFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LunaticUpgrader.MODID, "upgrade_fx"));

    public static final StreamCodec<ByteBuf, UpgradeFxPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpgradeFxPayload::pos,
            ByteBufCodecs.VAR_INT, UpgradeFxPayload::packed,
            ByteBufCodecs.VAR_INT, UpgradeFxPayload::actor,
            ByteBufCodecs.INT, UpgradeFxPayload::c0,
            ByteBufCodecs.INT, UpgradeFxPayload::c1,
            ByteBufCodecs.INT, UpgradeFxPayload::c2,
            UpgradeFxPayload::new);

    public int kind() {
        return packed & 0xF;
    }

    public int level() {
        return packed >> 4;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** ลงทะเบียน (mod bus) — handler เรียกคลาสฝั่ง client ข้างใน lambda เท่านั้น จึงไม่โหลดบน dedicated server */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.naravit.lunaticupgrader.client.ClientFx.start(payload)));
    }
}
