package com.exaroton.velocity.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.velocity.ExarotonPlugin;
import com.exaroton.velocity.Message;
import com.exaroton.velocity.SubCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RemoveServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public RemoveServer(ExarotonPlugin plugin) {
        super("remove", "Remove a server from the network and stop watching for updates.", plugin);
    }

    @Override
    public void execute(CommandSource sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("remove"));
            return;
        }

        try {
            String name = args[0];
            Optional<RegisteredServer> velocityServer = plugin.getProxy().getServer(name);
            if (!velocityServer.isPresent()) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            Server server = plugin.findServer(name, false);
            plugin.stopListeningToStatus(server.getId());
            plugin.getProxy().unregisterServer(velocityServer.get().getServerInfo());
            sender.sendMessage(Message.removed(name).getComponent());
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSource sender, String[] args) {
        return plugin.getProxy().getAllServers().stream().map(s -> s.getServerInfo().getName()).collect(Collectors.toList());
    }

    @Override
    public String getPermission() {
        return "exaroton.remove";
    }
}
