package com.contextapi.voice;

import com.contextapi.dtos.LessonDTO;
import com.contextapi.dynamics.RaptorDynamic;
import com.contextapi.entities.ConversationMessage;
import com.contextapi.entities.Lesson;
import com.contextapi.enums.ConversationAuthor;
import com.contextapi.enums.LessonStatus;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.providers.SpeechToTextProvider;
import com.contextapi.providers.TextToSpeechProvider;
import com.contextapi.repositories.LessonRepository;
import com.contextapi.services.LessonService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VoiceSessionService {

    public record ProcessResult(byte[] audio, String transcript, String responseText) {}
    public record GreetingResult(byte[] audio, String greetingText) {}
    public record SynthesizeResult(byte[] audio, String text) {}

    private final SpeechToTextProvider sttProvider;
    private final TextToSpeechProvider ttsProvider;
    private final LessonRepository lessonRepository;
    private final LessonService lessonService;

    public VoiceSessionService(SpeechToTextProvider sttProvider,
                                TextToSpeechProvider ttsProvider,
                                LessonRepository lessonRepository,
                                LessonService lessonService) {
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
        this.lessonRepository = lessonRepository;
        this.lessonService = lessonService;
    }

    public void startSession(Consumer<GreetingResult> callback) {
        String response = lessonService.startVoice();
        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(response) : null;
        callback.accept(new GreetingResult(audio, response));
    }

    public void processVoiceInput(String sessionId, byte[] audioData,
                                   Consumer<String> transcriptCallback,
                                   Consumer<ProcessResult> callback) {

        String transcript;
        if (sttProvider.isConfigured()) {
            transcript = sttProvider.transcribe(audioData, "webm");
            
            // Validate transcript before processing
            if (!isValidTranscript(transcript)) {
                log.warn("Ignoring invalid transcript for session {}: '{}'", sessionId, transcript);
                callback.accept(new ProcessResult(null, "", "Desculpe, nao consegui entender. Pode falar novamente?"));
                return;
            }
            
            transcriptCallback.accept(transcript);
        } else {
            callback.accept(new ProcessResult(null, "", "STT nao configurado."));
            return;
        }

        try {
            Lesson lesson = lessonRepository
                    .findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                    .orElseThrow(() -> new IllegalStateException("No active lesson"));

            LessonDTO result = lessonService.next(lesson.getId(), transcript);

            // Send first response (feedback to user's answer)
            String responseText1 = result.getConversationHistory().get(result.getConversationHistory().size() - 2).getContent();
            byte[] audio1 = ttsProvider.isConfigured() ? ttsProvider.synthesize(responseText1) : null;
            log.debug("Sending first response for session {}: {}", sessionId, responseText1);
            callback.accept(new ProcessResult(audio1, transcript, responseText1));

            // Larger delay to ensure first response is fully received and processed by frontend
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Send second response (next exercise)
            String responseText2 = result.getConversationHistory().get(result.getConversationHistory().size() - 1).getContent();
            byte[] audio2 = ttsProvider.isConfigured() ? ttsProvider.synthesize(responseText2) : null;
            log.debug("Sending second response for session {}: {}", sessionId, responseText2);
            callback.accept(new ProcessResult(audio2, transcript, responseText2));
        } catch (Exception e) {
            log.error("Failed to submit voice answer for session {}: {}", sessionId, e.getMessage());
            callback.accept(new ProcessResult(null, transcript,
                    "Erro ao processar resposta: " + e.getMessage()));
        }
    }

    private static final int MIN_TRANSCRIPT_LENGTH = 3;  // Minimum characters in transcript
    private static final String VALID_TRANSCRIPT_PATTERN = "[a-zA-ZÀ-ÿ0-9\\s]";

    /**
     * Validates if transcript is meaningful (not just noise or punctuation).
     * Rejects single dots, empty strings, or transcripts with less than 3 characters.
     */
    private boolean isValidTranscript(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return false;
        }
        
        String cleaned = transcript.trim();
        
        // Reject if it's just punctuation or single characters like "."
        if (!cleaned.matches(".*[a-zA-ZÀ-ÿ0-9]+.*")) {
            log.debug("Transcript rejected (no valid characters): '{}'", cleaned);
            return false;
        }
        
        // Reject if too short
        if (cleaned.length() < MIN_TRANSCRIPT_LENGTH) {
            log.debug("Transcript rejected (too short - {} chars): '{}'", cleaned.length(), cleaned);
            return false;
        }
        
        return true;
    }

    public void synthesizeText(String text, Consumer<SynthesizeResult> callback) {
        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(text) : null;
        callback.accept(new SynthesizeResult(audio, text));
    }

    public void endSession(String sessionId) {
        log.debug("Voice session ended: {}", sessionId);
    }
}

