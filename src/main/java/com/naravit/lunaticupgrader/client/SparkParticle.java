package com.naravit.lunaticupgrader.client;

import com.naravit.lunaticupgrader.SparkOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/** ประกายเรืองแสง: สี/ขนาด/อายุ/แรงโน้มถ่วงตาม SparkOptions, ความเร็วตามที่สั่งเป๊ะ */
public class SparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean fade;

    public SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                         SparkOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        int c = options.color();
        this.rCol = ((c >> 16) & 0xFF) / 255f;
        this.gCol = ((c >> 8) & 0xFF) / 255f;
        this.bCol = (c & 0xFF) / 255f;
        this.quadSize = 0.1f * options.scale();
        this.lifetime = Math.max(1, options.lifetime());
        this.gravity = options.gravity();
        this.friction = 0.96f;
        this.hasPhysics = false;
        this.fade = options.fade();
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            if (this.fade) this.alpha = 1.0f - (float) this.age / this.lifetime;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** เรืองแสงเต็มที่ ไม่มืดตามแสงรอบข้าง */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }
}
