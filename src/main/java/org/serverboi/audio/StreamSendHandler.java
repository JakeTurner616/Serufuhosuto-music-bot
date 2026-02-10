// src/main/java/org/serverboi/audio/StreamSendHandler.java
package org.serverboi.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Reliable StreamSendHandler — prevents audio stalls and Discord rate-limit bucket buildup.
 * Buffers ~0.5s of raw PCM (s16le, 48kHz, stereo).
 */
public class StreamSendHandler implements AudioSendHandler {

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2; // s16le
    private static final int FRAME_MS = 20;

    private static final int SAMPLES_PER_CH_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000; // 960
    private static final int FRAME_SIZE = SAMPLES_PER_CH_PER_FRAME * CHANNELS * BYTES_PER_SAMPLE; // 3840

    private static final int BUFFER_FRAMES = 25; // ~0.5s

    private final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(BUFFER_FRAMES);
    private final byte[] silence = new byte[FRAME_SIZE];

    private volatile boolean running = true;
    private volatile boolean feederDone = false;

    public StreamSendHandler(InputStream ffmpegStdout, Runnable onEnd) {
        System.out.println("[DEBUG] StreamSendHandler PCM " + SAMPLE_RATE + "Hz " + CHANNELS +
                "ch s16le; frame=" + FRAME_SIZE + " bytes; bufferFrames=" + BUFFER_FRAMES);

        Thread feeder = new Thread(() -> {
            try {
                byte[] frame = new byte[FRAME_SIZE];
                int totalRead;
                while (running && (totalRead = readFully(ffmpegStdout, frame)) != -1) {
                    if (totalRead < FRAME_SIZE) {
                        Arrays.fill(frame, totalRead, FRAME_SIZE, (byte) 0);
                    }
                    frameQueue.put(Arrays.copyOf(frame, FRAME_SIZE));
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                System.err.println("[ERROR] Feeder thread failed: " + e.getMessage());
            } finally {
                feederDone = true;
                running = false;
                try { ffmpegStdout.close(); } catch (Exception ignored) {}
                if (onEnd != null) onEnd.run();
            }
        }, "FFmpeg-Feeder");

        feeder.setDaemon(true);
        feeder.start();
    }

    private int readFully(InputStream in, byte[] buf) {
        int off = 0;
        int read;
        try {
            while (off < buf.length) {
                read = in.read(buf, off, buf.length - off);
                if (read == -1) break;
                off += read;
            }
            if (off == 0) return -1;
            return off;
        } catch (Exception e) {
            System.err.println("[ERROR] readFully failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean canProvide() {
        return !frameQueue.isEmpty() || (!feederDone && running);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        byte[] frame = frameQueue.poll();
        return ByteBuffer.wrap(frame != null ? frame : silence);
    }

    @Override
    public boolean isOpus() {
        return false; // raw PCM -> JDA encodes to opus
    }

    public void stop() {
        running = false;
        frameQueue.clear();
    }
}
