package org.serverboi.audio;

import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

public class AudioSessionManager {
    private static final Map<Long, Process> sessions = new ConcurrentHashMap<>();
    private static final Map<Long, Queue<String>> trackQueues = new ConcurrentHashMap<>();

    public static void register(Guild guild, Process process) {
        sessions.put(guild.getIdLong(), process);
    }

    public static void stop(Guild guild) {
        Process process = sessions.remove(guild.getIdLong());
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public static boolean isStreaming(Guild guild) {
        Process process = sessions.get(guild.getIdLong());
        return process != null && process.isAlive();
    }

    public static void enqueue(Guild guild, String url) {
        trackQueues.computeIfAbsent(guild.getIdLong(), k -> new LinkedList<>()).add(url);
    }

    public static String dequeue(Guild guild) {
        Queue<String> queue = trackQueues.get(guild.getIdLong());
        return (queue != null) ? queue.poll() : null;
    }

    public static boolean hasQueue(Guild guild) {
        Queue<String> queue = trackQueues.get(guild.getIdLong());
        return queue != null && !queue.isEmpty();
    }

    public static void clearQueue(Guild guild) {
        Queue<String> queue = trackQueues.get(guild.getIdLong());
        if (queue != null) queue.clear();
    }
}