package com.contextapi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class VoiceSessionHandler extends BinaryWebSocketHandler {

    private static final int MAX_AUDIO_BYTES = 512 * 1024;
    private static final int MIN_AUDIO_BYTES = 1024;
    private static final double VAD_ENERGY_THRESHOLD = 200.0;

    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();
    private final VoiceSessionService voiceSessionService;

    public VoiceSessionHandler(VoiceSessionService voiceSessionService) {
        this.voiceSessionService = voiceSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Voice WebSocket connected: {}", session.getId());
        sessionBuffers.put(session.getId(), new ByteArrayOutputStream());
        voiceSessionService.startSession(session.getId(), result -> {
            sendTextMessage(session, "{\"type\":\"response\",\"text\":\"" + escapeJson(result.greetingText()) + "\"}");
            if (result.audio() != null) {
                sendBinaryMessage(session, result.audio());
            }
        });
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteArrayOutputStream buffer = sessionBuffers.get(session.getId());
        if (buffer == null) {
            return;
        }

        ByteBuffer payload = message.getPayload();
        byte[] chunk = new byte[payload.remaining()];
        payload.get(chunk);

        if (chunk.length == 0 || (chunk.length == 1 && chunk[0] == 0x00)) {
            processCompleteAudio(session, buffer);
            return;
        }

        if (chunk.length == 1 && chunk[0] == 0x01) {
            buffer.reset();
            return;
        }

        if (buffer.size() + chunk.length > MAX_AUDIO_BYTES) {
            log.warn("Audio buffer full for session {}, resetting", session.getId());
            buffer.reset();
        }

        buffer.write(chunk, 0, chunk.length);
    }

    private void processCompleteAudio(WebSocketSession session, ByteArrayOutputStream buffer) {
        byte[] audioData = buffer.toByteArray();
        buffer.reset();

        if (audioData.length < MIN_AUDIO_BYTES) {
            return;
        }

        if (!hasVoiceActivity(audioData)) {
            log.trace("Silence detected, discarding audio from session {}", session.getId());
            return;
        }

        log.debug("Processing audio from session {}: {} bytes", session.getId(), audioData.length);

        voiceSessionService.processVoiceInput(session.getId(), audioData, result -> {
            // Send user transcript first (shows as "Você" in frontend)
            if (!result.transcript().isBlank()) {
                sendTextMessage(session, "{\"type\":\"transcript\",\"text\":\"" + escapeJson(result.transcript()) + "\"}");
            }
            // Send teacher response text
            sendTextMessage(session, "{\"type\":\"response\",\"text\":\"" + escapeJson(result.responseText()) + "\"}");
            // Send TTS audio if available (triggers "speaking" state)
            if (result.audio() != null) {
                sendBinaryMessage(session, result.audio());
            }
        });
    }

    /**
     * Simple energy-based Voice Activity Detection.
     * Calculates RMS energy of the audio signal. If below threshold, considers it silence.
     * Works with PCM16 raw audio (common in browser Web Audio API).
     * For encoded formats (Opus/WebM), the energy check is a coarse approximation.
     */
    private boolean hasVoiceActivity(byte[] audioData) {
        if (audioData == null || audioData.length < 256) {
            return false;
        }

        // Check RMS energy - works best with PCM16 raw audio
        // For WebM/Opus encoded data, we do a sample-based RMS on raw bytes
        double sumSquares = 0;
        int samples = 0;

        // Process min(4000 bytes, actual) to keep it fast
        int checkLength = Math.min(audioData.length, 4000);
        for (int i = 0; i < checkLength - 1; i += 2) {
            // Convert 2 bytes to signed 16-bit sample
            short sample = (short) (((audioData[i + 1] & 0xFF) << 8) | (audioData[i] & 0xFF));
            sumSquares += sample * sample;
            samples++;
        }

        if (samples == 0) return false;

        double rms = Math.sqrt(sumSquares / samples);
        return rms >= VAD_ENERGY_THRESHOLD;
    }

    private void sendTextMessage(WebSocketSession session, String text) {
        try {
            session.sendMessage(new org.springframework.web.socket.TextMessage(text));
        } catch (Exception e) {
            log.error("Failed to send WebSocket text message: {}", e.getMessage());
        }
    }

    private void sendBinaryMessage(WebSocketSession session, byte[] audio) {
        try {
            session.sendMessage(new BinaryMessage(audio));
        } catch (Exception e) {
            log.error("Failed to send WebSocket binary message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Voice WebSocket disconnected: {} (status: {})", session.getId(), status);
        sessionBuffers.remove(session.getId());
        voiceSessionService.endSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Voice WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessionBuffers.remove(session.getId());
        voiceSessionService.endSession(session.getId());
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
