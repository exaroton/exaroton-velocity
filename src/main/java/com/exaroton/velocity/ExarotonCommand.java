package com.exaroton.velocity;

import com.exaroton.velocity.subcommands.RestartServer;
import com.exaroton.velocity.subcommands.StartServer;
import com.exaroton.velocity.subcommands.StopServer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.stream.Collectors;

public class ExarotonCommand implements SimpleCommand {

    /**
     * exaroton plugin
     */
    private final ExarotonPlugin plugin;

    /**
     * task scheduler
     */
    private final Scheduler taskScheduler;

    /**
     * registered sub-commands
     * name -> command
     */
    private final HashMap<String, SubCommand> subCommands = new HashMap<>();

    public ExarotonCommand(ExarotonPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Invalid plugin");
        }
        this.plugin = plugin;
        this.loadCommands();
        this.taskScheduler = plugin.getProxy().getScheduler();
    }

    /**
     * register all sub-commands
     */
    private void loadCommands() {
        this.registerCommand(new StartServer(plugin));
        this.registerCommand(new StopServer(plugin));
        this.registerCommand(new RestartServer(plugin));
    }

    /**
     * register a sub command
     * @param subCommand sub command
     */
    private void registerCommand(SubCommand subCommand) {
        this.subCommands.put(subCommand.getName(), subCommand);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sender.sendMessage(this.getUsage());
            return;
        }

        SubCommand command = this.subCommands.get(args[0]);
        if (command == null) {
            sender.sendMessage(Message.error("Unknown sub-command."));
            return;
        }

        if (command.getPermission() != null && !sender.hasPermission(command.getPermission())) {
            sender.sendMessage(Message.error("You don't have the required permissions to execute this command!"));
            return;
        }

        taskScheduler.buildTask(plugin, () -> command.execute(sender, Arrays.copyOfRange(args, 1, args.length))).schedule();
    }

    /**
     * get list of available subcommands
     * @return command usage
     */
    private Component getUsage() {
        return (subCommands.size() == 0 ? Message.error("No sub-commands registered.") :
                Message.subCommandList(subCommands.keySet().toArray(new String[1])));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return new ArrayList<>(subCommands.keySet());
        }
        else if (args.length == 1) {
            return subCommands
                    .keySet()
                    .stream()
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        else {
            SubCommand command = subCommands
                    .get(args[0]);
            if (command == null || !sender.hasPermission(command.getPermission())) return new ArrayList<>();
            return command
                    .onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
    }
}
