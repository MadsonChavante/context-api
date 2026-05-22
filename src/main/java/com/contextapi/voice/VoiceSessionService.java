package com.contextapi.voice;

import com.contextapi.providers.AiProvider;
import com.contextapi.providers.SpeechToTextProvider;
import com.contextapi.providers.TextToSpeechProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class VoiceSessionService {

    public record ProcessResult(byte[] audio, String transcript, String responseText) {}
    public record GreetingResult(byte[] audio, String greetingText) {}

    private final AiProvider aiProvider;
    private final SpeechToTextProvider sttProvider;
    private final TextToSpeechProvider ttsProvider;
    private final Map<String, List<VoiceMessage>> conversationHistory = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            You are a friendly and encouraging English teacher (professor de ingles) helping a Brazilian student.
            Always refer to yourself in first person as "I" or "me" or "your teacher".
            Keep your responses short (2-3 sentences max), in Portuguese mixed with English when helpful.
            Be supportive and correct gently when the student makes mistakes.
            Focus on practical, everyday English that the student can use immediately.
            If the student asks about grammar, explain briefly with a simple example.
            """;

    private static final String GREETING_PROMPT = """
            You are an English teacher called "Teacher" starting a voice conversation with a Brazilian student.
            Greet the student warmly in first person (I am your teacher, my name is Teacher).
            Say 2 short sentences: introduce yourself as the English teacher and ask how they are.
            Mix Portuguese and English naturally. Be warm and encouraging.
            """;

    public VoiceSessionService(AiProvider aiProvider,
                                SpeechToTextProvider sttProvider,
                                TextToSpeechProvider ttsProvider) {
        this.aiProvider = aiProvider;
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
    }

    /**
     * Starts a new voice session with an AI-generated greeting.
     */
    public void startSession(String sessionId, Consumer<GreetingResult> callback) {
        List<VoiceMessage> history = conversationHistory
                .computeIfAbsent(sessionId, k -> new ArrayList<>());

        String greeting = aiProvider.complete(GREETING_PROMPT, 100, 0.7);
        if (greeting == null || greeting.isBlank()) {
            greeting = "Hello! I'm your English teacher. How are you today?";
        }
        greeting = greeting.trim();
        history.add(new VoiceMessage("assistant", greeting));

        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(greeting) : null;
        callback.accept(new GreetingResult(audio, greeting));
    }

    /**
     * Processes a voice input from the user. The callback receives a ProcessResult
     * containing the user transcript, teacher response text, and optionally TTS audio.
     */
    public void processVoiceInput(String sessionId, byte[] audioData,
                                   Consumer<ProcessResult> callback) {
        String transcript;
        if (sttProvider.isConfigured()) {
            transcript = sttProvider.transcribe(audioData, "webm");
            if (transcript == null || transcript.isBlank()) {
                callback.accept(new ProcessResult(null, "", "Nao entendi. Pode repetir?"));
                return;
            }
        } else {
            callback.accept(new ProcessResult(null, "", "STT nao configurado. Use o teclado por enquanto."));
            return;
        }

        List<VoiceMessage> history = conversationHistory
                .computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new VoiceMessage("user", transcript));

        String aiResponse = generateTeacherResponse(history, transcript);
        history.add(new VoiceMessage("assistant", aiResponse));

        if (history.size() > 20) {
            history.subList(0, 2).clear();
        }

        byte[] audio = ttsProvider.isConfigured() ? ttsProvider.synthesize(aiResponse) : null;
        callback.accept(new ProcessResult(audio, transcript, aiResponse));
    }

    private String generateTeacherResponse(List<VoiceMessage> history, String transcript) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        prompt.append("\n\nConversation history:\n");
        for (VoiceMessage msg : history) {
            prompt.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }

        String response = aiProvider.complete(prompt.toString(), 200, 0.8);
        if (response == null || response.isBlank()) {
            return "Desculpe, nao consegui processar sua pergunta. Tente novamente!";
        }
        return response.trim();
    }

    public void endSession(String sessionId) {
        conversationHistory.remove(sessionId);
        log.debug("Voice session ended: {}", sessionId);
    }

    private record VoiceMessage(String role, String content) {}
}
