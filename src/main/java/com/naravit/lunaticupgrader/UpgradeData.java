package com.naravit.lunaticupgrader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * @param level    ขั้นตีบวก 0-10
 * @param baseName ชื่อที่ผู้เล่นตั้งเอง (ว่าง = ใช้ชื่อปกติของไอเทม แปลตามภาษาของผู้เล่น)
 */
public record UpgradeData(int level, String baseName) {
    public static final UpgradeData NONE = new UpgradeData(0, "");

    public static final Codec<UpgradeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("level").forGetter(UpgradeData::level),
            Codec.STRING.optionalFieldOf("base_name", "").forGetter(UpgradeData::baseName)
    ).apply(inst, UpgradeData::new));

    public static final StreamCodec<ByteBuf, UpgradeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpgradeData::level,
            ByteBufCodecs.STRING_UTF8, UpgradeData::baseName,
            UpgradeData::new);
}
