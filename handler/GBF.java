package org.bunnys.handler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bunnys.handler.commands.message.config.MessageCommand;
import org.bunnys.handler.config.Config;
import org.bunnys.handler.utils.Logger;
import org.bunnys.handler.utils.handler.CommandManager;
import org.bunnys.handler.utils.handler.EventManager;
import org.bunnys.handler.utils.handler.ShutdownManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GBF Handler v4
 * Facade class coordinating bot initialization, command management, event registration, and shutdown for sharded bots.
 */
public class GBF {
    private static final int CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    // Thread factory for naming threads per shard
    private record GBFThreadFactory(int shardId) implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "gbf-shard-" + shardId + "-worker-" + threadCounter.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setDaemon(true);
            return thread;
        }
    }

    private final Config config;
    private final ShardManager shardManager;
    private final CommandManager commandManager;
    private final EventManager eventManager;
    private final ShutdownManager shutdownManager;
    private final List<ThreadPoolExecutor> shardThreadPools;
    private final long startTime = System.currentTimeMillis();

    public GBF(Config config) {
        if (config == null) throw new IllegalArgumentException("Config cannot be null");
        this.config = config;

        // Initialize shard-specific thread pools
        this.shardThreadPools = new ArrayList<>();
        int totalShards = config.getTotalShards(1);
        for (int i = 0; i < totalShards; i++) {
            final int shardId = i; // Create a final copy of i for the lambda
            ThreadPoolExecutor shardPool = new ThreadPoolExecutor(
                    CORE_POOL_SIZE,
                    MAX_POOL_SIZE,
                    KEEP_ALIVE_TIME,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(100),
                    new GBFThreadFactory(shardId),
                    (r, executor) -> Logger.error("Task rejected in shard " + shardId + " thread pool: " + r)
            );
            shardThreadPools.add(shardPool);
        }

        // Initialize ShardManager
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(config.token());
        builder.setShardsTotal(config.getTotalShards(1));
        // Use enableIntents with a Collection to avoid array conversion issues
        List<GatewayIntent> intents = config.intents().isEmpty() ?
                List.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES) :
                config.intents();
        builder.enableIntents(intents);
        this.shardManager = builder.build();

        this.commandManager = new CommandManager(config);
        this.eventManager = new EventManager(config, this, shardManager); // Pass this GBF instance
        this.shutdownManager = new ShutdownManager(shardManager, shardThreadPools);

        if (config.AutoLogin()) {
            try {
                login();
            } catch (InterruptedException e) {
                Logger.error("Login interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    public void login() throws InterruptedException {
        final long loginStartTime = startTime; // Capture startTime as effectively final
        CompletableFuture.allOf(
                eventManager.registerEvents(),
                commandManager.registerCommandsAsync()
        ).thenRun(() -> {
            shardManager.getShards().forEach(jda -> {
                try {
                    jda.awaitReady();
                    Logger.success("GBF Handler v4.1 Shard " + jda.getShardInfo().getShardId() +
                            " is ready! Took " + (System.currentTimeMillis() - loginStartTime) + "ms to load");
                } catch (InterruptedException e) {
                    Logger.error("Error waiting for shard " + jda.getShardInfo().getShardId() + " to be ready: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            });
        }).exceptionally(throwable -> {
            Logger.error("Startup failed: " + throwable.getMessage());
            return null;
        }).join();
    }

    public Config getConfig() {
        return config;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public List<JDA> getJDAs() {
        return shardManager.getShards();
    }

    public static String getHandlerVersion() {
        return "4.1.0";
    }

    public MessageCommand getMessageCommand(String name) {
        return commandManager.getMessageCommand(name).join();
    }

    public void setAlias(String commandName, String[] aliases) {
        commandManager.setAlias(commandName, aliases);
    }

    public String resolveCommandFromAlias(String alias) {
        return commandManager.resolveCommandFromAlias(alias);
    }

    public void shutdown() {
        shutdownManager.shutdown();
    }
}