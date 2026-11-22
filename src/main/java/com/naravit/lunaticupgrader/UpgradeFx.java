package com.naravit.lunaticupgrader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** ฝั่ง server: ส่งเอฟเฟกต์ Star seal + เล่นเสียง Lunar choir ที่ Anvil */
public final class UpgradeFx {
    public static final int SUCCESS = 0;
    public static final int UNSEAL = 1;
    public static final int FAIL = 2;

    /** ช่วงสะสมแรงก่อนปลดผนึก (tick) — ต้องตรงกับ ClientFx และไฟล์เสียง upgrade_unseal (1.8 วินาที) */
    public static final int UNSEAL_DELAY = 36;

    private static final List<Pending> PENDING = new ArrayList<>();

    private UpgradeFx() {}

    /**
     * @param level SUCCESS: ขั้นใหม่ (1-9) / UNSEAL: 10 / FAIL: ขั้นก่อนตี (0-9)
     */
    public static void play(Player player, ItemStack stack, int kind, int level) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        BlockPos pos = findAnvil(player);

        int[] palette = NameStyle.paletteFor(stack);
        int[] colors = switch (kind) {
            case UNSEAL -> new int[]{palette[0], palette[1], palette[2]};
            case SUCCESS -> new int[]{NameStyle.mainColor(level, palette), 0xFFFFFF, 0xC9B3FF};
            default -> new int[]{0xC2262E, 0xFF3D3D, 0x8A8AA0};
        };
        PacketDistributor.sendToPlayersNear(sl, null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 64,
                new UpgradeFxPayload(pos, kind | (level << 4), player.getId(), colors[0], colors[1], colors[2]));

        SoundEvent sound = switch (kind) {
            case UNSEAL -> LunaticUpgrader.SOUND_UNSEAL.get();
            case SUCCESS -> LunaticUpgrader.SOUND_SUCCESS.get();
            default -> LunaticUpgrader.SOUND_FAIL.get();
        };
        sl.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /** หา Anvil ที่ผู้เล่นกำลังใช้: บล็อกที่มองอยู่ ถ้าไม่ใช่ หา Anvil ที่ใกล้ที่สุดในรัศมี 5 บล็อก */
    public static BlockPos findAnvil(Player player) {
        HitResult hit = player.pick(6.0, 1.0f, false);
        if (hit instanceof BlockHitResult bhr && player.level().getBlockState(bhr.getBlockPos()).is(BlockTags.ANVIL)) {
            return bhr.getBlockPos();
        }
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -3, -5), center.offset(5, 3, 5))) {
            if (player.level().getBlockState(p).is(BlockTags.ANVIL)) {
                double d = p.distSqr(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) return best;
        return BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(2.0))).below();
    }

    /** ทำงานหลังจากนี้ n tick (ฝั่ง server) เช่น โชว์ข้อความหลังปลดผนึกเสร็จ */
    public static void later(ServerPlayer player, int ticks, Runnable task) {
        PENDING.add(new Pending(player, ticks, task));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.player.hasDisconnected()) {
                it.remove();
            } else if (--p.ticks <= 0) {
                it.remove();
                p.task.run();
            }
        }
    }

    private static final class Pending {
        final ServerPlayer player;
        int ticks;
        final Runnable task;

        Pending(ServerPlayer player, int ticks, Runnable task) {
            this.player = player;
            this.ticks = ticks;
            this.task = task;
        }
    }
}
