package org.serverboi.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class StreamSendHandler implements AudioSendHandler {
    private final InputStream stream;
    private final byte[] buffer = new byte[3840]; // 20ms @ 48kHz stereo 16-bit
    private final Runnable onEnd;
    private boolean firstFrame = true;

    public StreamSendHandler(InputStream stream, Runnable onEnd) {
        this.stream = stream;
        this.onEnd = onEnd;
        System.out.println("[DEBUG] StreamSendHandler initialized with 3840-byte buffer (20ms, 48kHz, 2ch, 16-bit)");
    }

    @Override
    public boolean canProvide() {
        try {
            return stream.available() > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] canProvide check failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        try {
            int totalRead = 0;
            while (totalRead < buffer.length) {
                int read = stream.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) {
                    onEnd.run(); // end of stream
                    return null;
                }
                totalRead += read;
            }

            if (firstFrame) {
                System.out.println("[DEBUG] First audio frame size: " + totalRead + " bytes");
                firstFrame = false;
            }

            return ByteBuffer.wrap(buffer, 0, totalRead);

        } catch (Exception e) {
            System.err.println("[ERROR] Audio stream read failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}