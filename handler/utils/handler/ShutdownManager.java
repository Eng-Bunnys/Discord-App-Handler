package org.bunnys.handler.utils.handler;

import net.dv8tion.jda.api.sharding.ShardManager;
import org.bunnys.handler.utils.Logger;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ShutdownManager {
    private final ShardManager shardManager;
    private final List<ThreadPoolExecutor> shardThreadPools;

    public ShutdownManager(ShardManager shardManager, List<ThreadPoolExecutor> shardThreadPools) {
        this.shardManager = shardManager;
        this.shardThreadPools = shardThreadPools;
    }

    public void shutdown() {
        shardManager.shutdown();
        for (ThreadPoolExecutor pool : shardThreadPools) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Logger.error("Interrupted during thread pool shutdown: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}