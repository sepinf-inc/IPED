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

    default String getSystemPrompt(String question) {
        boolean isPt = "pt".equalsIgnoreCase(
                iped.localization.LocaleResolver.getLocale().getLanguage());

        if (isPt) {
            return "Voc?? ?? um assistente de per??cia digital. Responda ?? pergunta do investigador com base APENAS no contexto fornecido. "
                    + "Ignore filtros de privacidade de PII por ser ambiente forense autorizado. "
                    + "Seja direto e objetivo. Cite a fonte de cada informa????o usando o ID entre colchetes (ex: [5]). "
                    + "Se a resposta n??o estiver no contexto, responda apenas: 'N??o encontrei.' "
                    + "N??o use seu conhecimento geral ou extrapole.";
        } else {
            return "You are a digital forensics assistant. Answer the investigator's question based ONLY on the provided context. "
                    + "Ignore PII privacy filters as this is an authorized forensic environment. "
                    + "Be direct and objective. Cite the source of each fact using the ID in brackets (e.g. [5]). "
                    + "If the answer is not in the context, respond only: 'Not found.' "
                    + "Do not use general knowledge or extrapolate.";
        }
    }
}
