package com.exaroton.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class Message {

    public static final TextComponent prefix =
            Component.text("[").color(NamedTextColor.GRAY)
            .append(Component.text("exaroton").color(NamedTextColor.GREEN))
            .append(Component.text("] ").color(NamedTextColor.GRAY));

    public static final TextComponent SERVER_NOT_FOUND = Message.error("Server wasn't found.");

    public static final TextComponent SERVER_NOT_ONLINE = Message.error("Server isn't online.");

    public static final TextComponent SERVER_NOT_OFFLINE = Message.error("Server isn't offline.");

    public static final TextComponent API_ERROR = Message.error("An API Error occurred. Check your log for details!");

    public static final TextComponent NOT_PLAYER = Message.error("This command can only be executed by players!");

    private final TextComponent component;

    private Message(TextComponent message) {
        this.component = prefix
                .append(message);
    }

    /**
     * show command usage
     * @param command command name
     */
    public static TextComponent usage(String command) {
        return new Message(Component.text("Usage: " + "/exaroton " + command)
                .append(Component.text(" <server> ").color(NamedTextColor.GREEN))).getComponent();
    }

    /**
     * @param message error message
     */
    public static TextComponent error(String message) {
        return new Message(Component.text(message).color(NamedTextColor.RED)).getComponent();
    }

    /**
     * show that an action is being executed
     * @param action action name (e.g. "Starting")
     * @param name server name
     */
    public static TextComponent action(String action, String name) {
        return new Message(Component.text(action + " server ")
                        .append(Component.text(name).color(NamedTextColor.GREEN))
                        .append(Component.text(".").color(NamedTextColor.GRAY))).getComponent();
    }

    /**
     * show that a server has been added to the proxy
     * @param name server name
     */
    public static Message added(String name) {
        return new Message(Component.text("Added server ")
                .append(Component.text(name).color(NamedTextColor.GREEN))
                .append(Component.text(" to the proxy."))
        );
    }

    /**
     * show that a server has been removed from the proxy
     * @param name server name
     */
    public static Message removed(String name) {
        return new Message(Component.text("Removed server ")
                .append(Component.text(name).color(NamedTextColor.GREEN))
                .append(Component.text(" from the proxy. No longer watching status updates."))
        );
    }

    /**
     * show that a server's status is being watched
     * @param name server name
     */
    public static Message watching(String name) {
        return new Message(Component.text("Watching status updates for ")
                .append(Component.text(name).color(NamedTextColor.GREEN))
                .append(Component.text("."))
        );
    }

    /**
     * @param name server name
     * @param online is server online
     */
    public static TextComponent statusChange(String name, boolean online) {
        return new Message(Component.text("Server ")
                .append(Component.text(name).color(NamedTextColor.GREEN))
                .append(Component.text(" went "))
                .append(Component.text(online ? "online" : "offline").color(online ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("."))).getComponent();
    }

    /**
     * list sub-commands
     * @param subcommands sub-command names
     */
    public static TextComponent subCommandList(Collection<SubCommand> subcommands) {
        Iterator<SubCommand> iterator = subcommands.iterator();
        TextComponent text = Component.text("Available sub-commands:\n").color(NamedTextColor.GRAY);
        while (iterator.hasNext()) {
            SubCommand subCommand = iterator.next();
            text = text
                    .append(Component.text("- ").color(NamedTextColor.GRAY))
                    .append(Component.text(subCommand.getName()).color(NamedTextColor.GREEN))
                    .append(Component.text(": ").color(NamedTextColor.GREEN))
                    .append(Component.text(subCommand.getDescription()));

            if (iterator.hasNext()) {
                text = text.append(Component.newline());
            }
        }
        return new Message(text).getComponent();
    }

    /**
     * @return velocity text component
     */
    public TextComponent getComponent() {
        return this.component;
    }

    public static String getFullString(TextComponent component) {
        StringBuilder content = new StringBuilder(component.content());
        for (Component c: component.children()) {
            content.append(getFullString((TextComponent) c));
        }
        return content.toString();
    }

    /**
     * show that this player is being moved to a server
     * @param serverName server name in network
     * @return message
     */
    public static Message switching(String serverName) {
        return new Message(Component.text("Switching to ")
                .append(Component.text(serverName).color(NamedTextColor.GREEN))
                .append(Component.text("...")));
    }
}

