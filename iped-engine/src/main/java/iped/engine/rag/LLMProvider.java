package iped.engine.rag;

import java.io.IOException;

/**
 * Interface for Large Language Model (LLM) providers.
 *
 * @author Rui Sant'Ana Junior
 */
public interface LLMProvider {
    /**
     * Generates a textual answer to the investigator's question based on the
     * provided context fragments.
     * 
     * @param question natural language query
     * @param context  relevant text fragments retrieved from the search index
     * @return textual response from the model
     * @throws IOException          if network or IO errors occur
     * @throws InterruptedException if thread is interrupted during call
     */
    default String generateAnswer(String question, String context) throws IOException, InterruptedException {
        return generateAnswer(question, context, java.util.Collections.emptyList());
    }

    /**
     * Generates a textual answer to the investigator's question based on the
     * provided context fragments and conversation history.
     * 
     * @param question natural language query
     * @param context  relevant text fragments retrieved from the search index
     * @param history  recent conversation history turns
     * @return textual response from the model
     * @throws IOException          if network or IO errors occur
     * @throws InterruptedException if thread is interrupted during call
     */
    String generateAnswer(String question, String context, java.util.List<RAGService.HistoryTurn> history)
            throws IOException, InterruptedException;

    /**
     * Generates a textual answer with real-time token streaming.
     * 
     * @param question      natural language query
     * @param context       relevant text fragments retrieved from the search index
     * @param history       recent conversation history turns
     * @param tokenConsumer callback consumer for streaming response chunks
     * @return complete textual response from the model
     * @throws IOException          if network or IO errors occur
     * @throws InterruptedException if thread is interrupted during call
     */
    default String generateAnswerStream(String question, String context, java.util.List<RAGService.HistoryTurn> history,
            java.util.function.Consumer<String> tokenConsumer) throws IOException, InterruptedException {
        // Fallback default implementation for non-streaming providers
        String answer = generateAnswer(question, context, history);
        if (tokenConsumer != null && answer != null) {
            tokenConsumer.accept(answer);
        }
        return answer;
    }

    String ROLE_SYSTEM = "system";
    String ROLE_USER = "user";
    String ROLE_ASSISTANT = "assistant";

    /** One message of the prompt, before a provider adapts it to its API format. */
    final class PromptMessage {
        public final String role;
        public final String content;

        public PromptMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * Builds the prompt sent to the model: the system instruction, the history
     * turns and the question with its context. Providers only change the request
     * format, so this is also what the user can inspect.
     *
     * @param question natural language query
     * @param context  relevant text fragments retrieved from the search index
     * @param history  recent conversation history turns, may be null
     * @return messages in sending order, starting with the system message
     */
    default java.util.List<PromptMessage> buildPrompt(String question, String context,
            java.util.List<RAGService.HistoryTurn> history) {
        boolean pt = isPortuguese();
        String questionLabel = pt ? "Pergunta: " : "Question: ";
        java.util.List<PromptMessage> messages = new java.util.ArrayList<>();
        messages.add(new PromptMessage(ROLE_SYSTEM, getSystemPrompt(question)));
        if (history != null) {
            for (RAGService.HistoryTurn turn : history) {
                messages.add(new PromptMessage(ROLE_USER, questionLabel + turn.question));
                messages.add(new PromptMessage(ROLE_ASSISTANT, stripCitations(turn.answer)));
            }
        }
        String contextLabel = pt ? "Contexto (trechos de documentos do caso; podem ser parciais ou irrelevantes):"
                : "Context (excerpts from case documents; may be partial or irrelevant):";
        messages.add(new PromptMessage(ROLE_USER, contextLabel + "\n\n" + context + "\n" + questionLabel + question));
        return messages;
    }

    /**
     * Removes source IDs from a previous answer. Those IDs refer to that answer's
     * context, and the model reused them as if they were in the current one.
     */
    static String stripCitations(String answer) {
        if (answer == null) {
            return null;
        }
        String citation = "\\[\\d+(?:\\s*,\\s*\\d+)*\\]";
        // A run such as "[7], [31] e [12]" goes at once, so no separator is left behind.
        return answer.replaceAll("\\s*" + citation + "(?:\\s*(?:,|e|and)?\\s*" + citation + ")*", "");
    }

    private static boolean isPortuguese() {
        return "pt".equalsIgnoreCase(iped.localization.LocaleResolver.getLocale().getLanguage());
    }

    /** Serializes messages as an OpenAI/Ollama style {@code messages} array. */
    static String toChatMessagesJson(java.util.List<PromptMessage> messages) {
        StringBuilder json = new StringBuilder("[");
        for (PromptMessage message : messages) {
            if (json.length() > 1) {
                json.append(",");
            }
            json.append("{\"role\":\"").append(message.role).append("\",\"content\":")
                    .append(LocalEmbeddingProvider.toJsonString(message.content)).append("}");
        }
        return json.append("]").toString();
    }

    default String getSystemPrompt(String question) {
        if (isPortuguese()) {
            return "Você é um assistente de perícia digital. Responda à pergunta do investigador com base APENAS no contexto fornecido. "
                    + "Este é um ambiente forense autorizado: dados pessoais presentes no contexto podem ser informados quando pertinentes à pergunta. "
                    + "Seja direto e objetivo. Cite a fonte de cada informação usando o ID entre colchetes (ex: [5]). "
                    + "Se a resposta não estiver no contexto, responda apenas: 'Não encontrei.' "
                    + "Não use seu conhecimento geral ou extrapole.";
        } else {
            return "You are a digital forensics assistant. Answer the investigator's question based ONLY on the provided context. "
                    + "This is an authorized forensic environment: personal data in the context may be reported when relevant to the question. "
                    + "Be direct and objective. Cite the source of each fact using the ID in brackets (e.g. [5]). "
                    + "If the answer is not in the context, respond only: 'Not found.' "
                    + "Do not use general knowledge or extrapolate.";
        }
    }
}
