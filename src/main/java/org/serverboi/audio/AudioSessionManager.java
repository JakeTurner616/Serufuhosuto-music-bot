package org.serverboi.audio;

import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioSessionManager {

    public record TrackRequest(String query, String title) {}

    private static final Map<Long, Process> sessions = new ConcurrentHashMap<>();
    private static final Map<Long, StreamSendHandler> handlers = new ConcurrentHashMap<>();
    private static final Map<Long, Queue<TrackRequest>> trackQueues = new ConcurrentHashMap<>();
    private static final Map<Long, TrackRequest> nowPlaying = new ConcurrentHashMap<>();

    // Simple join-failure cooldown map to avoid repeated connect attempts when the voice server
    // repeatedly rejects the connection (e.g., E2EE/DAVE required or auth/session failures).
    // Stores the epoch millis until which attempts are blocked for a guild.
    private static final Map<Long, Long> joinCooldownUntil = new ConcurrentHashMap<>();
    public static void register(Guild guild, Process process, StreamSendHandler handler, TrackRequest playing) {
        sessions.put(guild.getIdLong(), process);
        handlers.put(guild.getIdLong(), handler);
        if (playing != null) nowPlaying.put(guild.getIdLong(), playing);
    }

    public static void stop(Guild guild) {
        long id = guild.getIdLong();

        StreamSendHandler h = handlers.remove(id);
        if (h != null) h.stop();

        Process p = sessions.remove(id);
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(750, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                }
            } catch (Exception ignored) {
                p.destroyForcibly();
            }
        }

        nowPlaying.remove(id);
    }

    public static boolean isStreaming(Guild guild) {
        Process process = sessions.get(guild.getIdLong());
        return process != null && process.isAlive();
    }

    /**
     * Check whether we are allowed to attempt joining a voice channel in this guild.
     * This uses a simple cooldown window set by {@link #registerFailedJoin(Guild)}.
     */
    public static boolean canAttemptJoin(Guild guild) {
        long id = guild.getIdLong();
        Long until = joinCooldownUntil.get(id);
        return until == null || System.currentTimeMillis() >= until;
    }

    /**
     * Register a failed join attempt and set a short cooldown to avoid tight connect loops.
     * Current policy: 30s cooldown per failed attempt.
     */
    public static void registerFailedJoin(Guild guild) {
        long id = guild.getIdLong();
        long cooldownMs = 30_000L; // 30 seconds
        joinCooldownUntil.put(id, System.currentTimeMillis() + cooldownMs);
    }

    /**
     * Clear any join cooldown for the guild (call when join succeeds).
     */
    public static void clearJoinCooldown(Guild guild) {
        joinCooldownUntil.remove(guild.getIdLong());
    }

    public static void enqueue(Guild guild, TrackRequest req) {
        trackQueues.computeIfAbsent(guild.getIdLong(), k -> new ConcurrentLinkedQueue<>()).add(req);
    }

    public static TrackRequest dequeue(Guild guild) {
        Queue<TrackRequest> q = trackQueues.get(guild.getIdLong());
        return (q != null) ? q.poll() : null;
    }

    public static boolean hasQueue(Guild guild) {
        Queue<TrackRequest> q = trackQueues.get(guild.getIdLong());
        return q != null && !q.isEmpty();
    }

    public static void clearQueue(Guild guild) {
        Queue<TrackRequest> q = trackQueues.get(guild.getIdLong());
        if (q != null) q.clear();
    }

    public static TrackRequest getNowPlaying(Guild guild) {
        return nowPlaying.get(guild.getIdLong());
    }

    public static void setNowPlaying(Guild guild, TrackRequest req) {
        if (req == null) nowPlaying.remove(guild.getIdLong());
        else nowPlaying.put(guild.getIdLong(), req);
    }
}
