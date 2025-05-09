package org.serverboi.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class StreamSendHandler implements AudioSendHandler {
    private final InputStream stream;
    private final byte[] buffer = new byte[3840]; // 20ms of 48kHz stereo 16-bit PCM
    private final Runnable onEnd;

    private boolean firstFrame = true;
    private boolean ended = false; // Ensure onEnd is only triggered once
    private boolean sendSilenceOnce = false; // Send silence one frame after EOF

    public StreamSendHandler(InputStream stream, Runnable onEnd) {
        this.stream = stream;
        this.onEnd = onEnd;
        System.out.println("[DEBUG] StreamSendHandler initialized with 3840-byte buffer (20ms, 48kHz, 2ch, 16-bit)");
    }

    @Override
    public boolean canProvide() {
        try {
            return !ended && (stream.available() > 0 || sendSilenceOnce);
        } catch (Exception e) {
            System.err.println("[ERROR] canProvide check failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        if (ended && sendSilenceOnce) {
            // Send a single silence frame after EOF to flush Discord pipeline
            Arrays.fill(buffer, (byte) 0);
            sendSilenceOnce = false;
            return ByteBuffer.wrap(buffer);
        }

        try {
            int totalRead = 0;
            while (totalRead < buffer.length) {
                int read = stream.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) {
                    if (!ended) {
                        ended = true;
                        sendSilenceOnce = true;
                        onEnd.run();
                        System.out.println("[DEBUG] End of stream detected. Sending silence frame.");
                    }
                    return null;
                }
                totalRead += read;
            }

            if (firstFrame) {
                System.out.println("[DEBUG] First audio frame size: " + totalRead + " bytes");
                firstFrame = false;
            }

            // If we got fewer than 3840 bytes, pad with silence
            if (totalRead < buffer.length) {
                Arrays.fill(buffer, totalRead, buffer.length, (byte) 0);
            }

            return ByteBuffer.wrap(buffer, 0, buffer.length);
        } catch (Exception e) {
            System.err.println("[ERROR] Audio stream read failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isOpus() {
        return false; // we're sending raw PCM
    }
}