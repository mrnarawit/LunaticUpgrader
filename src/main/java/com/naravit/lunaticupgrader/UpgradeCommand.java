package com.naravit.lunaticupgrader;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /lunaticupgrader set <0-10>  ตั้งขั้นตีบวกของไอเทมในมือหลัก (0 = ล้าง)
 * /lunaticupgrader get         ดูขั้นของไอเทมในมือหลัก
 * ต้องเป็น OP (permission level 2) หรือเปิด cheats
 */
public final class UpgradeCommand {
    private UpgradeCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(LunaticUpgrader.MODID)
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, LunaticUpgrader.MAX_LEVEL))
                                .executes(UpgradeCommand::set)))
                .then(Commands.literal("get")
                        .executes(UpgradeCommand::get)));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        int level = IntegerArgumentType.getInteger(ctx, "level");

        if (stack.isEmpty() || !UpgradeEvents.isUpgradeable(stack)) {
            ctx.getSource().sendFailure(Component.translatable("lunaticupgrader.command.not_upgradeable"));
            return 0;
        }
        UpgradeEvents.setLevel(stack, level);
        player.getInventory().setChanged();
        ctx.getSource().sendSuccess(() -> Component.translatable("lunaticupgrader.command.set",
                stack.getHoverName(), level), true);
        return level;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        int level = stack.getOrDefault(LunaticUpgrader.UPGRADE.get(), UpgradeData.NONE).level();
        ctx.getSource().sendSuccess(() -> Component.translatable("lunaticupgrader.command.get",
                stack.getHoverName(), level, LunaticUpgrader.MAX_LEVEL), false);
        return level;
    }
}
