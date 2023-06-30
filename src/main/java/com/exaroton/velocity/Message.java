package com.exaroton.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Collection;
import java.util.Iterator;

import static net.kyori.adventure.text.Component.text;

public class Message {

    public static final Component SERVER_NOT_FOUND = Message.error("Server wasn't found.");

    public static final Component SERVER_NOT_ONLINE = Message.error("Server isn't online.");

    public static final Component SERVER_NOT_OFFLINE = Message.error("Server isn't offline.");

    public static final Component API_ERROR = Message.error("An API Error occurred. Check your log for details!");

    public static final Component NOT_PLAYER = Message.error("This command can only be executed by players!");

    private static TextComponent.Builder prefix() {
        return text()
                .content("[")
                .color(NamedTextColor.GRAY)
                .append(text("exaroton", NamedTextColor.GREEN))
                .append(text("] ", NamedTextColor.GRAY));
    }

    /**
     * show command usage
     * @param command command name
     */
    public static Component usage(String command) {
        return prefix()
                .append(text("Usage: /exaroton " + command)
                .append(text(" <server> ", NamedTextColor.GREEN)))
                .build();
    }

    /**
     * @param message error message
     */
    public static Component error(String message) {
        return prefix()
                .append(text(message, NamedTextColor.RED))
                .build();
    }

    /**
     * show that an action is being executed
     * @param action action name (e.g. "Starting")
     * @param name server name
     */
    public static Component action(String action, String name) {
        return prefix()
                .append(text(action + " server "))
                .append(text(name, NamedTextColor.GREEN))
                .append(text(".", NamedTextColor.GRAY))
                .build();
    }

    /**
     * show that a server has been added to the proxy
     * @param name server name
     */
    public static Component added(String name) {
        return prefix()
                .append(text("Added server "))
                .append(text(name, NamedTextColor.GREEN))
                .append(text(" to the proxy."))
                .build();
    }

    /**
     * show that a server has been removed from the proxy
     * @param name server name
     */
    public static Component removed(String name) {
        return prefix()
                .append(text("Removed server "))
                .append(text(name, NamedTextColor.GREEN))
                .append(text(" from the proxy. No longer watching status updates."))
                .build();
    }

    /**
     * show that a server's status is being watched
     * @param name server name
     */
    public static Component watching(String name) {
        return prefix()
                .append(text("Watching status updates for "))
                .append(text(name, NamedTextColor.GREEN))
                .append(text("."))
                .build();
    }

    /**
     * @param name server name
     * @param online is server online
     */
    public static TextComponent statusChange(String name, boolean online) {
        return prefix()
                .append(text("Server "))
                .append(text(name, NamedTextColor.GREEN))
                .append(text(" went "))
                .append(text(online ? "online" : "offline", online ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(text("."))
                .build();
    }

    /**
     * list sub-commands
     * @param subcommands sub-command names
     */
    public static Component subCommandList(Collection<SubCommand> subcommands) {
        final Iterator<SubCommand> iterator = subcommands.iterator();
        final TextComponent.Builder text = prefix()
                .append(text("Available sub-commands:", NamedTextColor.GRAY))
                .append(Component.newline());
        while (iterator.hasNext()) {
            final SubCommand subCommand = iterator.next();
            text.append(
                    text("- ", NamedTextColor.GRAY),
                    text(subCommand.getName(), NamedTextColor.GREEN),
                    text(": ", NamedTextColor.GREEN),
                    text(subCommand.getDescription())
            );

            if (iterator.hasNext()) {
                text.append(Component.newline());
            }
        }
        return text.build();
    }

    public static String getFullString(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * show that this player is being moved to a server
     * @param serverName server name in network
     * @return message
     */
    public static Component switching(String serverName) {
        return prefix()
                .append(text("Switching to "))
                .append(text(serverName, NamedTextColor.GREEN), text("..."))
                .build();
    }
}

