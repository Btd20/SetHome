package org.sethomegui.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeAdminTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("sethome.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // /homeadmin <aquí>
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("gui");
            subCommands.add("import");
            subCommands.add("reload");
            subCommands.add("version");

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        // /homeadmin import <aquí>
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            List<String> sources = new ArrayList<>();
            sources.add("Essentials");
            sources.add("HuskHomes");

            StringUtil.copyPartialMatches(args[1], sources, completions);
            Collections.sort(completions);
            return completions;
        }

        return Collections.emptyList();
    }
}