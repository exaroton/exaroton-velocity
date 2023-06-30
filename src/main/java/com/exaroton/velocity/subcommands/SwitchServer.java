package com.exaroton.velocity.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.velocity.ExarotonPlugin;
import com.exaroton.velocity.ExarotonPluginAPI;
import com.exaroton.velocity.Message;
import com.exaroton.velocity.SubCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import java.util.List;

public class SwitchServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public SwitchServer(ExarotonPlugin plugin) {
        super("switch", "Switch to a server and start it if necessary", plugin);
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (args.length != 1) {
            source.sendMessage(Message.usage("switch"));
            return;
        }

        try {
            if (!(source instanceof Player)) {
                source.sendMessage(Message.NOT_PLAYER);
                return;
            }

            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                source.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            source.sendMessage(Message.switching(plugin.findServerName(server.getAddress(), server.getName())));
            ExarotonPluginAPI.switchServer((Player) source, server);
        } catch (APIException e) {
            logger.error("An API Error occurred!", e);
            source.sendMessage(Message.API_ERROR);
        } catch (RuntimeException | InterruptedException e) {
            logger.error("Failed to execute switch command", e);
            source.sendMessage(Message.error("Failed to execute switch command. Check your console for details."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource source, String[] args) {
        return plugin.serverCompletions(args[0], null);
    }

    @Override
    public String getPermission() {
        return "exaroton.switch";
    }
}

