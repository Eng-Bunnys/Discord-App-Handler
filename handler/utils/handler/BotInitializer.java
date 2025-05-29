// This class is now replaced with ShardManager

//package org.bunnys.handler.utils.handler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bunnys.handler.config.Config;
import org.bunnys.handler.utils.EnvLoader;
import org.bunnys.handler.utils.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

//public class BotInitializer {
//    private static final int DEFAULT_RETRIES = 3;
//    private static final long INITIAL_BACKOFF_MS = 500;
//    private volatile JDA jda;
//    private final Config config;
//    private final int shardId;
//    private final int totalShards;
//    private final Executor shardPool;
//
//    public BotInitializer(Config config, int shardId, int totalShards, Executor shardPool) {
//        this.config = config;
//        this.shardId = shardId;
//        this.totalShards = totalShards;
//        this.shardPool = shardPool;
//
//        String token = config.token() != null && !config.token().isBlank()
//                ? config.token()
//                : EnvLoader.get("TOKEN");
//
//        if (token == null || token.isBlank())
//            throw new IllegalStateException("Bot token is not specified in config or environment.");
//
//        this.config.token(token);
//    }
//
//    public CompletableFuture<Void> login() {
//        long jdaStart = System.currentTimeMillis();
//
//        List<GatewayIntent> intents = this.config.intents().isEmpty()
//                ? Arrays.asList(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
//                : this.config.intents();
//
//        return attemptLogin(intents, 0, INITIAL_BACKOFF_MS)
//                .thenRun(() -> {
//                    if (this.config.LogActions())
//                        Logger.info("JDA initialization for shard " + shardId + " took " +
//                                (System.currentTimeMillis() - jdaStart) + "ms");
//                });
//    }
//
//    private CompletableFuture<Object> attemptLogin(List<GatewayIntent> intents, int attempt, long backoff) {
//        if (attempt >= this.config.getRetries(DEFAULT_RETRIES)) {
//            return CompletableFuture.failedFuture(
//                    new RuntimeException("Failed to initialize JDA for shard " + shardId + " after " +
//                            this.config.getRetries(DEFAULT_RETRIES) + " attempts"));
//        }
//
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                jda = JDABuilder.create(this.config.token(), intents)
//                        .setMemberCachePolicy(MemberCachePolicy.DEFAULT)
//                        .setChunkingFilter(ChunkingFilter.NONE)
//                        .setShardId(shardId)
//                        .setShardsTotal(totalShards)
//                        .build();
//                return null;
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }, shardPool).thenComposeAsync(result -> {
//            if (result == null)
//                return CompletableFuture.completedFuture(null);
//            return CompletableFuture.completedFuture(null);
//        }, shardPool).exceptionallyAsync(throwable -> {
//            Logger.warning("JDA initialization for shard " + shardId + " failed, retrying (" + (attempt + 1) + "/" +
//                    config.getRetries(DEFAULT_RETRIES) + "): " + throwable.getMessage());
//            return CompletableFuture.runAsync(() -> {}, shardPool)
//                    .orTimeout(backoff, TimeUnit.MILLISECONDS)
//                    .thenComposeAsync(__ -> attemptLogin(intents, attempt + 1, backoff * 2), shardPool);
//        }, shardPool);
//    }
//
//    public void awaitReady(Runnable onReady) {
//        CompletableFuture.runAsync(() -> {
//            try {
//                jda.awaitReady();
//                if (config.LogActions())
//                    onReady.run();
//            } catch (InterruptedException e) {
//                Logger.error("Error waiting for shard " + shardId + " to be ready: " + e.getMessage());
//                Thread.currentThread().interrupt();
//            }
//        }, shardPool);
//    }
//
//    public JDA getJDA() {
//        return jda;
//    }
//
//    public int getShardId() {
//        return shardId;
//    }
//}