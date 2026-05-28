package com.contextapi.voice;

import com.contextapi.dtos.LessonDTO;
import com.contextapi.dynamics.RaptorDynamic;
import com.contextapi.entities.Lesson;
import com.contextapi.enums.LessonStatus;
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
    private final RaptorDynamic raptorDynamic;

    public VoiceSessionService(SpeechToTextProvider sttProvider,
                                TextToSpeechProvider ttsProvider,
                                LessonRepository lessonRepository,
                                LessonService lessonService,
                                RaptorDynamic raptorDynamic) {
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
        this.lessonRepository = lessonRepository;
        this.lessonService = lessonService;
        this.raptorDynamic = raptorDynamic;
    }

    public void startSession(Consumer<GreetingResult> callback) {

        String response = raptorDynamic.startVoice();

        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(response) : null;
        callback.accept(new GreetingResult(audio, response));
    }

    public void processVoiceInput(String sessionId, byte[] audioData,
                                   Consumer<String> transcriptCallback,
                                   Consumer<ProcessResult> callback) {

        String transcript;
        if (sttProvider.isConfigured()) {
            transcript = sttProvider.transcribe(audioData, "webm");
            if (transcript == null || transcript.isBlank()) {
                callback.accept(new ProcessResult(null, "", ""));
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

            String responseText = result.getLastTeacherMessage();
            if (responseText == null || responseText.isBlank()) {
                responseText = "Recebido!";
            }

            byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(responseText) : null;
            callback.accept(new ProcessResult(audio, transcript, responseText));
        } catch (Exception e) {
            log.error("Failed to submit voice answer for session {}: {}", sessionId, e.getMessage());
            callback.accept(new ProcessResult(null, transcript,
                    "Erro ao processar resposta: " + e.getMessage()));
        }
    }

    public void synthesizeText(String text, Consumer<SynthesizeResult> callback) {
        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(text) : null;
        callback.accept(new SynthesizeResult(audio, text));
    }

    public void endSession(String sessionId) {
        log.debug("Voice session ended: {}", sessionId);
    }
}

