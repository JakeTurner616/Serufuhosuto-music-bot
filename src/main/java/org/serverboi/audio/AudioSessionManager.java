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
