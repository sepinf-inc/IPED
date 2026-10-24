package iped.engine.task.ner;

import java.util.Map;
import java.util.Set;

import iped.engine.config.NamedEntityTaskConfig;

/**
 * Interface for Named Entity Recognition implementations in IPED.
 */
public interface INamedEntityRecognizer {

    /**
     * Initializes the recognizer with the given configuration.
     *
     * @param config the NER task configuration
     * @throws Exception if initialization fails
     */
    void init(NamedEntityTaskConfig config) throws Exception;

    /**
     * Checks if the recognizer is available and ready for inference.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    /**
     * Recognizes named entities in the provided text for a specified language.
     *
     * @param text the text fragment to analyze
     * @param lang the detected language code (or "default")
     * @return a map from entity type (e.g. PERSON, LOCATION, ORGANIZATION) to a set of entity mentions
     * @throws Exception if recognition fails
     */
    Map<String, Set<String>> recognize(String text, String lang) throws Exception;

    /**
     * Releases any resources, child processes, or connections held by the recognizer.
     *
     * @throws Exception if cleanup fails
     */
    void finish() throws Exception;
}
