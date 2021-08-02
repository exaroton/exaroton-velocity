package com.exaroton.velocity;

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
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Plugin(id = "exaroton", name = "exaroton", version = "1.0.0", description = "Manage exaroton servers in your bungee proxy", authors = {"Aternos GmbH"})
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
            this.runAsyncTasks();
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
     * @param force skip cache
     * @return found server or null
     * @throws APIException exceptions from the API
     */
    public Server findServer(String query, boolean force) throws APIException {
        Server[] servers = serverCache != null && !force ? serverCache : fetchServers();

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
     * @param name server name
     * @param expectedStatus expected server staus
     */
    public ServerStatusListener listenToStatus(Server server, CommandSource sender, ServerInfo info, String name, int expectedStatus) {
        if (statusListeners.containsKey(server.getId())) {
            return statusListeners.get(server.getId())
                    .setSender(sender, expectedStatus)
                    .setServerInfo(info)
                    .setName(name);
        }
        server.subscribe();
        ServerStatusListener listener = new ServerStatusListener(this)
                .setSender(sender, expectedStatus)
                .setServerInfo(info)
                .setName(name);
        server.addStatusSubscriber(listener);
        statusListeners.put(server.getId(), listener);
        return listener;
    }

    /**
     * start watching servers in the bungee config
     */
    public void runAsyncTasks() {
        this.getProxy().getScheduler().buildTask(this, () -> {
            if (config.getBoolean("watch-servers")) {
                this.watchServers();
            }
            this.autoStartServers();
        }).schedule();
    }

    /**
     * watch servers in the bungee config
     */
    public void watchServers(){
        for (RegisteredServer registeredServer: proxy.getAllServers()) {
            String address = registeredServer.getServerInfo().getAddress().getHostName();
            if (address.matches(".*\\.exaroton\\.me(:\\d+)?")) {
                logger.info("Found exaroton server: " + address + ", start watching status changes");
                try {
                    Server server = this.findServer(address, false);
                    if (server.hasStatus(ServerStatus.ONLINE)) {
                        proxy.unregisterServer(registeredServer.getServerInfo());
                        proxy.registerServer(constructServerInfo(registeredServer.getServerInfo().getName(), server));
                    } else {
                        proxy.unregisterServer(registeredServer.getServerInfo());
                        logger.info("Server " + address + " is offline, removed it from the server list!");
                    }
                    this.listenToStatus(server, null, registeredServer.getServerInfo(), null, -1);
                } catch (APIException e) {
                    logger.log(Level.SEVERE, "Failed to access API, not watching "+address, e);
                }
            }
        }
    }

    /**
     * generate server info
     * @param name server name
     * @param server exaroton server
     * @return server info
     */
    public ServerInfo constructServerInfo(String name, Server server) {
        return new ServerInfo(name, new InetSocketAddress(server.getHost(), server.getPort()));
    }

    /**
     * automatically start servers from the config (asynchronous)
     */
    public void autoStartServers() {
        if (!config.getBoolean("auto-start.enabled", false)) return;
        for (String query: config.<String>getList("auto-start.servers")) {
            try {
                Server server = this.findServer(query, false);

                if (server == null) {
                    logger.log(Level.WARNING, "Can't start " + query + ": Server not found");
                    continue;
                }

                if (server.hasStatus(ServerStatus.ONLINE)) {
                    String name = findServerName(server.getAddress());
                    if (name == null) {
                        logger.log(Level.INFO, server.getAddress() + " is already online, adding it to proxy!");
                        this.getProxy().registerServer(this.constructServerInfo(server.getName(), server));
                    } else {
                        logger.log(Level.INFO, server.getAddress() + " is already online!");
                    }
                    this.listenToStatus(server, null, null, name, -1);
                    return;
                }

                if (server.hasStatus(new int[]{ServerStatus.STARTING,
                        ServerStatus.LOADING, ServerStatus.PREPARING, ServerStatus.RESTARTING})) {
                    logger.log(Level.INFO, server.getAddress() + " is already starting!");
                    this.listenToStatus(server, null, null, findServerName(server.getAddress()), -1);
                    return;
                }

                if (!server.hasStatus(ServerStatus.OFFLINE)) {
                    logger.log(Level.WARNING, "Can't start " + server.getAddress() + ": Server isn't offline.");
                    continue;
                }

                logger.log(Level.INFO, "Starting "+ server.getAddress());
                this.listenToStatus(server, null, null, findServerName(server.getAddress()), -1);
                server.start();

            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to start start "+ query +"!", e);
            }
        }
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

    /**
     * try to find the server name in the velocity config
     * @param address exaroton address e.g. example.exaroton.com
     * @return server name e.g. lobby
     */
    public String findServerName(String address) {
        for (Map.Entry<String, String> entry : proxy.getConfiguration().getServers().entrySet()) {
            if (entry.getValue().matches(Pattern.quote(address) + ":\\d+")) {
                return entry.getKey();
            }
        }
        return null;
    }
}
