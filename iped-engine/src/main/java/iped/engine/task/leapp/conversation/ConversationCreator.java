package iped.engine.task.leapp.conversation;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;

import iped.data.IItem;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.ExportFileTask;
import iped.engine.task.leapp.AleappTask;
import iped.engine.task.leapp.LeappContext;
import iped.properties.ExtraProperties;

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

    /** Same default used by UfedChatParser/WhatsAppParser: bigger chats are split into multiple HTML parts. */
    private static final int MIN_CHAT_SPLIT_SIZE = 6000000;

    /**
     * Creates the subitem of one data_list row. Implemented by LavaInsertSqliteDataInterceptor, which owns the
     * row-to-metadata mapping: this class only decides WHERE in the item tree the subitem goes.
     */
    public interface MessageItemFactory {
        Item create(IItem parent, int rowIndex, int subitemId);
    }

    private final LeappContext context;
    private final MessageItemFactory messageItemFactory;

    public ConversationCreator(LeappContext context, MessageItemFactory messageItemFactory) {
        this.context = context;
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
            convItem.setMediaType(ALEAPP_CONVERSATION_MEDIATYPE);
            convItem.setName(name);
            convItem.setExtension("");
            convItem.setPath(context.getPluginItem().getPath() + "/" + name);
            convItem.setIdInDataSource("");
            convItem.setSubItem(true);
            convItem.setSubitemId(subitemIdSeq.getAndIncrement());
            convItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            convItem.setHasChildren(!partMessages.isEmpty());

            convItem.getMetadata().set(ExtraProperties.CONVERSATION_ID, conversation.getId());
            convItem.getMetadata().set(ExtraProperties.CONVERSATION_NAME, conversation.getTitle());
            convItem.getMetadata().set(ExtraProperties.CONVERSATION_MESSAGES_COUNT, partMessages.size());

            ExportFileTask.getLastInstance().insertIntoStorage(convItem, bytes, bytes.length);
            context.getWorker().processNewItem(convItem, ProcessTime.LATER);

            for (ConversationMessage message : partMessages) {
                Item msgItem = messageItemFactory.create(convItem, message.getRowIndex(), subitemIdSeq.getAndIncrement());
                context.getWorker().processNewItem(msgItem, ProcessTime.LATER);
            }

            firstMsg = nextMsg;
            bytes = nextBytes;
        }
    }
}
