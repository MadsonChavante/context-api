package com.contextapi.voice;

import com.contextapi.dtos.LessonDTO;
import com.contextapi.dtos.SubmitAnswerRequest;
import com.contextapi.entities.Lesson;
import com.contextapi.entities.LessonExercise;
import com.contextapi.enums.LessonStatus;
import com.contextapi.providers.SpeechToTextProvider;
import com.contextapi.providers.TextToSpeechProvider;
import com.contextapi.repositories.LessonExerciseRepository;
import com.contextapi.repositories.LessonRepository;
import com.contextapi.services.LessonService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class VoiceSessionService {

    public record ProcessResult(byte[] audio, String transcript, String responseText) {}
    public record GreetingResult(byte[] audio, String greetingText) {}
    public record SynthesizeResult(byte[] audio, String text) {}

    private final SpeechToTextProvider sttProvider;
    private final TextToSpeechProvider ttsProvider;
    private final LessonRepository lessonRepository;
    private final LessonExerciseRepository exerciseRepository;
    private final LessonService lessonService;

    public VoiceSessionService(SpeechToTextProvider sttProvider,
                                TextToSpeechProvider ttsProvider,
                                LessonRepository lessonRepository,
                                LessonExerciseRepository exerciseRepository,
                                LessonService lessonService) {
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
        this.lessonRepository = lessonRepository;
        this.exerciseRepository = exerciseRepository;
        this.lessonService = lessonService;
    }

    /**
     * Starts a voice session tied to the active lesson.
     */
    public void startSession(String sessionId, Consumer<GreetingResult> callback) {
        Lesson lesson = lessonRepository
                .findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .orElse(null);

        if (lesson == null) {
            callback.accept(new GreetingResult(null,
                    "Nenhuma aula ativa. Crie uma aula no modo texto primeiro."));
            return;
        }

        // Use repository directly to avoid LazyInitializationException
        String greeting;
        List<LessonExercise> allExercises = exerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId());
        LessonExercise current = allExercises.stream()
                .filter(e -> !e.isAnswered())
                .findFirst()
                .orElse(null);

        if (current != null) {
            greeting = "Traduza para o ingles: " + current.getPromptPt();
        } else {
            greeting = lesson.getIntro() != null && !lesson.getIntro().isBlank()
                    ? lesson.getIntro()
                    : "Pronto para praticar! Fale a traducao da frase que aparecer na tela.";
        }

        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(greeting) : null;
        callback.accept(new GreetingResult(audio, greeting));
    }

    /**
     * Processes a voice input: transcribe and submit to LessonService.
     */
    public void processVoiceInput(String sessionId, byte[] audioData,
                                   Consumer<String> transcriptCallback,
                                   Consumer<ProcessResult> callback) {
        Lesson lesson = lessonRepository
                .findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .orElse(null);

        if (lesson == null) {
            callback.accept(new ProcessResult(null, "",
                    "Nenhuma aula ativa. Ative o modo aula primeiro."));
            return;
        }

        // Use repository directly to avoid LazyInitializationException
        List<LessonExercise> allExercises = exerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId());
        LessonExercise current = allExercises.stream()
                .filter(e -> !e.isAnswered())
                .findFirst()
                .orElse(null);

        if (current == null) {
            callback.accept(new ProcessResult(null, "",
                    "Nao ha exercicio pendente. Aguarde o proximo exercicio."));
            return;
        }

        // Transcribe
        String transcript;
        if (sttProvider.isConfigured()) {
            transcript = sttProvider.transcribe(audioData, "webm");
            if (transcript == null || transcript.isBlank()) {
                callback.accept(new ProcessResult(null, "", ""));
                return;
            }
            // Libera o transcript para o front IMEDIATAMENTE
            transcriptCallback.accept(transcript);
        } else {
            callback.accept(new ProcessResult(null, "", "STT nao configurado."));
            return;
        }

        // Submit via LessonService (which classifies, evaluates, and auto-generates next)
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setExerciseId(current.getId());
        req.setAnswer(transcript);

        try {
            LessonDTO result = lessonService.submitAnswer(lesson.getId(), req);

            // Build response text — lastTeacherMessage or feedback
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

    /**
     * Synthesizes arbitrary text via TTS provider and returns audio + text.
     * Called when the frontend sends a "speak" command over WebSocket.
     */
    public void synthesizeText(String text, Consumer<SynthesizeResult> callback) {
        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(text) : null;
        callback.accept(new SynthesizeResult(audio, text));
    }

    public void endSession(String sessionId) {
        log.debug("Voice session ended: {}", sessionId);
    }
}
