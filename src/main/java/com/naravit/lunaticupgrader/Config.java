package com.naravit.lunaticupgrader;

import net.neoforged.neoforge.common.ModConfigSpec;


/**
 * SERVER config: <world>/serverconfig/lunaticupgrader-server.toml
 * (ค่าจาก server ถูกส่งไปให้ client อัตโนมัติ -> tooltip กับค่าจริงตรงกันเสมอ)
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SHIELD_TOUGHNESS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SHIELD_ARMOR_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ARMOR_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ARMOR_TOUGHNESS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue DURABILITY_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue PICKAXE_SPEED_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SPELL_RESISTANCE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue MAGIC_WEAPON_SPELL_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue INSTRUMENT_MAJOR_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue INSTRUMENT_MINOR_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue INSTRUMENT_ATTACK_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue[] SUCCESS_CHANCE = new ModConfigSpec.DoubleValue[LunaticUpgrader.MAX_LEVEL];
    public static final ModConfigSpec.IntValue[] XP_COST = new ModConfigSpec.IntValue[LunaticUpgrader.MAX_LEVEL];

    /** Default success chance for upgrading to +1 ... +10 */
    private static final double[] DEFAULT_CHANCE = {0.95, 0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50};

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("upgrade");
        BONUS_PER_LEVEL = b.comment("Bonus per upgrade level as a percentage (0.06 = +6% Attack Damage / Arrow Damage per level)")
                .defineInRange("bonusPerLevel", 0.06, 0.0, 10.0);
        SHIELD_TOUGHNESS_PER_LEVEL = b.comment("Shields: flat Armor Toughness added per upgrade level while held")
                .defineInRange("shieldToughnessPerLevel", 0.3, 0.0, 100.0);
        SHIELD_ARMOR_PER_LEVEL = b.comment("Shields: flat Armor added per upgrade level while held")
                .defineInRange("shieldArmorPerLevel", 0.3, 0.0, 100.0);
        ARMOR_PER_LEVEL = b.comment("Armor pieces: flat Armor added per upgrade level")
                .defineInRange("armorPerLevel", 0.3, 0.0, 100.0);
        ARMOR_TOUGHNESS_PER_LEVEL = b.comment("Armor pieces: flat Armor Toughness added per upgrade level")
                .defineInRange("armorToughnessPerLevel", 0.2, 0.0, 100.0);
        DURABILITY_PER_LEVEL = b.comment("Max durability bonus per upgrade level for all upgradeable items (0.1 = +10%)")
                .defineInRange("durabilityPerLevel", 0.1, 0.0, 10.0);
        PICKAXE_SPEED_PER_LEVEL = b.comment("Pickaxes: mining speed bonus per upgrade level (0.02 = +2%)")
                .defineInRange("pickaxeSpeedPerLevel", 0.02, 0.0, 10.0);
        b.pop();

        b.comment("Optional mod compat (ignored if the mod is not installed)").push("compat");
        SPELL_RESISTANCE_PER_LEVEL = b.comment("Spell Power Attribute: armor spell_power:resistance.generic per level (0.01 = +1%)")
                .defineInRange("spellResistancePerLevel", 0.01, 0.0, 10.0);
        MAGIC_WEAPON_SPELL_PER_LEVEL = b.comment("Wizards: weapons tagged rpg_series:archetype/magic_damage_weapon get spell_power:generic per level instead of attack damage")
                .defineInRange("magicWeaponSpellPowerPerLevel", 0.03, 0.0, 10.0);
        INSTRUMENT_MAJOR_PER_LEVEL = b.comment("Bard: main school per level (lutes = arcane, lyres = healing)")
                .defineInRange("instrumentMajorPerLevel", 0.04, 0.0, 10.0);
        INSTRUMENT_MINOR_PER_LEVEL = b.comment("Bard: secondary school per level (lutes = healing, lyres = arcane)")
                .defineInRange("instrumentMinorPerLevel", 0.02, 0.0, 10.0);
        INSTRUMENT_ATTACK_PER_LEVEL = b.comment("Bard: attack damage per level for lutes/lyres (replaces bonusPerLevel)")
                .defineInRange("instrumentAttackPerLevel", 0.02, 0.0, 10.0);
        b.pop();

        b.comment("Success chance for upgrading TO each level (1.0 = 100%).",
                        "On failure the template and XP are consumed and the item drops 1 level.")
                .push("successChance");
        for (int i = 0; i < LunaticUpgrader.MAX_LEVEL; i++) {
            SUCCESS_CHANCE[i] = b.defineInRange("toLevel" + (i + 1), DEFAULT_CHANCE[i], 0.0, 1.0);
        }
        b.pop();

        b.comment("Experience POINTS consumed when upgrading TO each level")
                .push("xpCost");
        for (int i = 0; i < LunaticUpgrader.MAX_LEVEL; i++) {
            XP_COST[i] = b.defineInRange("toLevel" + (i + 1), 25 * (i + 1), 0, 1_000_000);
        }
        b.pop();

        SPEC = b.build();
    }

    /** อ่านค่า config; ถ้ายังไม่โหลด (เช่น หน้าเมนูหลัก) ใช้ค่าเริ่มต้น */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private Config() {}
}
