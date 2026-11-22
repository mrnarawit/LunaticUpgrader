package com.naravit.lunaticupgrader;

import java.text.DecimalFormat;

import java.util.List;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class UpgradeEvents {
    /** id ของค่าพลังโจมตีพื้นฐานใน vanilla (Item.BASE_ATTACK_DAMAGE_ID เป็น protected) */
    private static final ResourceLocation BASE_ATTACK_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    private static final ResourceLocation SHIELD_TOUGHNESS_ID = id("upgrade_shield_toughness");
    private static final ResourceLocation SHIELD_ARMOR_ID = id("upgrade_shield_armor");
    private static final ResourceLocation MINING_SPEED_ID = id("upgrade_mining_speed");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.##");

    private UpgradeEvents() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LunaticUpgrader.MODID, path);
    }

    // ------------------------------------------------------------------
    // 1) Anvil: อาวุธ/เครื่องมือ/ธนู (ช่องซ้าย) + Smithing Template ใดก็ได้ (ช่องขวา) -> +1
    // ------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        if (!(right.getItem() instanceof SmithingTemplateItem)) return;
        if (!isUpgradeable(left)) return;

        UpgradeData current = left.getOrDefault(LunaticUpgrader.UPGRADE.get(), UpgradeData.NONE);
        if (current.level() >= LunaticUpgrader.MAX_LEVEL) {
            event.setCanceled(true); // MAX แล้ว ไม่มีผลลัพธ์
            return;
        }

        int newLevel = current.level() + 1;
        int xpCost = Config.get(Config.XP_COST[newLevel - 1]);
        double chance = Config.get(Config.SUCCESS_CHANCE[newLevel - 1]);
        Player player = event.getPlayer();
        boolean creative = player != null && player.hasInfiniteMaterials();
        boolean enough = creative || (player != null && totalXpPoints(player) >= xpCost);

        // แสดงโอกาสสำเร็จที่ action bar (ฝั่ง server)
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("lunaticupgrader.upgrade.preview",
                            levelSuffix(newLevel, left).copy(),
                            Math.round(chance * 100),
                            xpCost)
                    .withStyle(enough ? ChatFormatting.YELLOW : ChatFormatting.RED), true);
        }

        // XP ไม่พอ -> ไม่มีผลลัพธ์
        if (!enough) {
            event.setCanceled(true);
            return;
        }

        ItemStack output = left.copyWithCount(1);
        applyUpgrade(output, new UpgradeData(newLevel, resolveBaseName(left, current)));
        // Enchantment Cost ที่ Anvil แสดง = จำนวนเลเวลที่ผู้เล่นจะลดลงจริงเมื่อเสีย xpCost แต้ม
        int levelCost = levelCostFor(player, xpCost, creative);
        // จำค่านี้ไว้ในผลลัพธ์ชั่วคราว เพื่อคืนเลเวลที่ Anvil หักให้ถูกต้องตอนหยิบ
        output.set(LunaticUpgrader.UPGRADE_COST.get(), levelCost);
        output.set(LunaticUpgrader.UPGRADE_XP.get(), xpCost);

        event.setOutput(output);
        event.setCost(levelCost);
        event.setMaterialCost(1); // ใช้ Template 1 ชิ้น
    }

    // ------------------------------------------------------------------
    // 2) ตอนหยิบผลลัพธ์ออกจาก Anvil: หัก XP + สุ่มโอกาสสำเร็จ
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onAnvilTake(AnvilRepairEvent event) {
        ItemStack right = event.getRight();
        if (!(right.getItem() instanceof SmithingTemplateItem)) return;

        ItemStack output = event.getOutput();
        UpgradeData result = output.get(LunaticUpgrader.UPGRADE.get());
        if (result == null || result.level() <= 0) return;

        // ลบค่า cost ชั่วคราวออก (ทั้งสองฝั่ง) ไม่ให้ติดไปกับไอเทม
        Integer levelCost = output.get(LunaticUpgrader.UPGRADE_COST.get());
        output.remove(LunaticUpgrader.UPGRADE_COST.get());
        output.remove(LunaticUpgrader.UPGRADE_XP.get());

        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // Anvil หักเลเวลไปแล้ว -> คืนให้ก่อน แล้วหักเป็นแต้ม XP ตาม config (ผลลัพธ์เท่ากับที่แสดงไว้)
        if (!player.getAbilities().instabuild && levelCost != null) {
            player.giveExperienceLevels(levelCost);
        }
        if (!player.hasInfiniteMaterials()) {
            int xpCost = Config.get(Config.XP_COST[result.level() - 1]);
            player.giveExperiencePoints(-Math.min(xpCost, totalXpPoints(player)));
        }

        double chance = Config.get(Config.SUCCESS_CHANCE[result.level() - 1]);
        if (player.getRandom().nextDouble() < chance) {
            Component msg = Component.translatable("lunaticupgrader.upgrade.success",
                    levelSuffix(result.level(), output).copy());
            if (result.level() >= LunaticUpgrader.MAX_LEVEL) {
                // +10: ปลดผนึก -> ขึ้นข้อความหลังโซ่เส้นสุดท้ายขาด
                UpgradeFx.play(player, output, UpgradeFx.UNSEAL, result.level());
                if (player instanceof ServerPlayer sp) {
                    UpgradeFx.later(sp, UpgradeFx.UNSEAL_DELAY, () -> sp.displayClientMessage(msg, true));
                }
            } else {
                UpgradeFx.play(player, output, UpgradeFx.SUCCESS, result.level());
                player.displayClientMessage(msg, true);
            }
            return;
        }

        // เอฟเฟกต์ตีพลาด (ใช้ขั้นก่อนตี)
        UpgradeFx.play(player, output, UpgradeFx.FAIL, result.level() - 1);

        // ล้มเหลว: ลดลง 1 ขั้นจากขั้นเดิม (Template และ XP ถูกใช้ไปแล้ว)
        int dropped = Math.max(0, result.level() - 2);
        if (dropped > 0) {
            applyUpgrade(output, new UpgradeData(dropped, result.baseName()));
            player.displayClientMessage(Component.translatable("lunaticupgrader.upgrade.fail",
                            levelSuffix(dropped, output).copy())
                    .withStyle(ChatFormatting.RED), true);
        } else {
            output.remove(LunaticUpgrader.UPGRADE.get());
            applyDurability(output, 0);
            if (result.baseName().isEmpty()) output.remove(DataComponents.CUSTOM_NAME);
            else output.set(DataComponents.CUSTOM_NAME, Component.literal(result.baseName()));
            player.displayClientMessage(Component.translatable("lunaticupgrader.upgrade.fail_zero")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    // ------------------------------------------------------------------
    // 3) อาวุธ/เครื่องมือ: +6% Attack Damage ต่อขั้น (Pickaxe: ขุดไวขึ้น +2% ต่อขั้นด้วย)
    //    เกราะ: +0.3 Armor และ +0.2 Armor Toughness ต่อขั้น (+1% Spell Resistance ถ้ามี Spell Power)
    //    Wizards magic weapon: +3% Spell Power (generic) แทน Attack Damage
    //    Bard lute: +4% Arcane, +2% Healing, +2% Attack / lyre: +2% Arcane, +4% Healing, +2% Attack
    //    โล่: +0.3 Armor และ +0.3 Armor Toughness ต่อขั้น (ตอนถือ)
    // ------------------------------------------------------------------
    // LOWEST = ทำงานหลัง mod อื่น (เช่น neoattributemodify) เพื่อไม่ให้ค่าของเราถูกเขียนทับ
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        UpgradeData data = stack.get(LunaticUpgrader.UPGRADE.get());
        if (data == null || data.level() <= 0) return;

        int lvl = data.level();

        // โล่: เมื่อถือไว้ในมือ (มือหลักหรือมือรอง)
        if (stack.getItem() instanceof ShieldItem) {
            event.addModifier(Attributes.ARMOR,
                    new AttributeModifier(SHIELD_ARMOR_ID, flat(Config.get(Config.SHIELD_ARMOR_PER_LEVEL), lvl),
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.HAND);
            event.addModifier(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(SHIELD_TOUGHNESS_ID, flat(Config.get(Config.SHIELD_TOUGHNESS_PER_LEVEL), lvl),
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.HAND);
            return;
        }

        EquipmentSlotGroup attackSlot = null, armorSlot = null;
        for (ItemAttributeModifiers.Entry entry : event.getDefaultModifiers().modifiers()) {
            Attribute attr = entry.attribute().value();
            if (attr == Attributes.ATTACK_DAMAGE.value()) attackSlot = entry.slot();
            else if (attr == Attributes.ARMOR.value()) armorSlot = entry.slot();
        }

        // เกราะ
        if (armorSlot != null) {
            // รวมโบนัสเข้ากับค่าเดิมของชิ้นนั้น -> tooltip ขึ้นตัวเลขเดียว เช่น +8 Armor -> +9.5 Armor
            mergeFlat(event, Attributes.ARMOR, flat(Config.get(Config.ARMOR_PER_LEVEL), lvl),
                    armorSlot, "upgrade_armor_" + armorSlot.getSerializedName());
            mergeFlat(event, Attributes.ARMOR_TOUGHNESS, flat(Config.get(Config.ARMOR_TOUGHNESS_PER_LEVEL), lvl),
                    armorSlot, "upgrade_armor_toughness_" + armorSlot.getSerializedName());
            // Spell Power Attribute (ถ้าลงไว้): Spell Resistance +1% ต่อขั้น
            addPercent(event, ModCompat.SPELL_RESISTANCE, "upgrade_spell_resistance_" + armorSlot.getSerializedName(),
                    Config.get(Config.SPELL_RESISTANCE_PER_LEVEL) * lvl, armorSlot);
            return;
        }

        // อาวุธ/เครื่องมือ (+ อาวุธเวท/เครื่องดนตรีจาก RPG Series)
        EquipmentSlotGroup weaponSlot = attackSlot != null ? attackSlot : EquipmentSlotGroup.MAINHAND;
        switch (ModCompat.kindOf(stack)) {
            case MAGIC -> addPercent(event, ModCompat.SPELL_GENERIC, "upgrade_spell_generic",
                    Config.get(Config.MAGIC_WEAPON_SPELL_PER_LEVEL) * lvl, weaponSlot);
            case LUTE, LYRE -> {
                boolean lute = ModCompat.kindOf(stack) == ModCompat.WeaponKind.LUTE;
                double major = Config.get(Config.INSTRUMENT_MAJOR_PER_LEVEL) * lvl;
                double minor = Config.get(Config.INSTRUMENT_MINOR_PER_LEVEL) * lvl;
                addPercent(event, ModCompat.SPELL_ARCANE, "upgrade_spell_arcane", lute ? major : minor, weaponSlot);
                addPercent(event, ModCompat.SPELL_HEALING, "upgrade_spell_healing", lute ? minor : major, weaponSlot);
                if (attackSlot != null) {
                    mergeAttackPercent(event, Config.get(Config.INSTRUMENT_ATTACK_PER_LEVEL) * lvl, attackSlot);
                }
            }
            case NORMAL -> {
                if (attackSlot != null) {
                    mergeAttackPercent(event, bonusPercent(lvl), attackSlot);
                }
            }
        }

        // Pickaxe: ขุดไวขึ้น +2% ต่อขั้น (ตอนถือมือหลัก)
        if (stack.getItem() instanceof PickaxeItem || stack.is(ItemTags.PICKAXES)) {
            event.addModifier(Attributes.BLOCK_BREAK_SPEED,
                    new AttributeModifier(MINING_SPEED_ID, Config.get(Config.PICKAXE_SPEED_PER_LEVEL) * lvl,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    EquipmentSlotGroup.MAINHAND);
        }
    }

    /**
     * เพิ่ม Attack Damage เป็น % ของค่าพลังโจมตีจริงของอาวุธ (ADD_MULTIPLIED_BASE)
     * เกมคิดเป็น (พลังพื้นฐาน 1 + ค่าโจมตีของอาวุธที่ใช้งานจริง) x (1 + %)
     * จึงอิงค่าที่ datapack / mod อื่นแก้ไว้ด้วย เช่น ดาบที่ถูกตั้งเป็น 7 -> +10 = 7 x 1.6 = 11.2
     * (tooltip รวมเป็นบรรทัดเดียวใน fixAttackTooltip)
     */
    private static void mergeAttackPercent(ItemAttributeModifierEvent event, double percent, EquipmentSlotGroup slot) {
        if (percent <= 0) return;
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(id("upgrade_attack_damage"), percent, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                slot);
    }

    /**
     * บวกค่าเข้า modifier ADD_VALUE เดิมของ attribute นั้น (ใช้ id เดิม -> แสดงเป็นบรรทัดเดียวใน tooltip)
     * ถ้าไม่มีค่าเดิม เพิ่ม modifier ใหม่แทน
     */
    private static void mergeFlat(ItemAttributeModifierEvent event, Holder<Attribute> attribute, double bonus,
                                  EquipmentSlotGroup slot, String fallbackId) {
        if (bonus == 0) return;
        ItemAttributeModifiers.Entry target = null;
        for (ItemAttributeModifiers.Entry e : event.getModifiers()) {
            if (e.attribute().value() != attribute.value()) continue;
            if (e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
            // เลือกตัว base ของ vanilla ก่อน (เช่น minecraft:base_attack_damage)
            if (target == null || e.modifier().id().equals(BASE_ATTACK_ID)) target = e;
        }
        if (target != null) {
            event.replaceModifier(attribute,
                    new AttributeModifier(target.modifier().id(), target.modifier().amount() + bonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    target.slot());
        } else {
            event.addModifier(attribute,
                    new AttributeModifier(id(fallbackId), bonus, AttributeModifier.Operation.ADD_VALUE), slot);
        }
    }

    /** เพิ่ม attribute แบบ % (ADD_MULTIPLIED_TOTAL) ถ้า attribute นั้นมีอยู่ (ลง mod ไว้) */
    private static void addPercent(ItemAttributeModifierEvent event, String attributeId, String modifierId,
                                   double amount, EquipmentSlotGroup slot) {
        if (amount <= 0) return;
        ModCompat.attribute(attributeId).ifPresent(attr -> event.addModifier(attr,
                new AttributeModifier(id(modifierId), amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                slot));
    }

    private static double flat(double perLevel, int level) {
        return Math.round(perLevel * level * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // 4) ธนู/หน้าไม้: ลูกธนูที่ยิงออกไปแรงขึ้น +6% ต่อขั้น
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !(weapon.getItem() instanceof ProjectileWeaponItem)) return;
        UpgradeData data = weapon.get(LunaticUpgrader.UPGRADE.get());
        if (data == null || data.level() <= 0) return;

        arrow.setBaseDamage(arrow.getBaseDamage() * (1.0 + bonusPercent(data.level())));
    }

    // ------------------------------------------------------------------
    // 5) Tooltip
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        UpgradeData data = stack.get(LunaticUpgrader.UPGRADE.get());
        if (data == null || data.level() <= 0) return;

        Component line = data.level() >= LunaticUpgrader.MAX_LEVEL
                ? Component.translatable("lunaticupgrader.tooltip.maxed", data.level())
                : Component.translatable("lunaticupgrader.tooltip.level", data.level(), LunaticUpgrader.MAX_LEVEL);
        // ชื่อแบบเคลื่อนไหว (tooltip ถูกสร้างใหม่ทุกเฟรม -> ใช้เวลาปัจจุบันได้)
        if (!event.getToolTip().isEmpty()) {
            event.getToolTip().set(0, NameStyle.name(baseNameText(stack, data), data.level(), true, NameStyle.paletteFor(stack)));
        }
        int insertAt = Math.min(1, event.getToolTip().size());
        int lineColor = NameStyle.lineColor(data.level(), true, NameStyle.paletteFor(stack));
        event.getToolTip().add(insertAt, line.copy().withStyle(st -> st.withColor(lineColor)));

        // ธนูไม่มี attribute ให้ vanilla แสดง -> เพิ่มบรรทัดเอง
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            event.getToolTip().add(Component.translatable("lunaticupgrader.tooltip.arrow_damage",
                            PERCENT_FORMAT.format(bonusPercent(data.level()) * 100))
                    .withStyle(ChatFormatting.BLUE));
        }
    }

    // ------------------------------------------------------------------
    // 6) แก้ตัวเลข Attack Damage ใน tooltip ให้ตรงกับค่าจริง
    //    บาง mod (เช่น neoattributemodify กับอาวุธที่ datapack แก้ค่าไว้) เขียนบรรทัดนี้เองโดยไม่รวมโบนัสของเรา
    //    -> คำนวณค่าจริงจาก modifier ของไอเทม แล้วแทนตัวเลขในบรรทัดนั้น (ทำเป็นตัวสุดท้าย)
    // ------------------------------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fixAttackTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        UpgradeData data = stack.get(LunaticUpgrader.UPGRADE.get());
        if (data == null || data.level() <= 0) return;
        double pct = attackPercent(stack, data.level());
        if (pct <= 0) return;

        // ค่าที่เกมคำนวณจาก modifier ของไอเทม (กรณีไม่มี mod อื่นมาแก้การแสดงผล)
        double add = Attributes.ATTACK_DAMAGE.value().getDefaultValue(), mulBase = 0, mulTotal = 1;
        boolean found = false;
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            if (e.attribute().value() != Attributes.ATTACK_DAMAGE.value() || !e.slot().test(EquipmentSlot.MAINHAND)) continue;
            switch (e.modifier().operation()) {
                case ADD_VALUE -> { add += e.modifier().amount(); found = true; }
                case ADD_MULTIPLIED_BASE -> mulBase += e.modifier().amount();
                case ADD_MULTIPLIED_TOTAL -> mulTotal *= 1 + e.modifier().amount();
            }
        }
        if (!found) return;
        double computed = add * (1 + mulBase) * mulTotal;

        String nameKey = Attributes.ATTACK_DAMAGE.value().getDescriptionId();
        String pctText = ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(pct * 100);
        List<Component> tip = event.getToolTip();
        for (int i = 0; i < tip.size(); i++) {
            Component line = tip.get(i);

            // ลบบรรทัด "+60% Attack Damage" ของเราออก (รวมไปไว้ในตัวเลขหลักแล้ว)
            TranslatableContents pl = findLine(line, "attribute.modifier.plus.1", nameKey);
            if (pl != null && argText(pl.getArgs()[0]).equals(pctText)) {
                tip.remove(i);
                i--;
                continue;
            }

            TranslatableContents tc = findLine(line, "attribute.modifier.equals.0", nameKey);
            if (tc == null) continue;
            double shown;
            try {
                shown = Double.parseDouble(argText(tc.getArgs()[0]).replace(',', '.').trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            // ถ้าตัวเลขที่แสดงยังไม่รวมโบนัส (เช่น neoattributemodify แสดงค่าของ datapack) -> คูณเพิ่มเอง
            double real = Math.abs(shown - computed) < 0.01 ? computed : shown * (1 + pct);
            String text = ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(real);
            // ใช้สีเดิมของบรรทัด (ปกติเขียว); ถ้าเป็นสีทองแบบ "merged" ของ NeoForge ให้เป็นเขียวตามปกติ
            TextColor color = findColor(line);
            if (color == null || color.equals(TextColor.fromLegacyFormat(ChatFormatting.GOLD))) {
                color = TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN);
            }
            tip.set(i, Component.literal(" ")
                    .append(Component.translatable("attribute.modifier.equals.0", text, Component.translatable(nameKey)))
                    .withStyle(line.getStyle().withColor(color)));
        }
    }

    /** % Attack Damage ที่ขั้นนี้ให้ (0 = ไม่เพิ่ม เช่น อาวุธเวท) */
    private static double attackPercent(ItemStack stack, int level) {
        return switch (ModCompat.kindOf(stack)) {
            case NORMAL -> bonusPercent(level);
            case LUTE, LYRE -> Config.get(Config.INSTRUMENT_ATTACK_PER_LEVEL) * level;
            case MAGIC -> 0;
        };
    }

    private static String argText(Object arg) {
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    /** สีแรกที่เจอในบรรทัด (รวมส่วนย่อย) */
    private static TextColor findColor(Component c) {
        if (c.getStyle().getColor() != null) return c.getStyle().getColor();
        for (Component sib : c.getSiblings()) {
            TextColor r = findColor(sib);
            if (r != null) return r;
        }
        if (c.getContents() instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component ac) {
                    TextColor r = findColor(ac);
                    if (r != null) return r;
                }
            }
        }
        return null;
    }

    /** หาบรรทัด attribute ตาม translation key เช่น "<ตัวเลข> Attack Damage" (attribute.modifier.equals.0) */
    private static TranslatableContents findLine(Component c, String key, String nameKey) {
        if (c.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals(key)
                && tc.getArgs().length >= 2
                && tc.getArgs()[1] instanceof Component name
                && name.getContents() instanceof TranslatableContents nt
                && nt.getKey().equals(nameKey)) {
            return tc;
        }
        for (Component sib : c.getSiblings()) {
            TranslatableContents r = findLine(sib, key, nameKey);
            if (r != null) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** อาวุธ/เครื่องมือ (มีค่า Attack Damage), เกราะ (มีค่า Armor), ธนู/หน้าไม้ หรือโล่ — รองรับไอเทมจาก mod อื่นด้วย */
    public static boolean isUpgradeable(ItemStack stack) {
        if (stack.getItem() instanceof ProjectileWeaponItem) return true;
        if (stack.getItem() instanceof ShieldItem) return true;
        // เกราะใน 1.21.1 เก็บค่า Armor ไว้ที่ตัว ArmorItem ไม่ได้อยู่ใน component ของ ItemStack
        if (stack.getItem() instanceof ArmorItem) return true;
        if (ModCompat.kindOf(stack) != ModCompat.WeaponKind.NORMAL) return true;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            Attribute attr = entry.attribute().value();
            if (attr == Attributes.ATTACK_DAMAGE.value() || attr == Attributes.ARMOR.value()) return true;
        }
        return false;
    }

    private static double bonusPercent(int level) {
        return Config.get(Config.BONUS_PER_LEVEL) * level;
    }

    /** แต้ม XP ทั้งหมดที่ผู้เล่นมีจริง (คำนวณจาก level + progress เหมือน vanilla) */
    public static int totalXpPoints(Player player) {
        int total = 0;
        for (int i = 0; i < player.experienceLevel; i++) {
            total += xpNeededForLevel(i);
        }
        return total + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /**
     * จำนวนเลเวลที่ลดลงจริงเมื่อเสีย xpCost แต้ม (อย่างน้อย 1 เพราะ Anvil ต้องมี cost > 0)
     * Creative: แสดงจำนวนเลเวลที่ต้องมีถ้าเริ่มจาก 0
     */
    private static int levelCostFor(Player player, int xpCost, boolean creative) {
        if (player == null || creative) {
            return Math.max(1, levelsToReach(xpCost));
        }
        int before = player.experienceLevel;
        int after = levelForPoints(totalXpPoints(player) - xpCost);
        return Math.max(1, before - after);
    }

    /** เลเวลที่ได้จากแต้ม XP ทั้งหมด */
    private static int levelForPoints(int points) {
        int level = 0;
        while (points >= xpNeededForLevel(level)) {
            points -= xpNeededForLevel(level);
            level++;
        }
        return level;
    }

    /** เลเวลต่ำสุดที่มีแต้มรวม >= points */
    private static int levelsToReach(int points) {
        int level = 0, total = 0;
        while (total < points) {
            total += xpNeededForLevel(level);
            level++;
        }
        return level;
    }

    private static int xpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** ตั้งขั้นตีบวกตรง ๆ (ใช้กับคำสั่ง) 0 = ล้างการตีบวก */
    public static void setLevel(ItemStack stack, int level) {
        UpgradeData current = stack.getOrDefault(LunaticUpgrader.UPGRADE.get(), UpgradeData.NONE);
        String baseName = resolveBaseName(stack, current);
        if (level <= 0) {
            stack.remove(LunaticUpgrader.UPGRADE.get());
            applyDurability(stack, 0);
            if (baseName.isEmpty()) stack.remove(DataComponents.CUSTOM_NAME);
            else stack.set(DataComponents.CUSTOM_NAME, Component.literal(baseName));
            return;
        }
        applyUpgrade(stack, new UpgradeData(Math.min(level, LunaticUpgrader.MAX_LEVEL), baseName));
    }

    private static void applyUpgrade(ItemStack stack, UpgradeData data) {
        stack.set(LunaticUpgrader.UPGRADE.get(), data);
        applyDurability(stack, data.level());

        int level = data.level();
        MutableComponent name;
        if (NameStyle.isSimple(level)) {
            // สีเดียว: เก็บชื่อแบบแปลภาษาได้
            Component base = data.baseName().isEmpty()
                    ? stack.getItem().getName(stack)
                    : Component.literal(data.baseName());
            name = Component.empty().append(base).append(" +" + level);
            name.withStyle(style -> style.withColor(NameStyle.mainColor(level, NameStyle.paletteFor(stack))).withItalic(false));
        } else {
            // ไล่เฉดทีละตัวอักษร (ชื่อนิ่ง; tooltip จะทำให้เคลื่อนไหวเอง)
            name = NameStyle.name(baseNameText(stack, data), level, false, NameStyle.paletteFor(stack));
        }
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private static String baseNameText(ItemStack stack, UpgradeData data) {
        return data.baseName().isEmpty() ? stack.getItem().getName(stack).getString() : data.baseName();
    }

    /** ความทนทานสูงสุด +10% ต่อขั้น (คิดจากค่าปกติของไอเทม, ไม่แสดงใน tooltip) */
    private static void applyDurability(ItemStack stack, int level) {
        int base = stack.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (base <= 0 || !stack.has(DataComponents.MAX_DAMAGE)) return;
        int max = (int) Math.round(base * (1.0 + Config.get(Config.DURABILITY_PER_LEVEL) * level));
        stack.set(DataComponents.MAX_DAMAGE, Math.max(1, max));
        // ลดขั้นแล้วความทนทานสูงสุดลดลง -> กันไม่ให้ของพังทันที
        if (stack.getDamageValue() >= max) stack.setDamageValue(Math.max(0, max - 1));
    }

    private static Component levelSuffix(int level, ItemStack stack) {
        int color = NameStyle.mainColor(level, NameStyle.paletteFor(stack));
        return Component.literal("+" + level).withStyle(st -> st.withColor(color).withBold(level >= 8));
    }


    /**
     * หาชื่อฐาน (ไม่มี +N): ถ้าเคยตีบวกแล้วใช้ชื่อที่เก็บไว้ในข้อมูล,
     * ถ้ายังไม่เคยตีแต่ถูกเปลี่ยนชื่อไว้ก่อน ใช้ชื่อนั้น, ไม่งั้น "" = ชื่อปกติของไอเทม
     * (ไม่อ่านจากช่องพิมพ์ชื่อของ Anvil เพื่อกันชื่อ +N ซ้อนกัน)
     */
    private static String resolveBaseName(ItemStack left, UpgradeData current) {
        String base;
        if (current.level() > 0) base = current.baseName();
        else if (left.has(DataComponents.CUSTOM_NAME)) base = left.getHoverName().getString();
        else return "";
        base = stripSuffix(base);
        String defaultName = left.getItem().getName(left).getString();
        return base.equals(defaultName) ? "" : base;
    }

    private static String stripSuffix(String name) {
        return name.replaceAll("(\\s*\\+\\d+(\\s+MAX)?)+$", "").trim();
    }
}
