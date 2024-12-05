/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.commands;

import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;

import net.dries007.tfc.util.Helpers;

@SuppressWarnings("unused")
public final class TFCCommands
{
    private static final Component DISABLED = Component.translatable("tfc.commands.disabled_by_tfc");

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context)
    {
        // Register all new commands as sub commands of the `tfc` root
        dispatcher.register(Commands.literal("tfc")
            .then(ClearWorldCommand.create())
            .then(PlayerCommand.create())
            .then(CountBlockCommand.create(context))
            .then(PropickCommand.create())
            .then(AddTrimCommand.create(context))
        );

        // For command modifications / replacements, we register directly
        // First, remove the vanilla command by the same name
        // This seems to work. It does leave the command still lying around, but it shouldn't matter as we replace it anyway
        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("time") || node.getName().equals("weather"));
        dispatcher.register(TimeCommand.create());
        dispatcher.register(WeatherCommand.create());

        // Remove the NeoForge provided `/neoforge day` command, and replace with a very simple "this is disabled by TFC" message
        // We fold the functionality into `/time`
        dispatcher.getRoot()
            .getChild("neoforge")
            .getChildren()
            .removeIf(node -> node.getName().equals("day"));
        dispatcher.register(Commands.literal("neoforge")
            .then(Commands.literal("day")
                .executes(c -> { c.getSource().sendFailure(DISABLED); return 0; })));
    }

    public static <S extends SharedSuggestionProvider> Supplier<SuggestionProvider<S>> register(String id, SuggestionProvider<SharedSuggestionProvider> provider)
    {
        return Suppliers.memoize(() -> SuggestionProviders.register(Helpers.identifier(id), provider));
    }
}