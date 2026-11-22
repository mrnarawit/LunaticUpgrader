package com.naravit.lunaticupgrader.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.naravit.lunaticupgrader.LunaticUpgrader;
import com.naravit.lunaticupgrader.NameStyle;
import com.naravit.lunaticupgrader.UpgradeData;
import com.naravit.lunaticupgrader.UpgradeFx;
import com.naravit.lunaticupgrader.UpgradeFxPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * วงดาว 10 ดวงรอบช่องผลลัพธ์ในหน้าจอ Anvil (ใช้ได้กับ Easy Anvils ด้วย)
 *  - ตอนวางของ: ดาวที่ได้มาแล้วสว่างตามสีขั้น ช่องถัดไปกะพริบ อักษรเวทไหลจาก Template เข้าหาช่องนั้น
 *  - สำเร็จ: ดาวตกพุ่งจากช่องผลลัพธ์ ประกายรวมตัว ดาวดวงใหม่สว่าง + "+N" ลอยขึ้น
 *  - +10: ประกายไหลเข้าช่องสุดท้าย หน้าจอสั่น -> ดาวครบวง ระเบิดแสง กรอบเรืองแสง + "+10"
 *  - พลาด: ประกายฟุ้งเป็นสีเทา ดาวดวงล่าสุดดับ + "-1"
 * เห็นเฉพาะคนที่ใช้ Anvil
 */
@EventBusSubscriber(modid = LunaticUpgrader.MODID, value = Dist.CLIENT)
public final class AnvilUiFx {
    private static final int RX = 17, RY = 12;          // รัศมีวงดาว (พิกเซล GUI)
    private static final float T_IGNITE = 0.85f, T_COMPLETE = 1.8f, T_FIZZLE = 0.5f;
    private static final int GRAY = 0x6E6A7A, WHITE = 0xFFFFFF;
    private static final int[] RUNE = {0xC9B3FF, 0x9D6BFF, 0xE8D9FF};

    // แอนิเมชันที่กำลังเล่น (-1 = ไม่มี)
    private static int kind = -1, level, c0, c1, c2;
    private static long startMs, lastMs;
    private static boolean fired, cometFired;
    private static float glow, slotFlash, shake, spawnAcc, glyphAcc, cometAcc;
    private static int slotFlashColor;
    private static boolean pushed;

    private static final List<Part> PARTS = new ArrayList<>();
    private static final List<Txt> TEXTS = new ArrayList<>();
    private static final Random RND = new Random();

    private AnvilUiFx() {}

    static void start(UpgradeFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof AnvilMenu)) return;
        kind = p.kind();
        level = p.level();
        c0 = p.c0();
        c1 = p.c1();
        c2 = p.c2();
        startMs = Util.getMillis();
        fired = false;
        cometFired = false;
        spawnAcc = 0;
        cometAcc = 0;
    }

    // ------------------------------------------------------------------
    // จอ Anvil สั่น (เลื่อนทั้งหน้าจอ)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        pushed = false;
        if (shake > 0.05f && event.getScreen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof AnvilMenu) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate((RND.nextFloat() * 2 - 1) * shake, (RND.nextFloat() * 2 - 1) * shake, 0);
            pushed = true;
        }
    }

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        if (pushed) {
            event.getGuiGraphics().pose().popPose();
            pushed = false;
        }
    }

    // ------------------------------------------------------------------
    // วาด (พิกัดเทียบกับมุมซ้ายบนของหน้าจอ Anvil)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!(screen.getMenu() instanceof AnvilMenu menu)) return;

        long now = Util.getMillis();
        float dt = lastMs == 0 ? 0 : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        float time = now / 1000f;

        Slot left = menu.getSlot(0), right = menu.getSlot(1), result = menu.getSlot(2);
        int cx = result.x + 8, cy = result.y + 8;
        ItemStack leftStack = left.getItem();
        int cur = leftStack.getOrDefault(LunaticUpgrader.UPGRADE.get(), UpgradeData.NONE).level();
        boolean preview = result.getItem().has(LunaticUpgrader.UPGRADE_XP.get());
        boolean idle = preview || (cur > 0 && right.getItem().getItem() instanceof SmithingTemplateItem);
        int[] palette = leftStack.isEmpty() ? new int[]{c0, c1, c2} : NameStyle.paletteFor(leftStack);

        float e = kind < 0 ? 0 : (now - startMs) / 1000f;
        if (kind >= 0 && e > endTime(kind)) kind = -1;

        // อัปเดตค่าที่จางลงเอง
        glow = Math.max(0, glow - dt * 0.45f);
        slotFlash = Math.max(0, slotFlash - dt * 2f);
        if (kind != UpgradeFx.UNSEAL || e > T_COMPLETE) shake = Math.max(0, shake - dt * 4f);

        if (kind < 0 && !idle && PARTS.isEmpty() && TEXTS.isEmpty() && glow <= 0 && slotFlash <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        // กรอบเรืองแสงหลังครบ +10
        if (glow > 0) {
            int w = screen.getXSize(), h = screen.getYSize();
            int[] pal = {c0, c1, c2};
            for (int k = 0; k < 3; k++) {
                int a = (int) (255 * glow * (0.6f - k * 0.18f));
                int col = argb(a, pal[((int) (time * 6) + k) % 3]);
                int o = 2 + k * 2;
                g.fill(-o, -o, w + o, -o + 1, col);
                g.fill(-o, h + o - 1, w + o, h + o, col);
                g.fill(-o, -o, -o + 1, h + o, col);
                g.fill(w + o - 1, -o, w + o, h + o, col);
            }
        }

        // แสงวาบที่ช่องผลลัพธ์
        if (slotFlash > 0) g.fill(result.x, result.y, result.x + 16, result.y + 16, argb((int) (slotFlash * 255), slotFlashColor));

        // ---------- สถานะวงดาว ----------
        int lit = cur;                 // จำนวนดาวที่สว่าง
        int charge = -1;               // ช่องที่กำลังก่อตัว
        float chargeAmt = 0;
        int dimIdx = -1;               // ดาวที่กำลังดับ
        float dimAmt = 0;
        boolean maxedGlow = cur >= 10;

        if (kind == UpgradeFx.SUCCESS) {
            int idx = Math.max(0, Math.min(8, level - 1));
            if (e < T_IGNITE) {
                lit = idx;
                charge = idx;
                chargeAmt = e / T_IGNITE;
                spawnAcc += dt;
                while (spawnAcc > 0.04f) {
                    spawnAcc -= 0.04f;
                    gather(starX(cx, idx), starY(cy, idx), 9, new int[]{NameStyle.levelColor(idx + 1), WHITE});
                }
            } else {
                lit = idx + 1;
                if (!fired) {
                    fired = true;
                    int col = NameStyle.levelColor(idx + 1);
                    burst(starX(cx, idx), starY(cy, idx), 18, 60, new int[]{col, WHITE}, 0.7f, 0);
                    slotFlash = 1;
                    slotFlashColor = col;
                    TEXTS.add(new Txt("+" + level, new int[]{col}, cx, cy - 14, false));
                }
            }
            if (!cometFired) {
                cometFired = true;
                PARTS.add(Part.comet(cx, cy, starX(cx, idx), starY(cy, idx), NameStyle.levelColor(idx + 1), 0.5f));
            }
        } else if (kind == UpgradeFx.UNSEAL) {
            int[] pal = {c0, c1, c2, WHITE};
            if (e < T_COMPLETE) {
                lit = 9;
                charge = 9;
                chargeAmt = e / T_COMPLETE;
                shake = chargeAmt * 1.2f;
                spawnAcc += dt;
                while (spawnAcc > 0.05f) {
                    spawnAcc -= 0.05f;
                    for (int i = 0; i < 1 + (int) (chargeAmt * 4); i++) gather(starX(cx, 9), starY(cy, 9), 14, pal);
                }
                cometAcc += dt;
                if (cometAcc > 0.3f) {
                    cometAcc = 0;
                    int j = RND.nextInt(9);
                    PARTS.add(Part.comet(starX(cx, j), starY(cy, j), starX(cx, 9), starY(cy, 9), NameStyle.levelColor(j + 1), 0.4f));
                }
            } else {
                lit = 10;
                maxedGlow = true;
                palette = new int[]{c0, c1, c2};
                if (!fired) {
                    fired = true;
                    shake = 3f;
                    for (int i = 0; i < 10; i++) burst(starX(cx, i), starY(cy, i), 14, 140, pal, 1.2f, 20);
                    burst(cx, cy, 60, 220, pal, 1.6f, 10);
                    slotFlash = 1;
                    slotFlashColor = WHITE;
                    glow = 1;
                    TEXTS.add(new Txt("+10", pal, cx, cy - 16, true));
                }
            }
        } else if (kind == UpgradeFx.FAIL) {
            int prev = Math.max(0, Math.min(9, level));
            lit = prev;
            int target = Math.min(9, prev);
            if (e < T_FIZZLE) {
                charge = target;
                chargeAmt = Math.min(0.7f, e / T_FIZZLE * 0.7f);
                spawnAcc += dt;
                while (spawnAcc > 0.04f) {
                    spawnAcc -= 0.04f;
                    gather(starX(cx, target), starY(cy, target), 9, new int[]{NameStyle.levelColor(target + 1), WHITE});
                }
            } else {
                if (prev > 0) {
                    dimIdx = prev - 1;
                    dimAmt = Math.min(1, (e - T_FIZZLE) / 0.6f);
                    if (dimAmt >= 1) lit = prev - 1;
                }
                if (!fired) {
                    fired = true;
                    burst(starX(cx, target), starY(cy, target), 16, 50, new int[]{0x8A8AA0, 0x5A5A6A}, 0.7f, 60);
                    shake = 1f;
                    slotFlash = 0.5f;
                    slotFlashColor = 0x8A2E3A;
                    if (prev > 0) {
                        for (int i = 0; i < 5; i++) {
                            Part s = new Part(starX(cx, prev - 1), starY(cy, prev - 1), (RND.nextFloat() - 0.5f) * 8,
                                    -8 - RND.nextFloat() * 8, i % 2 == 0 ? 0x6E6E6E : 0x5A5A5A, 0.9f);
                            s.size = 2;
                            PARTS.add(s);
                        }
                    }
                    TEXTS.add(new Txt(prev > 0 ? "-1" : "x", new int[]{0xFF3D3D}, cx, cy - 14, false));
                }
            }
        } else if (preview && cur < 10) {
            // อักษรเวทไหลจาก Template เข้าหาช่องดาวถัดไป
            glyphAcc += dt;
            while (glyphAcc > 0.06f) {
                glyphAcc -= 0.06f;
                if (RND.nextFloat() < 0.6f) {
                    Part p = Part.conv(right.x + 8 + RND.nextInt(5) - 2, right.y + 8 + RND.nextInt(5) - 2,
                            starX(cx, cur), starY(cy, cur), RUNE[RND.nextInt(3)], 0.9f);
                    PARTS.add(p);
                }
            }
        }

        // ---------- วาดวงดาว ----------
        if (kind >= 0 || idle) {
            for (int i = 0; i < 10; i++) {
                int x = starX(cx, i), y = starY(cy, i);
                if (i == dimIdx) {
                    star(g, x, y, 1, argb(255, lerp(starColor(i, palette), GRAY, dimAmt)));
                } else if (i < lit) {
                    int col = maxedGlow ? palette[(i + (int) (time * 6)) % palette.length] : starColor(i, palette);
                    boolean twinkle = Math.sin(time * 3 + i * 1.7) > 0.6;
                    star(g, x, y, 1, argb(255, twinkle ? WHITE : col));
                } else if (i == charge) {
                    int col = i < 9 ? NameStyle.levelColor(i + 1) : palette[(int) (time * 10) % palette.length];
                    int jx = chargeAmt > 0.5f ? RND.nextInt(3) - 1 : 0, jy = chargeAmt > 0.5f ? RND.nextInt(3) - 1 : 0;
                    star(g, x + jx, y + jy, chargeAmt > 0.6f ? 2 : 1, argb((int) (100 + 155 * chargeAmt), chargeAmt > 0.8f ? WHITE : col));
                } else if (i == cur && kind < 0 && preview) {
                    int a = (int) (90 + 90 * Math.sin(time * 5));
                    star(g, x, y, 1, argb(a, i < 9 ? NameStyle.levelColor(i + 1) : palette[0]));
                } else {
                    g.fill(x, y, x + 1, y + 1, argb(255, GRAY));
                }
            }
        }

        // ---------- particle ----------
        Iterator<Part> it = PARTS.iterator();
        while (it.hasNext()) {
            Part p = it.next();
            p.age += dt;
            if (p.age > p.life) {
                it.remove();
                continue;
            }
            float f = p.age / p.life;
            float x, y, a;
            if (p.conv) {
                float ease = p.comet ? f : f * f;
                x = p.x + (p.tx - p.x) * ease;
                y = p.y + (p.ty - p.y) * ease - (p.comet ? (float) Math.sin(f * Math.PI) * 10 : 0);
                a = p.comet ? 1 : Math.min(1, f * 3);
                if (p.comet) {
                    for (int k = 1; k < 5; k++) {
                        float e2 = Math.max(0, f - k * 0.04f);
                        int tx = Math.round(p.x + (p.tx - p.x) * e2);
                        int ty = Math.round(p.y + (p.ty - p.y) * e2 - (float) Math.sin(e2 * Math.PI) * 10);
                        g.fill(tx, ty, tx + 1, ty + 1, argb((int) (255 * (0.5f - k * 0.1f)), p.color));
                    }
                }
            } else {
                p.vy += p.gravity * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                x = p.x;
                y = p.y;
                a = 1 - f;
            }
            int ix = Math.round(x), iy = Math.round(y);
            g.fill(ix, iy, ix + p.size, iy + p.size, argb((int) (255 * a), p.color));
        }

        // ---------- ตัวเลขลอย ----------
        Font font = Minecraft.getInstance().font;
        Iterator<Txt> tt = TEXTS.iterator();
        while (tt.hasNext()) {
            Txt t = tt.next();
            t.age += dt;
            if (t.age > 1.4f) {
                tt.remove();
                continue;
            }
            t.y -= (t.big ? 10 : 16) * dt;
            int a = (int) (255 * Math.min(1, (1.4f - t.age) * 2));
            g.pose().pushPose();
            float scale = t.big ? 2f : 1f;
            int w = font.width(t.text);
            g.pose().translate(t.x - w * scale / 2f, t.y, 0);
            g.pose().scale(scale, scale, 1);
            int xx = 0;
            for (int i = 0; i < t.text.length(); i++) {
                String ch = String.valueOf(t.text.charAt(i));
                int col = t.colors[(i + (int) (time * 6)) % t.colors.length];
                g.drawString(font, ch, xx, 0, argb(Math.max(8, a), col), true);
                xx += font.width(ch);
            }
            g.pose().popPose();
        }

        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static float endTime(int k) {
        return switch (k) {
            case UpgradeFx.SUCCESS -> 1.4f;
            case UpgradeFx.UNSEAL -> 3.6f;
            default -> 1.5f;
        };
    }

    private static int starX(int cx, int i) {
        return cx + (int) Math.round(Math.cos(-Math.PI / 2 + i * Math.PI * 2 / 10) * RX);
    }

    private static int starY(int cy, int i) {
        return cy + (int) Math.round(Math.sin(-Math.PI / 2 + i * Math.PI * 2 / 10) * RY);
    }

    private static int starColor(int i, int[] palette) {
        return i < 9 ? NameStyle.levelColor(i + 1) : palette[0];
    }

    /** ดาวรูป + (แขนยาว arm พิกเซล) */
    private static void star(GuiGraphics g, int x, int y, int arm, int color) {
        g.fill(x - arm, y, x + arm + 1, y + 1, color);
        g.fill(x, y - arm, x + 1, y + arm + 1, color);
    }

    private static void gather(int x, int y, int radius, int[] colors) {
        double a = RND.nextDouble() * Math.PI * 2;
        double r = radius * (0.6 + RND.nextDouble() * 0.4);
        PARTS.add(Part.conv((float) (x + Math.cos(a) * r), (float) (y + Math.sin(a) * r), x, y,
                colors[RND.nextInt(colors.length)], 0.35f));
    }

    private static void burst(int x, int y, int n, float speed, int[] colors, float life, float gravity) {
        for (int i = 0; i < n; i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            float v = speed * (0.3f + RND.nextFloat() * 0.7f);
            Part p = new Part(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v,
                    colors[RND.nextInt(colors.length)], life * (0.6f + RND.nextFloat() * 0.4f));
            p.gravity = gravity;
            PARTS.add(p);
        }
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static final class Part {
        float x, y, vx, vy, tx, ty, life, age, gravity;
        int color, size = 1;
        boolean conv, comet;

        Part(float x, float y, float vx, float vy, int color, float life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.life = life;
        }

        static Part conv(float x, float y, float tx, float ty, int color, float life) {
            Part p = new Part(x, y, 0, 0, color, life);
            p.tx = tx;
            p.ty = ty;
            p.conv = true;
            return p;
        }

        static Part comet(float x, float y, float tx, float ty, int color, float life) {
            Part p = conv(x, y, tx, ty, color, life);
            p.comet = true;
            p.size = 2;
            return p;
        }
    }

    private static final class Txt {
        final String text;
        final int[] colors;
        final float x;
        float y, age;
        final boolean big;

        Txt(String text, int[] colors, float x, float y, boolean big) {
            this.text = text;
            this.colors = colors;
            this.x = x;
            this.y = y;
            this.big = big;
        }
    }
}
