package iped.engine.rag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads an answer sent as server-sent events (Claude and OpenAI-compatible
 * APIs) and passes each piece of text on as it arrives.
 */
final class LLMEventStream {

    /** What a provider takes from each event of its stream. */
    interface Events {

        /**
         * @return the answer text in the event, or null when it has none
         * @throws IOException if the event reports an error
         */
        String text(JsonObject event) throws IOException;

        /** @return true if the event says generation stopped at the token limit */
        boolean cutOff(JsonObject event);
    }

    private LLMEventStream() {
    }

    static String read(InputStream body, Events events, Consumer<String> tokenConsumer)
            throws IOException, InterruptedException {
        StringBuilder answer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Stops reading (and paying for tokens) once the user cancels the question.
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || data.equals("[DONE]")) {
                    continue;
                }
                JsonObject event;
                try {
                    event = JsonParser.parseString(data).getAsJsonObject();
                } catch (RuntimeException e) {
                    continue;
                }
                append(answer, events.text(event), tokenConsumer);
                if (events.cutOff(event)) {
                    append(answer, LocalLLMProvider.truncationNotice(), tokenConsumer);
                }
            }
        }
        return answer.toString();
    }

    private static void append(StringBuilder answer, String text, Consumer<String> tokenConsumer) {
        if (text == null || text.isEmpty()) {
            return;
        }
        answer.append(text);
        if (tokenConsumer != null) {
            tokenConsumer.accept(text);
        }
    }

    /** @return the string at {@code key}, or null when it is missing or not a string */
    static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString()
                : null;
    }

    /** @return the object at {@code key}, or null when it is missing or not an object */
    static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
