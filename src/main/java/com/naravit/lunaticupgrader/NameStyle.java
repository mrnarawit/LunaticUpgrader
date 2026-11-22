package com.naravit.lunaticupgrader;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * สีชื่อตามขั้น (ธีม D · Rarity ladder)
 *  +1..+6  สีเดียว
 *  +7      ไล่เฉด
 *  +8      ไล่เฉด + ตัวหนา
 *  +9      ตัวหนา + แสงวิ่งผ่าน (เคลื่อนไหวใน tooltip)
 *  +10     ตัวหนา + สีพลิ้วแบบ Aurora ตามชนิดไอเทม (เคลื่อนไหวใน tooltip)
 */
public final class NameStyle {
    /** สีของขั้น +1 ถึง +9 */
    private static final int[] LEVEL = {
            0xD0D0D0, 0x8FE36B, 0x3FD4A0, 0x45B5FF, 0x5B7CFF,
            0xA66BFF, 0xE86BFF, 0xFF7A3D, 0xFF3D3D};
    // สีของ +10 แยกตามชนิดไอเทม (พลิ้วแบบ Aurora)
    /** อาวุธ/เครื่องมือ: Twilight ส้ม / ชมพู / ม่วงคราม */
    public static final int[] WEAPON = {0xFF8A3D, 0xFF5E8A, 0x5B3DFF, 0xFF5E8A};
    /** โล่: Nether forge ส้ม / ทอง / น้ำตาลแดง */
    public static final int[] SHIELD = {0xFF6A00, 0xFFD54A, 0x8A2E1F, 0xFFD54A};
    /** เครื่องดนตรี (Bard): Ocean pearl ขาวมุก / ฟ้าอมเขียว / น้ำเงิน */
    public static final int[] INSTRUMENT = {0xE8FFFB, 0x5FE1D9, 0x2E9BFF, 0x5FE1D9};
    /** ธนู/หน้าไม้: Toxic neon เขียวนีออน */
    public static final int[] BOW = {0xB8FF3A, 0x3AFF8A, 0xE1FF5E, 0x3AFF8A};
    /** คทา/ไม้กายสิทธิ์ (Wizards): Mint candy มิ้นต์ / ขาว / ชมพู */
    public static final int[] STAFF = {0x7DFFC8, 0xFFFFFF, 0xFF9EC4, 0xFFFFFF};
    /** Pickaxe: Fire and ice ฟ้า / ขาว / ส้ม */
    public static final int[] PICKAXE = {0x3FE0FF, 0xFFFFFF, 0xFF8A2E, 0xFFFFFF};
    /** เกราะ: Obsidian flame ม่วงเข้ม / ม่วงสด / ส้มไฟ */
    public static final int[] ARMOR = {0x3A1A5A, 0xC040FF, 0xFF8A2E, 0xC040FF};

    /** เลือกชุดสี +10 ตามชนิดไอเทม */
    public static int[] paletteFor(ItemStack stack) {
        switch (ModCompat.kindOf(stack)) {
            case LUTE, LYRE: return INSTRUMENT;
            case MAGIC: return STAFF;
            default: break;
        }
        if (stack.getItem() instanceof ShieldItem) return SHIELD;
        if (stack.getItem() instanceof ProjectileWeaponItem) return BOW;
        if (stack.getItem() instanceof ArmorItem) return ARMOR;
        if (stack.getItem() instanceof PickaxeItem || stack.is(ItemTags.PICKAXES)) return PICKAXE;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        boolean attack = false, armor = false;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) attack = true;
            if (e.attribute().value() == Attributes.ARMOR.value()) armor = true;
        }
        return armor && !attack ? ARMOR : WEAPON;
    }

    private NameStyle() {}

    /** สีประจำขั้น +1..+9 (ใช้กับดาวแต่ละดวงในวงดาว) */
    public static int levelColor(int level) {
        return LEVEL[Math.max(1, Math.min(9, level)) - 1];
    }

    /** สีหลักของขั้น (ใช้กับ +N ในข้อความ / บรรทัด Upgrade) */
    public static int mainColor(int level, int[] palette) {
        if (level >= 10) return palette[0];
        return LEVEL[Math.max(1, level) - 1];
    }

    /** ชื่อแบบมีลูกเล่น; animated = ใช้เวลาปัจจุบัน (สำหรับ tooltip ฝั่ง client) */
    public static MutableComponent name(String base, int level, boolean animated, int[] palette) {
        String text = base + " +" + level;
        double t = animated ? Util.getMillis() / 1000.0 : 0.0;
        boolean bold = level >= 8;
        int n = text.length();
        MutableComponent out = Component.empty().withStyle(s -> s.withItalic(false));
        for (int i = 0; i < n; i++) {
            double p = n > 1 ? (double) i / (n - 1) : 0;
            int color = switch (level) {
                case 7 -> lerp(LEVEL[5], LEVEL[6], p);
                case 8 -> lerp(LEVEL[6], LEVEL[7], p);
                case 9 -> shimmer(LEVEL[8], i, n, t, animated);
                default -> level >= 10 ? aurora(palette, (double) i / n, t) : LEVEL[level - 1];
            };
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(bold).withItalic(false)));
        }
        return out;
    }

    /** สีของบรรทัด "Upgrade: ..." */
    public static int lineColor(int level, boolean animated, int[] palette) {
        if (level >= 10) return aurora(palette, 0, animated ? Util.getMillis() / 1000.0 : 0.0);
        if (level == 7 || level == 8) return LEVEL[level - 1];
        return mainColor(level, palette);
    }

    /** ขั้น +1..+6 ใช้สีเดียว -> เก็บชื่อแบบแปลภาษาได้ */
    public static boolean isSimple(int level) {
        return level <= 6;
    }

    private static int shimmer(int base, int i, int n, double t, boolean animated) {
        if (!animated) return base;
        double pos = (((t * 0.6) % 1.6) - 0.3) * n;
        double f = Math.max(0, 1 - Math.abs(i - pos) / 2.5) * 0.85;
        return lerp(base, 0xFFFFFF, f);
    }

    private static int aurora(int[] pal, double p, double t) {
        double x = p * 0.5 + 0.15 * Math.sin(t + p * 6) - t * 0.1;
        x = x - Math.floor(x);
        double s = x * (pal.length - 1);
        int k = Math.min((int) s, pal.length - 2);
        return lerp(pal[k], pal[k + 1], s - k);
    }

    private static int lerp(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
