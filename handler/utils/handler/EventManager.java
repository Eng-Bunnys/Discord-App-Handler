package org.bunnys.handler.utils.handler;

import net.dv8tion.jda.api.sharding.ShardManager;
import org.bunnys.handler.GBF;
import org.bunnys.handler.config.Config;
import org.bunnys.handler.events.EventLoader;
import org.bunnys.handler.utils.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EventManager {
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private final Config config;
    private final ShardManager shardManager;
    private final GBF client; // Added GBF instance
    private final boolean loadEvents;
    private final boolean loadHandlerEvents;
    private final AtomicInteger eventCount = new AtomicInteger(0);

    public EventManager(Config config, GBF client, ShardManager shardManager) {
        this.config = config;
        this.client = client;
        this.shardManager = shardManager;
        this.loadEvents = config.EventFolder() != null && !config.EventFolder().isBlank() && !config.IgnoreEvents();
        this.loadHandlerEvents = !config.IgnoreEventsFromHandler();
    }

    public CompletableFuture<Void> registerEvents() {
        eventCount.set(0);
        long start = System.currentTimeMillis();
        CompletableFuture<Void> handlerEventsFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> customEventsFuture = CompletableFuture.completedFuture(null);

        Executor shardPool = config.getShardThreadPool(0); // Use first shard's thread pool for simplicity

        if (loadHandlerEvents)
            handlerEventsFuture = loadAndRegisterEvents("org.bunnys.handler.events.defaults", shardPool);

        if (loadEvents)
            customEventsFuture = loadAndRegisterEvents(config.EventFolder(), shardPool);

        return CompletableFuture.allOf(handlerEventsFuture, customEventsFuture)
                .thenRun(() -> {
                    if (config.LogActions()) {
                        Logger.success("Loaded " + eventCount.get() + " events across all shards in " +
                                (System.currentTimeMillis() - start) + "ms");
                    }
                })
                .exceptionally(throwable -> {
                    Logger.error("Event registration failed: " + throwable.getMessage());
                    return null;
                });
    }

    private CompletableFuture<Void> loadAndRegisterEvents(String packageName, Executor shardPool) {
        StringBuilder slowEvents = new StringBuilder();
        return CompletableFuture.supplyAsync(() -> EventLoader.loadEvents(packageName, client, shardPool), shardPool)
                .thenCompose(events -> {
                    CompletableFuture<?>[] futures = events.stream()
                            .map(event -> CompletableFuture.runAsync(() -> {
                                        try {
                                            long eventStart = System.nanoTime();
                                            shardManager.addEventListener(event);
                                            long eventEnd = System.nanoTime();
                                            if (config.LogActions() && (eventEnd - eventStart) / 1_000_000 > 1) {
                                                synchronized (slowEvents) {
                                                    slowEvents.append("Slow event registration for ")
                                                            .append(event.getClass().getSimpleName())
                                                            .append(": ")
                                                            .append((eventEnd - eventStart) / 1_000_000)
                                                            .append("ms\n");
                                                }
                                            }
                                            eventCount.incrementAndGet();
                                        } catch (Exception e) {
                                            Logger.error("Failed to register event " + event.getClass().getName() + ": " + e.getMessage());
                                        }
                                    }, shardPool)
                                    .orTimeout(config.getTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                                    .exceptionally(throwable -> {
                                        Logger.error("Failed to register event from " + packageName + ": " + throwable.getMessage());
                                        return null;
                                    }))
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(futures);
                })
                .thenRun(() -> {
                    if (config.LogActions() && slowEvents.length() > 0) {
                        Logger.warning(slowEvents.toString());
                    }
                });
    }
}