package com.exaroton.bungee;

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

public class ServerStatusListener extends ServerStatusSubscriber {

    /**
     * bungee proxy
     */
    private final ProxyServer proxy;

    /**
     * optional command sender
     */
    private CommandSource sender;

    /**
     *
     */
    private ServerInfo serverInfo;

    public ServerStatusListener(ProxyServer proxy) {
        this.proxy = proxy;
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

    @Override
    public void statusUpdate(Server oldServer, Server newServer) {
        String serverName = this.serverInfo == null ? newServer.getName() : this.serverInfo.getName();
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServer(serverName).isPresent()) {
                this.sendInfo("Server "+serverName+" already exists in bungee network", NamedTextColor.RED);
                return;
            }
            serverInfo = new ServerInfo(serverName, new InetSocketAddress(newServer.getAddress(), newServer.getPort()));
            proxy.registerServer(serverInfo);
            this.sendInfo("[exaroton] " + newServer.getAddress() + " went online!", NamedTextColor.GREEN);
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.unregisterServer(serverInfo);
            this.sendInfo("[exaroton] " + newServer.getAddress() + " is no longer online!", NamedTextColor.RED);
        }
    }

    /**
     * send message to all subscribed sources
     * @param message message
     */
    public void sendInfo(String message, TextColor color) {
        Component text = Component.text(message).color(color);
        proxy.getConsoleCommandSource().sendMessage(text);
        if (sender != null) {
            sender.sendMessage(text);
            //unsubscribe user from further updates
            this.sender = null;
        }
    }
}
