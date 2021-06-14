package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.ExarotonClient;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(id = "exaroton", name = "exaroton", version = "1.0.0", description = "Manage exaroton servers in your bungee proxy")
public class ExarotonPlugin {

    /**
     * exaroton API client
     */
    private ExarotonClient exarotonClient;

    /**
     * main configuration (config.toml)
     */
    private Toml config;

    /**
     * Proxy server
     */
    private final ProxyServer proxy;

    /**
     * logger
     */
    private final Logger logger;

    /**
     * server cache
     */
    private Server[] serverCache;

    /**
     * server status listeners
     * serverid -> status listener
     */
    private final HashMap<String, ServerStatusListener> statusListeners = new HashMap<>();

    @Inject
    public ExarotonPlugin(ProxyServer proxy, Logger logger, @DataDirectory final Path folder) {
        this.proxy = proxy;
        this.logger = logger;
        try {
            this.loadConfig(folder.toFile());
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to load config file!", e);
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        if (this.config != null && this.createExarotonClient()) {
            this.registerCommands();
            this.startWatchingServers();
            this.autoStartServers();
        }
    }

    /**
     * load and/or create config.toml
     * @return configuration file
     * @throws IOException exception loading config
     */
    private File getConfigFile(File folder) throws IOException {
        folder.mkdir();
        File configFile = new File(folder, "config.toml");
        if (!configFile.exists()) {
            InputStream in = getClass().getResourceAsStream("config.toml");
            if (in != null) {
                Files.copy(in, configFile.toPath());
            }
            else {
                configFile.createNewFile();
            }
        }
        return configFile;
    }

    /**
     * load main configuration
     * @throws IOException exception loading config
     */
    private void loadConfig(File folder) throws IOException {
        File configFile = this.getConfigFile(folder);
        this.config = new Toml().read(configFile);
        InputStream in = getClass().getResourceAsStream("/config.toml");
        Toml defaultConfig = new Toml().read(in);
        this.config = this.addDefaults(this.config, defaultConfig);
        new TomlWriter().write(this.config.toMap(), configFile);
    }

    /**
     * update config recursively
     * @param config current configuration
     * @param defaults defaults
     * @return config with defaults
     */
    private Toml addDefaults(Toml config, Toml defaults) {
        if (config == null) return defaults;

        Map<String, Object> data = config.toMap();
        for (Map.Entry<String, Object> entry: defaults.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Toml) {
                data.put(key, addDefaults(config.getTable(key), defaults.getTable(key)).toMap());
            }
            else if (!config.contains(key)) {
                data.put(key, value);
            }
        }
        return new Toml().read(new TomlWriter().write(data));
    }

    /**
     * create exaroton client
     * @return was the client successfully created
     */
    public boolean createExarotonClient() {
        String apiToken = this.config.getString("apiToken");
        if (apiToken == null || apiToken.length() == 0 || apiToken.equals("example-token")) {
            logger.log(Level.SEVERE, "Invalid API Token specified!");
            return false;
        }
        else {
            this.exarotonClient = new ExarotonClient(apiToken);
            return true;
        }
    }

    /**
     * register commands
     */
    private void registerCommands() {
        CommandManager commandManager = proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder("exaroton").build(), new ExarotonCommand(this));
    }

    /**
     * update server cache to provided servers
     * @throws APIException API exceptions
     * @return exaroton servers
     */
    public Server[] fetchServers() throws APIException {
        this.getProxy().getScheduler().buildTask(this, () -> this.serverCache = null).delay(1, TimeUnit.MINUTES).schedule();
        return this.serverCache = exarotonClient.getServers();
    }

    /**
     * find a server
     * if a server can't be uniquely identified then the id will be preferred
     * @param query server name, address or id
     * @return found server or null
     * @throws APIException exceptions from the API
     */
    public Server findServer(String query) throws APIException {
        Server[] servers = fetchServers();

        servers = Arrays.stream(servers)
                .filter(server -> matchExact(server, query))
                .toArray(Server[]::new);

        switch (servers.length) {
            case 0:
                return null;

            case 1:
                return servers[0];

            default:
                Optional<Server> server = Arrays.stream(servers).filter(s -> s.getId().equals(query)).findFirst();
                return server.orElse(servers[0]);
        }
    }

    /**
     * does this server match the query exactly
     * @param server exaroton server
     * @param query server name, address or id
     * @return does the server match exactly
     */
    public boolean matchExact(Server server, String query) {
        return server.getAddress().equals(query) || server.getName().equals(query) || server.getId().equals(query);
    }

    /**
     * does this server start with the query
     * @param server exaroton server
     * @param query partial server name, address or id
     * @return does the server start with the query
     */
    public boolean matchBeginning(Server server, String query) {
        return server.getAddress().startsWith(query) || server.getName().startsWith(query) || server.getId().startsWith(query);
    }

    /**
     * find auto completions by a query and status
     * @param query partial server name, address or ID
     * @param status server status
     * @return all matching server names, addresses and IDs
     */
    public List<String> serverCompletions(String query, int status) {
        if (serverCache == null) {
            try {
                this.fetchServers();
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to load completions", e);
                return new ArrayList<>();
            }
        }
        Server[] matching = Arrays.stream(serverCache).filter(server -> matchBeginning(server, query) && server.hasStatus(status)).toArray(Server[]::new);
        ArrayList<String> result = new ArrayList<>();

        for (Server server: matching) {
            result.add(server.getName());
        }
        for (Server server: matching) {
            result.add(server.getAddress());
        }
        for (Server server: matching) {
            result.add(server.getId());
        }

        return result;
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server server to subscribe to
     * @param sender command sender to update
     * @param info velocity server info
     */
    public void listenToStatus(Server server, CommandSource sender, ServerInfo info) {
        if (statusListeners.containsKey(server.getId())) {
            statusListeners.get(server.getId())
                    .setSender(sender)
                    .setServerInfo(info);
            return;
        }
        server.subscribe();
        ServerStatusListener listener = new ServerStatusListener(this.getProxy())
                .setSender(sender)
                .setServerInfo(info);
        server.addStatusSubscriber(listener);
        statusListeners.put(server.getId(), listener);
    }

    /**
     * start watching servers in the bungee config
     */
    public void startWatchingServers() {
        if (config.getBoolean("watch-servers")) {
            this.getProxy().getScheduler().buildTask(this, () -> {
                this.watchServers();
            }).schedule();
        }
    }

    /**
     * watch servers in the bungee config
     */
    public void watchServers(){
        for (RegisteredServer registeredServer: proxy.getAllServers()) {
            String address = registeredServer.getServerInfo().getAddress().getHostName();
            address = address.replaceAll(":\\d+$", "");
            if (address.endsWith(".exaroton.me")) {
                logger.info("Found exaroton server: " + address + ", start watching status changes");
                try {
                    Server server = this.findServer(address);
                    if (!server.hasStatus(ServerStatus.ONLINE)) {
                        proxy.unregisterServer(registeredServer.getServerInfo());
                        logger.info("Server " + address + " is offline, removed it from the server list!");
                    }
                    this.listenToStatus(server, null, registeredServer.getServerInfo());
                } catch (APIException e) {
                    logger.log(Level.SEVERE, "Failed to access API, not watching "+address, e);
                }
            }
        }
    }

    /**
     * automatically start servers from the config (asynchronous)
     */
    public void autoStartServers() {
        if (!config.getBoolean("auto-start.enabled", false)) return;
        this.getProxy().getScheduler().buildTask(this, () -> {
            for (String query: config.<String>getList("auto-start.servers")) {
                try {
                    Server server = this.findServer(query);

                    if (server == null) {
                        logger.log(Level.WARNING, "Can't start " + query + ": Server not found");
                        continue;
                    }

                    if (server.hasStatus(new int[]{ServerStatus.ONLINE, ServerStatus.STARTING,
                            ServerStatus.LOADING, ServerStatus.PREPARING, ServerStatus.RESTARTING})) {
                        logger.log(Level.INFO, server.getAddress() + " is already online or starting!");
                        return;
                    }

                    if (!server.hasStatus(ServerStatus.OFFLINE)) {
                        logger.log(Level.WARNING, "Can't start " + server.getAddress() + ": Server isn't offline.");
                        continue;
                    }

                    logger.log(Level.INFO, "Starting "+ server.getAddress());
                    this.listenToStatus(server, null, null);
                    server.start();

                } catch (APIException e) {
                    logger.log(Level.SEVERE, "Failed to start start "+ query +"!", e);
                }
            }
        }).schedule();
    }

    /**
     * @return velocity proxy
     */
    public ProxyServer getProxy() {
        return proxy;
    }

    /**
     * @return logger
     */
    public Logger getLogger() {
        return logger;
    }
}
