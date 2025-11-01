package org.serverboi.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Reliable StreamSendHandler — prevents audio stalls and Discord rate-limit bucket buildup.
 * Uses a small async feeder thread to keep ~0.5 s (≈25 frames) of PCM buffered.
 */
public class StreamSendHandler implements AudioSendHandler {

    private static final int FRAME_SIZE = 3840; // 20 ms of 48 kHz stereo 16-bit PCM
    private static final int BUFFER_FRAMES = 25; // ~0.5 s of audio
    private final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(BUFFER_FRAMES);
    private final byte[] silence = new byte[FRAME_SIZE];
    private volatile boolean running = true;
    private volatile boolean feederDone = false;

    public StreamSendHandler(InputStream ffmpegStream, Runnable onEnd) {
        System.out.println("[DEBUG] StreamSendHandler initialized with " + FRAME_SIZE +
                "-byte frames and " + BUFFER_FRAMES + "-frame async buffer");

        Thread feeder = new Thread(() -> {
            try {
                byte[] frame = new byte[FRAME_SIZE];
                int totalRead;
                while (running && (totalRead = readFully(ffmpegStream, frame)) != -1) {
                    if (totalRead < FRAME_SIZE)
                        Arrays.fill(frame, totalRead, FRAME_SIZE, (byte) 0);
                    frameQueue.put(Arrays.copyOf(frame, FRAME_SIZE));
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                System.err.println("[ERROR] Feeder thread failed: " + e.getMessage());
            } finally {
                feederDone = true;
                running = false;
                try { ffmpegStream.close(); } catch (Exception ignored) {}
                System.out.println("[DEBUG] Feeder ended — calling onEnd");
                if (onEnd != null) onEnd.run();
            }
        }, "FFmpeg-Feeder");
        feeder.setDaemon(true);
        feeder.start();
    }

    private int readFully(InputStream in, byte[] buf) {
        int off = 0;
        int read = 0;
        try {
            while (off < buf.length) {
                read = in.read(buf, off, buf.length - off);
                if (read == -1) break;
                off += read;
            }
            if (off == 0 && read == -1)
                return -1;
            return off;
        } catch (Exception e) {
            System.err.println("[ERROR] readFully failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean canProvide() {
        // Provide until feeder done AND buffer empty
        return !frameQueue.isEmpty() || (!feederDone && running);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        byte[] frame = frameQueue.poll();
        if (frame == null) {
            // Prevent underruns — send silence frame if no buffered audio
            return ByteBuffer.wrap(silence);
        }
        return ByteBuffer.wrap(frame);
    }

    @Override
    public boolean isOpus() {
        return false; // Raw PCM
    }

    /** Stop streaming manually (on skip/stop). */
    public void stop() {
        running = false;
        frameQueue.clear();
    }
}
