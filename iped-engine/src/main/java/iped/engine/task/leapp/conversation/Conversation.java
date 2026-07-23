package iped.engine.task.leapp.conversation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * A conversation assembled from the data_list rows sharing the same discriminator column value.
 */
public class Conversation {

    /** Value of the conversationDiscriminatorColumn (e.g. a Discord channel id). */
    private final String id;

    /** Value of the conversationLabelColumn, when declared: a human-friendly conversation name. */
    private final String label;

    /** Artifact (display) name of the plugin that produced the rows. */
    private final String artifactName;

    private final List<ConversationMessage> messages = new ArrayList<>();

    public Conversation(String id, String label, String artifactName) {
        this.id = id;
        this.label = label;
        this.artifactName = artifactName;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getTitle() {
        return StringUtils.firstNonBlank(label, id, "?");
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    /**
     * Sorts messages chronologically (stable: rows without a parseable timestamp keep their original relative order,
     * before dated ones, like a null date sorts first elsewhere in IPED).
     */
    public void sortMessages() {
        messages.sort(Comparator.comparing(ConversationMessage::getTimestamp,
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
