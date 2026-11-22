package com.naravit.lunaticupgrader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * particle ประกายของ mod: กำหนดสี ขนาด อายุ แรงโน้มถ่วง และการจางได้ทุกตัว
 * (vanilla dust ปรับความเร็ว/อายุไม่ได้ เลยทำตัวนี้เอง ใช้ texture glitter ของ end rod)
 */
public record SparkOptions(int color, float scale, int lifetime, float gravity, boolean fade) implements ParticleOptions {
    public static final MapCodec<SparkOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("color").forGetter(SparkOptions::color),
            Codec.FLOAT.fieldOf("scale").forGetter(SparkOptions::scale),
            Codec.INT.fieldOf("lifetime").forGetter(SparkOptions::lifetime),
            Codec.FLOAT.fieldOf("gravity").forGetter(SparkOptions::gravity),
            Codec.BOOL.fieldOf("fade").forGetter(SparkOptions::fade)
    ).apply(i, SparkOptions::new));

    public static final StreamCodec<ByteBuf, SparkOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SparkOptions::color,
            ByteBufCodecs.FLOAT, SparkOptions::scale,
            ByteBufCodecs.VAR_INT, SparkOptions::lifetime,
            ByteBufCodecs.FLOAT, SparkOptions::gravity,
            ByteBufCodecs.BOOL, SparkOptions::fade,
            SparkOptions::new);

    @Override
    public ParticleType<?> getType() {
        return LunaticUpgrader.SPARK.get();
    }
}
