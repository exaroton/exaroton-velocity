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
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        ExarotonPluginAPI.setPlugin(this);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        if (this.config != null && this.createExarotonClient()) {
            this.registerCommands();
            this.runAsyncTasks();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.exarotonClient != null) {
            this.autoStopServers();
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
        if (this.getProxy().getConfiguration().getServers().containsKey(query)) {
            query = this.getProxy().getConfiguration().getServers().get(query);
        }
        final String finalQuery = query;
        Server[] servers = serverCache != null && !force ? serverCache : fetchServers();

        servers = Arrays.stream(servers)
                .filter(server -> matchExact(server, finalQuery))
                .toArray(Server[]::new);

        switch (servers.length) {
            case 0:
                return null;

            case 1:
                return servers[0];

            default:
                Optional<Server> server = Arrays.stream(servers).filter(s -> s.getId().equals(finalQuery)).findFirst();
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
     * @return get server cache (request if necessary)
     */
    public Server[] getServerCache() throws APIException {
        if (serverCache == null) {
            this.fetchServers();
        }
        return serverCache;
    }

    /**
     * @param servers server list
     * @param status status code
     * @return severs that have the requested status
     */
    public Stream<Server> findWithStatus(Stream<Server> servers, int status) {
        return servers.filter(server -> server.hasStatus(status));
    }

    public Stream<Server> findWithQuery(Stream<Server> servers, String query) {
        return servers.filter(server -> matchBeginning(server, query));
    }

    public List<String> getAllNames(Server[] servers) {
        List<String> result = new ArrayList<>();

        for (Server server : servers) {
            result.add(server.getName());
        }
        for (Server server : servers) {
            result.add(server.getAddress());
        }
        for (Server server : servers) {
            result.add(server.getId());
        }

        return result;
    }

    /**
     * find auto completions by a query and status
     * @param query partial server name, address or ID
     * @param status server status
     * @return all matching server names, addresses and IDs
     */
    public List<String> serverCompletions(String query, Integer status) {
        Stream<Server> servers;
        try {
            servers = Arrays.stream(getServerCache());
        }
        catch (APIException exception) {
            logger.log(Level.SEVERE, "Failed to access API", exception);
            return new ArrayList<>();
        }
        if (status != null)
            servers = findWithStatus(servers, status);
        servers = findWithQuery(servers, query);
        Server[] matching = servers.toArray(Server[]::new);

        List<String> result = this.getProxy().getAllServers().stream()
                .map(s -> s.getServerInfo().getName())
                .filter(s -> s.startsWith(query))
                .collect(Collectors.toList());

        result.addAll(getAllNames(matching));

        return result;
    }

    public List<String> serverCompletionsNotInProxy(String query) {
        Stream<Server> servers;
        try {
            servers = Arrays.stream(getServerCache());
        } catch (APIException exception) {
            logger.log(Level.SEVERE, "Failed to access API", exception);
            return new ArrayList<>();
        }
        servers = findWithQuery(servers, query);
        servers = servers.filter(s -> {
            String name = findServerName(s.getAddress(), s.getName());
            return !this.getProxy().getServer(name).isPresent();
        });

        return getAllNames(servers.toArray(Server[]::new));
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server         server to subscribe to
     * @param name           server name in bungee server list
     */
    public ServerStatusListener listenToStatus(Server server, ServerInfo info, String name) {
        return this.listenToStatus(server, null, info , name, -1);
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
        ServerStatusListener listener = new ServerStatusListener(this, server)
                .setSender(sender, expectedStatus)
                .setServerInfo(info)
                .setName(name);
        server.addStatusSubscriber(listener);
        statusListeners.put(server.getId(), listener);
        return listener;
    }

    /**
     * stop listening to server status
     * @param serverId ID of the server to unsubscribe from
     */
    public void stopListeningToStatus(String serverId) {
        if (!this.statusListeners.containsKey(serverId)) {
            return;
        }
        this.statusListeners.get(serverId).unsubscribe();
        this.statusListeners.remove(serverId);
    }

    /**
     * start autostart and watch servers
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
     * watch servers in the velocity config
     */
    public void watchServers(){
        for (RegisteredServer registeredServer: proxy.getAllServers()) {
            String address = registeredServer.getServerInfo().getAddress().getHostName();
            if (address.matches(".*\\.exaroton\\.me(:\\d+)?$")) {
                address = address.replaceAll(":\\d+$", "");
                try {
                    Server server = this.findServer(address, false);
                    if (server == null) {
                        logger.warning("Can't find server " + address + ". Unable to watch status changes");
                        return;
                    }
                    logger.info("Found exaroton server: " + address + ". Starting to watch status changes");
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
                    String name = findServerName(server.getAddress(), server.getName());
                    if (name == null) {
                        logger.log(Level.INFO, server.getAddress() + " is already online, adding it to proxy!");
                        this.getProxy().registerServer(this.constructServerInfo(server.getName(), server));
                    } else {
                        logger.log(Level.INFO, server.getAddress() + " is already online!");
                    }
                    this.listenToStatus(server, null, null, name, -1);
                    continue;
                }

                if (server.hasStatus(new int[]{ServerStatus.STARTING,
                        ServerStatus.LOADING, ServerStatus.PREPARING, ServerStatus.RESTARTING})) {
                    logger.log(Level.INFO, server.getAddress() + " is already starting!");
                    this.listenToStatus(server, null, null, findServerName(server.getAddress()), -1);
                    continue;
                }

                if (!server.hasStatus(ServerStatus.OFFLINE)) {
                    logger.log(Level.WARNING, "Can't start " + server.getAddress() + ": Server isn't offline.");
                    continue;
                }

                logger.log(Level.INFO, "Starting "+ server.getAddress());
                this.listenToStatus(server, null, null, findServerName(server.getAddress()), -1);
                server.start();

            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to start "+ query +"!", e);
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
        return findServerName(address, null);
    }

    /**
     * try to find the server name in the velocity config
     * @param address exaroton address e.g. example.exaroton.com
     * @param fallback fallback name e.g. example
     * @return server name e.g. lobby
     */
    public String findServerName(String address, String fallback) {
        for (Map.Entry<String, String> entry : proxy.getConfiguration().getServers().entrySet()) {
            if (entry.getValue().matches(Pattern.quote(address) + "(:\\d+)?")) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    public void updateServer(Server server) {
        if (serverCache == null) return;
        int index = 0;
        for (; index < serverCache.length; index++) {
            if (serverCache[index].getId().equals(server.getId())) break;
        }
        if (index < serverCache.length) {
            serverCache[index] = server;
        }
    }

    /**
     * automatically stop servers from the config
     */
    public void autoStopServers() {
        if (!config.getBoolean("auto-stop.enabled")) return;

        ExecutorService executor = Executors.newCachedThreadPool();
        ArrayList<Callable<Object>> stopping = new ArrayList<>();

        for (String query : config.<String>getList("auto-stop.servers")) {
            try {
                Server server = this.findServer(query, false);

                if (server == null) {
                    logger.log(Level.WARNING, "Can't stop " + query + ": Server not found");
                    continue;
                }

                String name = findServerName(server.getAddress(), server.getName());
                if (server.hasStatus(new int[]{ServerStatus.OFFLINE, ServerStatus.CRASHED})) {
                    logger.log(Level.INFO, name + " is already offline!");
                    continue;
                }

                if (server.hasStatus(new int[]{ServerStatus.SAVING, ServerStatus.STOPPING})) {
                    logger.log(Level.INFO, name + " is already stopping!");
                    continue;
                }

                if (!server.hasStatus(ServerStatus.ONLINE)) {
                    logger.log(Level.SEVERE, "Can't stop " + name + ": Server isn't online.");
                    continue;
                }

                logger.log(Level.INFO, "Stopping " + name);
                stopping.add(() -> {
                    server.stop();
                    return null;
                });
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to stop " + query + "!", e);
            }
        }
        if (stopping.size() == 0)
            return;

        try {
            executor.invokeAll(stopping);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to stop servers", e);
            return;
        }

        int count = stopping.size();
        logger.info("Successfully stopped " + count + " server" + (count == 1 ? "" : "s") + "!");
    }
}
