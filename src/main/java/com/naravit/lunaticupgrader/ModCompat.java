package com.naravit.lunaticupgrader;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * รองรับ mod เสริมแบบ optional (ไม่ต้องลงก็ได้ ไม่มี dependency ตอน build):
 * - Spell Power Attribute  -> attribute spell_power:*
 * - Wizards (RPG Series)   -> tag rpg_series:archetype/magic_damage_weapon
 * - Bard (RPG Series plus) -> tag bards_rpg:lutes, bards_rpg:lyres
 * ถ้าไม่ได้ลง mod นั้น attribute จะหาไม่เจอ / tag จะว่าง -> ข้ามไปเอง
 */
public final class ModCompat {
    public static final String SPELL_GENERIC = "spell_power:generic";
    public static final String SPELL_ARCANE = "spell_power:arcane";
    public static final String SPELL_HEALING = "spell_power:healing";
    public static final String SPELL_RESISTANCE = "spell_power:resistance.generic";

    public static final TagKey<Item> MAGIC_WEAPON = tag("rpg_series:archetype/magic_damage_weapon");
    public static final TagKey<Item> LUTES = tag("bards_rpg:lutes");
    public static final TagKey<Item> LYRES = tag("bards_rpg:lyres");

    public enum WeaponKind { NORMAL, MAGIC, LUTE, LYRE }

    private ModCompat() {}

    private static TagKey<Item> tag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    /** หา attribute จาก id; ไม่มี (ไม่ได้ลง mod) -> empty */
    public static Optional<Holder<Attribute>> attribute(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Optional.empty();
        return BuiltInRegistries.ATTRIBUTE.getHolder(rl).map(h -> (Holder<Attribute>) h);
    }

    /** ชนิดอาวุธ: เช็คเครื่องดนตรีก่อน เผื่อมันอยู่ใน tag magic weapon ด้วย */
    public static WeaponKind kindOf(ItemStack stack) {
        if (stack.is(LUTES)) return WeaponKind.LUTE;
        if (stack.is(LYRES)) return WeaponKind.LYRE;
        if (stack.is(MAGIC_WEAPON)) return WeaponKind.MAGIC;
        return WeaponKind.NORMAL;
    }
}
