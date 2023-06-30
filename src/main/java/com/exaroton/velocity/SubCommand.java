package com.exaroton.velocity;

import com.velocitypowered.api.command.CommandSource;
import org.slf4j.Logger;

import java.util.List;

public abstract class SubCommand {

    /**
     * sub-command name
     */
    private final String name;

    /**
     * sub-command description
     */
    private final String description;

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
    public SubCommand(String name, String description,  ExarotonPlugin plugin) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command name!");
        }
        this.name = name;

        if (description == null || description.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command description!");
        }
        this.description = description;

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
     * get the sub-command description
     *
     * @return sub-command description
     */
    public String getDescription() {
        return description;
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