package com.exaroton.velocity;

import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.api.ws.subscriber.ServerStatusSubscriber;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusListener extends ServerStatusSubscriber {

    /**
     * bungee proxy
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

    public ServerStatusListener(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    public ServerStatusListener setServerInfo(ServerInfo serverInfo) {
        if (serverInfo != null) {
            this.serverInfo = serverInfo;
        }
        return this;
    }

    public ServerStatusListener setSender(CommandSource sender) {
        if (sender != null) {
            this.sender = sender;
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
        String serverName = this.serverInfo == null ? (this.name == null ? newServer.getName() : this.name) : this.serverInfo.getName();
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServer(serverName).isPresent()) {
                this.sendInfo("Server "+serverName+" already exists in bungee network", NamedTextColor.RED);
                return;
            }
            this.serverInfo = new ServerInfo(serverName, new InetSocketAddress(newServer.getHost(), newServer.getPort()));
            proxy.registerServer(this.serverInfo);
            this.sendInfo("[exaroton] " + newServer.getAddress() + " went online!", NamedTextColor.GREEN);
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.unregisterServer(this.serverInfo);
            this.sendInfo("[exaroton] " + newServer.getAddress() + " is no longer online!", NamedTextColor.RED);
        }
    }

    /**
     * send message to all subscribed sources
     * @param message message
     */
    public void sendInfo(String message, TextColor color) {
        Component text = Component.text(message).color(color);
        logger.log(Level.INFO, message);
        if (sender != null && !sender.equals(proxy.getConsoleCommandSource())) {
            sender.sendMessage(text);
            //unsubscribe user from further updates
            this.sender = null;
        }
    }
}
