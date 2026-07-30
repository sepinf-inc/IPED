package iped.engine.task.leapp.conversation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.data.IItem;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.ExportFileTask;
import iped.engine.task.leapp.AleappTask;
import iped.engine.task.leapp.LeappContext;
import iped.parsers.util.ConversationConstants;
import iped.properties.ExtraProperties;
import iped.utils.DateUtil;

/**
 * Turns the {@link Conversation}s of one plugin run into case items: for each conversation (or part of it, when the
 * HTML is split by size) one chat-preview item is created as child of the plugin evidence, its HTML rendered by
 * {@link ConversationHtmlReportGenerator} and stored via {@link ExportFileTask#insertIntoStorage}; the row subitems of
 * the messages are then created as children of the part item they were rendered into, mirroring the UFED chat
 * structure (chat preview → message subitems).
 */
public class ConversationCreator {

    public static final MediaType ALEAPP_CONVERSATION_MEDIATYPE = MediaType
            .application(AleappTask.ALEAPP_APPLICATION_PREFIX + "chat-preview");

    /**
     * App-specific chat-preview media types, mirroring UfedChatParser: they carry the source app in the mime so
     * IconManager and CategoriesConfig can give conversations the proper app icon/category. All of them MUST be
     * registered in CustomSignatures.xml as sub-class-of x-aleapp-chat-preview, whose parent x-preview-with-links
     * routes them to HtmlLinkViewer (enabling the app.open/app.check javascript bridge).
     */
    private static final Map<String, MediaType> APP_PREVIEW_MEDIATYPES = Map.ofEntries( //
            Map.entry("whatsapp", previewType("whatsapp")), //
            Map.entry("telegram", previewType("telegram")), //
            Map.entry("skype", previewType("skype")), //
            Map.entry("facebook", previewType("facebook")), //
            Map.entry("instagram", previewType("instagram")), //
            Map.entry("signal", previewType("signal")), //
            Map.entry("snapchat", previewType("snapchat")), //
            Map.entry("threema", previewType("threema")), //
            Map.entry("tiktok", previewType("tiktok")), //
            Map.entry("viber", previewType("viber")), //
            Map.entry("discord", previewType("discord")));

    private static MediaType previewType(String app) {
        return MediaType.application(AleappTask.ALEAPP_APPLICATION_PREFIX + "chat-preview-" + app);
    }

    /** Same default used by UfedChatParser/WhatsAppParser: bigger chats are split into multiple HTML parts. */
    private static final int MIN_CHAT_SPLIT_SIZE = 6000000;

    /**
     * Creates the subitem of one data_list row. Implemented by PluginResultsProcessor, which owns the
     * row-to-metadata mapping: this class only decides WHERE in the item tree the subitem goes.
     */
    public interface MessageItemFactory {
        Item create(IItem parent, int rowIndex, int subitemId);
    }

    private final LeappContext context;
    private final ConversationViewSpec view;
    private final MessageItemFactory messageItemFactory;

    public ConversationCreator(LeappContext context, ConversationViewSpec view, MessageItemFactory messageItemFactory) {
        this.context = context;
        this.view = view;
        this.messageItemFactory = messageItemFactory;
    }

    public void createConversations(List<Conversation> conversations, AtomicInteger subitemIdSeq) throws Exception {
        for (Conversation conversation : conversations) {
            createConversation(conversation, subitemIdSeq);
        }
    }

    private void createConversation(Conversation conversation, AtomicInteger subitemIdSeq) throws Exception {

        conversation.sortMessages();

        ConversationHtmlReportGenerator generator = new ConversationHtmlReportGenerator(conversation, MIN_CHAT_SPLIT_SIZE);

        byte[] bytes = generator.generateNextChatHtml();
        int frag = 0;
        int firstMsg = 0;

        while (bytes != null) {
            int nextMsg = generator.getNextMsgNum();
            byte[] nextBytes = generator.generateNextChatHtml();

            String name = conversation.getArtifactName() + " - " + conversation.getTitle();
            if (frag > 0 || nextBytes != null) {
                // fragment naming mirroring UfedChatParser/WhatsAppParser
                name += "_" + (++frag);
            }

            List<ConversationMessage> partMessages = conversation.getMessages().subList(firstMsg, nextMsg);

            Item convItem = (Item) context.getPluginItem().createChildItem();
            convItem.setMediaType(resolvePreviewMediaType());
            convItem.setName(name);
            convItem.setExtension("");
            convItem.setPath(context.getPluginItem().getPath() + "/" + name);
            convItem.setIdInDataSource("");
            convItem.setSubItem(true);
            convItem.setSubitemId(subitemIdSeq.getAndIncrement());
            convItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            convItem.setHasChildren(!partMessages.isEmpty());

            // standard cross-parser "Conversation:" metadata, as set by other chat
            // parsers (e.g. iped.parsers.ufed.handler.ChatHandler)
            convItem.getMetadata().set(ExtraProperties.CONVERSATION_ID, conversation.getId());
            convItem.getMetadata().set(ExtraProperties.CONVERSATION_NAME, conversation.getTitle());
            convItem.getMetadata().set(ExtraProperties.CONVERSATION_MESSAGES_COUNT, partMessages.size());
            Set<String> participants = conversation.getParticipants();
            for (String participant : participants) {
                convItem.getMetadata().add(ExtraProperties.CONVERSATION_PARTICIPANTS, participant);
            }
            if (!participants.isEmpty()) {
                convItem.getMetadata().add(ExtraProperties.CONVERSATION_PARTICIPANTS + ":count",
                        Integer.toString(participants.size()));
            }

            ExportFileTask.getLastInstance().insertIntoStorage(convItem, bytes, bytes.length);
            context.getWorker().processNewItem(convItem, ProcessTime.LATER);

            for (ConversationMessage message : partMessages) {
                Item msgItem = messageItemFactory.create(convItem, message.getRowIndex(), subitemIdSeq.getAndIncrement());
                setCommunicationMetadata(msgItem, message, conversation);
                context.getWorker().processNewItem(msgItem, ProcessTime.LATER);
            }

            firstMsg = nextMsg;
            bytes = nextBytes;
        }
    }

    /**
     * Standard cross-parser "Communication:" metadata on a message subitem, as set by other chat parsers (e.g.
     * iped.parsers.ufed.handler.InstantMessageHandler). The row cells mapped by column classification
     * (Communication:From/To/Date) may already be present: the data view mapping only fills the gaps, so nothing is
     * overwritten.
     */
    private void setCommunicationMetadata(Item msgItem, ConversationMessage message, Conversation conversation) {

        Metadata metadata = msgItem.getMetadata();

        if (message.getOutgoing() != null) {
            metadata.set(ExtraProperties.COMMUNICATION_DIRECTION, message.isOutgoing()
                    ? ConversationConstants.DIRECTION_OUTGOING
                    : ConversationConstants.DIRECTION_INCOMING);
            removeAleappMetadata(metadata, view.getDirectionColumn());
        }

        if (metadata.get(ExtraProperties.COMMUNICATION_FROM) == null && StringUtils.isNotBlank(message.getSender())) {
            metadata.set(ExtraProperties.COMMUNICATION_FROM, message.getSender());
            removeAleappMetadata(metadata, view.getSenderColumn());
        }

        // no per-row recipient in LEAPP data: the conversation itself is the
        // destination, mirroring what InstantMessageHandler does for group chats
        if (metadata.get(ExtraProperties.COMMUNICATION_TO) == null) {
            metadata.set(ExtraProperties.COMMUNICATION_TO, conversation.getTitle());
            metadata.add(ExtraProperties.COMMUNICATION_TO + ExtraProperties.CONVERSATION_SUFFIX_ID, conversation.getId());
            // the discriminator/label cells became the chat's Conversation:id/Name
            // and this message's Communication:To
            removeAleappMetadata(metadata, view.getDiscriminatorColumn());
            removeAleappMetadata(metadata, view.getLabelColumn());
        }

        if (metadata.get(ExtraProperties.MESSAGE_DATE) == null && message.getTimestamp() != null) {
            metadata.set(ExtraProperties.MESSAGE_DATE, DateUtil.dateToString(message.getTimestamp()));
            removeAleappMetadata(metadata, view.getTimeColumn());
        }

        if (metadata.get(ExtraProperties.MESSAGE_BODY) == null && StringUtils.isNotBlank(message.getBody())) {
            metadata.set(ExtraProperties.MESSAGE_BODY, message.getBody());
            removeAleappMetadata(metadata, view.getTextColumn());
        }
    }

    /** Values promoted to a standard property are not kept duplicated under the "aleapp:" prefix. */
    private static void removeAleappMetadata(Metadata metadata, String column) {
        if (column != null) {
            metadata.remove(AleappTask.ALEAPP_METADATA_PREFIX + column);
        }
    }

    /**
     * Resolves the app-specific chat-preview media type by looking for a known app keyword in the plugin module name
     * (e.g. "discordChats") or artifact name (e.g. "Discord Chats"); falls back to the generic type.
     */
    private MediaType resolvePreviewMediaType() {
        String hint = (context.getPlugin().getModuleName() + " " + context.getPlugin().getName())
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, MediaType> entry : APP_PREVIEW_MEDIATYPES.entrySet()) {
            if (hint.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return ALEAPP_CONVERSATION_MEDIATYPE;
    }
}
