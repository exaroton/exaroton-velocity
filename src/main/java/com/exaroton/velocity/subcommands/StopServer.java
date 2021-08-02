package com.exaroton.velocity.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.velocity.ExarotonPlugin;
import com.exaroton.velocity.SubCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.logging.Level;

public class StopServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public StopServer(ExarotonPlugin plugin) {
        super("stop", plugin);
    }

    @Override
    public void execute(CommandSource sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /exaroton stop <server>").color(NamedTextColor.RED));
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Component.text("Server not found!").color(NamedTextColor.RED));
                return;
            }

            if (!server.hasStatus(ServerStatus.ONLINE)) {
                sender.sendMessage(Component.text("Server is not online").color(NamedTextColor.RED));
                return;
            }

            plugin.listenToStatus(server, sender, null, plugin.findServerName(server.getAddress()));
            server.stop();
            sender.sendMessage(Component.text("Stopping server...").color(NamedTextColor.WHITE));
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Component.text("An API Error occurred. Check your log for more Info!").color(NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        return plugin.serverCompletions(args[0], ServerStatus.ONLINE);
    }

    @Override
    public String getPermission() {
        return "exaroton.stop";
    }
}
