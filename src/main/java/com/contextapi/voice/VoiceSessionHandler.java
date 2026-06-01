package com.contextapi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class VoiceSessionHandler extends BinaryWebSocketHandler {

    private static final int MAX_AUDIO_BYTES = 512 * 1024;
    private static final int MIN_AUDIO_BYTES = 8000;  // ~500ms of audio at 16kHz
    private static final double VAD_ENERGY_THRESHOLD = 300.0;

    private static final String JSON_TRANSCRIPT_PREFIX = "{\"type\":\"transcript\",\"text\":\"";

    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();
    private final VoiceSessionService voiceSessionService;

    private final ExecutorService voiceExecutor = Executors.newCachedThreadPool();
    private static final String JSON_RESPONSE_PREFIX = "{\"type\":\"response\",\"text\":\"";

    public VoiceSessionHandler(VoiceSessionService voiceSessionService) {
        this.voiceSessionService = voiceSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Voice WebSocket connected: {}", session.getId());
        sessionBuffers.put(session.getId(), new ByteArrayOutputStream());

        
        CompletableFuture.runAsync(() ->
            voiceSessionService.startSession(result -> {
                sendTextMessage(session, JSON_RESPONSE_PREFIX + escapeJson(result.greetingText()) + "\"}");
                if (result.audio() != null) {
                    sendBinaryMessage(session, result.audio());
                }
            }), voiceExecutor);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("Text message from session {}: {}", session.getId(), payload);

        try {
            if (payload.contains("\"type\":\"speak\"") || payload.contains("\"type\":\"next\"")) {
                String text = extractJsonValue(payload, "text");

                CompletableFuture.runAsync(() ->
                    voiceSessionService.synthesizeText(text, result -> {
                        sendTextMessage(session, JSON_RESPONSE_PREFIX + escapeJson(result.text()) + "\"}");
                        if (result.audio() != null) {
                            sendBinaryMessage(session, result.audio());
                        }
                    }), voiceExecutor);
            }
        } catch (Exception e) {
            log.error("Failed to handle text message: {}", e.getMessage());
        }
    }

    /**
     * Simple JSON value extractor for quoted keys. Not a full parser — sufficient
     * for our small control messages.
     */
    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
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

        CompletableFuture.runAsync(() ->
            voiceSessionService.processVoiceInput(session.getId(), audioData,
                transcript -> sendTextMessage(session,
                        JSON_TRANSCRIPT_PREFIX + escapeJson(transcript) + "\"}"),
                result -> handleProcessResult(session, result)
            ), voiceExecutor);
    }

    private void handleProcessResult(WebSocketSession session, VoiceSessionService.ProcessResult result) {
        if (!result.responseText().isBlank()) {
            log.debug("Sending response to session {}: {}", session.getId(), result.responseText());
            sendTextMessage(session, JSON_RESPONSE_PREFIX + escapeJson(result.responseText()) + "\"}");

            if (result.audio() != null) {
                sleepSafely(100);
                sendBinaryMessage(session, result.audio());
            }
        }
        if (result.transcript().isBlank() && result.responseText().isBlank()) {
            sendTextMessage(session, "{\"type\":\"error\",\"message\":\"Rate limit do STT atingido. Tente novamente em instantes.\"}");
        }
    }

    private static void sleepSafely(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        
        double sumSquares = 0;
        int samples = 0;
        
        int checkLength = Math.min(audioData.length, 4000);
        for (int i = 0; i < checkLength - 1; i += 2) {
            
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
            session.sendMessage(new TextMessage(text));
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

