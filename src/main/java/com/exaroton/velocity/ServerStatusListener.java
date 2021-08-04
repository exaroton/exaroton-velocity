package com.exaroton.velocity;

import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.api.ws.subscriber.ServerStatusSubscriber;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusListener extends ServerStatusSubscriber {

    private final ExarotonPlugin plugin;

    /**
     * velocity proxy
     */
    private final ProxyServer proxy;

    /**
     * logger
     */
    private final Logger logger;

    /**
     * optional command sender
     */
    private CommandSource sender;

    /**
     *
     */
    private ServerInfo serverInfo;

    /**
     * server name in proxy
     */
    private String name;

    /**
     * server status sender is waiting for
     */
    private int expectedStatus;

    public ServerStatusListener(ExarotonPlugin plugin) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();
    }

    public String getName(Server server) {
        return this.name != null ? this.name : server.getName();
    }

    public ServerStatusListener setServerInfo(ServerInfo serverInfo) {
        if (serverInfo != null) {
            this.serverInfo = serverInfo;
        }
        return this;
    }

    public ServerStatusListener setSender(CommandSource sender, int expectedStatus) {
        if (sender != null) {
            this.sender = sender;
            this.expectedStatus = expectedStatus;
        }
        return this;
    }

    public ServerStatusListener setName(String name) {
        if (name != null) {
            this.name = name;
        }
        return this;
    }

    @Override
    public void statusUpdate(Server oldServer, Server newServer) {
        plugin.updateServer(newServer);
        String serverName = this.serverInfo == null ? (this.name == null ? newServer.getName() : this.name) : this.serverInfo.getName();
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServer(serverName).isPresent()) {
                this.sendInfo(Message.error("Server "+serverName+" already exists in velocity network"), true);
                return;
            }
            this.serverInfo = plugin.constructServerInfo(serverName, newServer);
            proxy.registerServer(this.serverInfo);
            this.sendInfo(Message.statusChange(serverName, true), expectedStatus == ServerStatus.ONLINE);
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            Optional<RegisteredServer> registeredServer = this.proxy.getServer(serverName);
            if (!registeredServer.isPresent()) {
                this.sendInfo(Message.error("Server " + serverName + " is not registered in velocity network!"), true);
                return;
            }
            proxy.unregisterServer(registeredServer.get().getServerInfo());
            this.sendInfo(Message.statusChange(serverName, false), expectedStatus == ServerStatus.OFFLINE);
        }
    }

    /**
     * send message to all subscribed sources
     * @param message message
     */
    public void sendInfo(TextComponent message, boolean unsubscribe) {
        logger.log(Level.INFO, Message.getFullString(message));
        if (sender != null && !sender.equals(proxy.getConsoleCommandSource())) {
            sender.sendMessage(message);
            if (unsubscribe) {
                //unsubscribe user from further updates
                this.sender = null;
            }
        }
    }
}
