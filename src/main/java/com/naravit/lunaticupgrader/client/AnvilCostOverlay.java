package com.naravit.lunaticupgrader.client;

import com.naravit.lunaticupgrader.LunaticUpgrader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * แทน "Enchantment Cost: N" ในหน้าจอ Anvil ด้วย "Upgrade Cost: N XP"
 * ใช้ได้ทั้งหน้าจอ Anvil ปกติและ Anvil ที่ mod อื่นเปลี่ยน (เช่น Easy Anvils)
 *
 * วิธีทำ: ก่อนวาดตัวหนังสือ ตั้งค่า cost ฝั่ง client เป็น 0 ชั่วคราว (หน้าจอจะไม่วาดบรรทัด cost เดิม)
 * แล้ววาดบรรทัดของเราแทน จากนั้นคืนค่า cost เดิมทันที
 */
@EventBusSubscriber(modid = LunaticUpgrader.MODID, value = Dist.CLIENT)
public final class AnvilCostOverlay {
    private static final int COST_SLOT = 0;      // AnvilMenu มี data slot เดียว = cost
    private static final int RESULT_SLOT = 2;
    private static final int TEXT_COLOR = 0x80FF20;   // สีเขียวแบบ vanilla
    private static final int BOX_COLOR = 0x4F000000;  // พื้นหลังโปร่งแบบ vanilla

    private static AbstractContainerMenu hiddenMenu = null;
    private static int hiddenCost = 0;

    private AnvilCostOverlay() {}

    /** หลังวาดพื้นหลัง ก่อนวาดตัวหนังสือ: ซ่อน cost เดิม */
    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        restore();
        AbstractContainerMenu menu = event.getContainerScreen().getMenu();
        if (!(menu instanceof AnvilMenu anvil)) return;
        if (upgradeXp(anvil) == null) return;

        int cost = anvil.getCost();
        if (cost <= 0) return;
        anvil.setData(COST_SLOT, 0);
        if (anvil.getCost() != 0) {          // ไม่ใช่ slot ที่คิดไว้ -> ยกเลิก
            anvil.setData(COST_SLOT, cost);
            return;
        }
        hiddenMenu = anvil;
        hiddenCost = cost;
    }

    /** หลังวาดตัวหนังสือ (pose ถูกเลื่อนไปมุมซ้ายบนของหน้าจอ Anvil แล้ว): คืนค่า + วาดบรรทัดของเรา */
    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        boolean wasHidden = hiddenMenu != null && hiddenMenu == screen.getMenu();
        restore();
        if (!wasHidden || !(screen.getMenu() instanceof AnvilMenu anvil)) return;

        Integer xp = upgradeXp(anvil);
        if (xp == null) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("lunaticupgrader.anvil.cost", xp);
        int right = screen.getXSize() - 8;
        int x = right - font.width(text) - 2;
        g.fill(x - 2, 67, right, 79, BOX_COLOR);
        g.drawString(font, text, x, 69, TEXT_COLOR);
    }

    /** กันพลาด: คืนค่าเสมอเมื่อวาดเสร็จ */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        restore();
    }

    private static Integer upgradeXp(AnvilMenu anvil) {
        return anvil.getSlot(RESULT_SLOT).getItem().get(LunaticUpgrader.UPGRADE_XP.get());
    }

    private static void restore() {
        if (hiddenMenu != null) {
            hiddenMenu.setData(COST_SLOT, hiddenCost);
            hiddenMenu = null;
        }
    }
}
