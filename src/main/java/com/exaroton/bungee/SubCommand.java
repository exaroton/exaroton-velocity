package com.exaroton.bungee;

import com.velocitypowered.api.command.CommandSource;

import java.util.List;
import java.util.logging.Logger;

public abstract class SubCommand {

    /**
     * sub-command name
     */
    private final String name;

    /**
     * plugin
     */
    protected final ExarotonPlugin plugin;

    /**
     * logger
     */
    protected final Logger logger;

    /**
     * @param plugin exaroton plugin
     */
    public SubCommand(String name, ExarotonPlugin plugin) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command name!");
        }
        this.name = name;
        if (plugin == null) {
            throw new IllegalArgumentException("Invalid plugin!");
        }
        this.plugin = plugin;
        logger = plugin.getLogger();
    }

    /**
     * get the sub-command name (e.g. "start")
     *
     * @return sub-command name
     */
    public String getName() {
        return name;
    }

    /**
     * execute command
     *
     * @param sender command sender
     * @param args   command arguments
     */
    public abstract void execute(CommandSource sender, String[] args);

    /**
     * get the required permission node
     *
     * @return permission node or null
     */
    public String getPermission() {
        return null;
    }

    /**
     * suggest possible options for tab completion
     * @param sender command sender
     * @param args arguments
     * @return
     */
    public abstract List<String> onTabComplete(CommandSource sender, String[] args);

}