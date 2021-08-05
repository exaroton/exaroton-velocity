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
import java.util.logging.Level;

public class AddServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public AddServer(ExarotonPlugin plugin) {
        super("add", plugin);
    }

    @Override
    public void execute(CommandSource sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("add"));
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            ServerStatusListener listener = plugin.listenToStatus(server, sender, null, plugin.findServerName(server.getAddress()), ServerStatus.ONLINE);
            String name = listener.getName(server);
            sender.sendMessage(Message.watching(name).getComponent());

            if (server.hasStatus(ServerStatus.ONLINE)) {
                if (plugin.getProxy().getServer(name).isPresent()) {
                    sender.sendMessage(Message.error("Failed to add server: A server with the name " + name + " already exists in proxy."));
                }
                else {
                    plugin.getProxy().registerServer(plugin.constructServerInfo(name, server));
                    sender.sendMessage(Message.added(name).getComponent());
                }
            }
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        return plugin.serverCompletionsNotInProxy(args[0]);
    }

    @Override
    public String getPermission() {
        return "exaroton.add";
    }
}
