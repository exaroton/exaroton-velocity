package com.exaroton.velocity.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.velocity.ExarotonPlugin;
import com.exaroton.velocity.Message;
import com.exaroton.velocity.ServerStatusListener;
import com.exaroton.velocity.SubCommand;
import com.velocitypowered.api.command.CommandSource;

import java.util.List;

public class StartServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public StartServer(ExarotonPlugin plugin) {
        super("start", "Start a server",  plugin);
    }

    @Override
    public void execute(CommandSource sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("start"));
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            if (!server.hasStatus(ServerStatus.OFFLINE, ServerStatus.CRASHED)) {
                sender.sendMessage(Message.SERVER_NOT_OFFLINE);
                return;
            }

            ServerStatusListener listener = plugin.listenToStatus(server, sender, null, plugin.findServerName(server.getAddress()), ServerStatus.ONLINE);
            server.start();
            sender.sendMessage(Message.action("Starting", listener.getName(server)));
        } catch (APIException e) {
            logger.error("An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        return plugin.serverCompletions(args[0], ServerStatus.OFFLINE);
    }

    @Override
    public String getPermission() {
        return "exaroton.start";
    }
}
