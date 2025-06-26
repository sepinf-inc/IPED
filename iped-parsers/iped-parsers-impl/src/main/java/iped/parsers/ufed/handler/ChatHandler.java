package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Party;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.WAContact;
import iped.properties.BasicProps;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;

/**
 * Handles all processing logic for a Chat model.
 */
public class ChatHandler extends BaseModelHandler<Chat> {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);

    public ChatHandler(Chat model, IItemReader modelItem) {
        super(model, modelItem);
    }

    public ChatHandler(Chat model) {
        super(model);
    }

    @Override
    public void fillMetadata(String prefix, Metadata metadata) {

        super.fillMetadata(prefix, metadata);

        model.getParticipants().forEach(p -> {
            new PartyHandler(p,  item).fillMetadata("Participants", metadata);
        });

        model.getPhoneOwnerParticipant().ifPresent(p -> {
            new PartyHandler(p,  item).fillMetadata("PhoneOwner", metadata);
        });

        model.getParticipants().stream().filter(Party::isGroupAdmin).forEach(p -> {
            new PartyHandler(p,  item).fillMetadata("GroupAdmins", metadata);
        });
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {

        loadChatAccountReference(searcher);

        model.getPhotos().forEach(photo -> {
            new ContactPhotoHandler(photo).loadReferences(searcher);
        });

        Map<String, IItemReader> participantsCache = new HashMap<>();

        model.getParticipants().forEach(participant -> {
            new PartyHandler(participant, item, participantsCache).loadReferences(searcher);
        });

        model.getMessages().forEach(m -> {
            new InstantMessageHandler(m, item, participantsCache).loadReferences(searcher);
        });
    }

    @Override
    public String getTitle() {
        return getTitle(true, true);
    }

    public String getTitle(boolean outputPrefix, boolean outputOwnerParticipant) {
        String source = model.getSource();
        String chatType = model.getChatType();
        String name = model.getName();
        String chatId = model.getFieldId();
        String ufedId = model.getId();

        StringBuilder sb = new StringBuilder();
        if (outputPrefix) {
            sb.append(source).append(' ');

            if (chatType != null) {
                switch (chatType) {
                case Chat.TYPE_ONEONONE:
                    sb.append("Chat").append(' ');
                    break;

                case Chat.TYPE_GROUP:
                    sb.append(Messages.getString("UFEDChatParser.Group")).append(' ');
                    break;

                case Chat.TYPE_BROADCAST:
                    if (model.getParticipants().size() == 1 && StringUtils.containsAnyIgnoreCase(source, Chat.SOURCE_TELEGRAM, Chat.SOURCE_WHATSAPP)) {
                        sb.append(Messages.getString("UFEDChatParser.Status"));
                    } else if (Chat.SOURCE_TELEGRAM.equalsIgnoreCase(source)) {
                        sb.append(Messages.getString("UFEDChatParser.Channel"));
                    } else {
                        sb.append(Messages.getString("UFEDChatParser.Broadcast"));
                    }
                    sb.append(' ');
                    break;

                case Chat.TYPE_UNKNOWN:
                    sb.append(Messages.getString("UFEDChatParser.Unknown")).append(' ');
                    break;

                default:
                    sb.append(chatType).append(' ');
                    break;
                }
            }
            sb.append("- ");
        }

        Party ownerPartipant = model.getPhoneOwnerParticipant().orElse(null);
        List<Party> otherParticipants = model.getOtherParticipants();

        if (name != null) {
            sb.append(name);
        } else if (otherParticipants.size() == 1) {

            // append other participant
            if (outputOwnerParticipant) {
                sb.append("[");
            }
            sb.append(new PartyHandler(otherParticipants.get(0), item).getTitle());
            if (outputOwnerParticipant) {
                sb.append("]");
            }

            // append owner participant
            if (ownerPartipant != null && outputOwnerParticipant) {
                sb.append(" - [").append(new PartyHandler(ownerPartipant, item).getTitle()).append("]");
            }
        } else if (chatId != null) {
            sb.append("[ID:").append(chatId).append("]");
        } else if (ufedId != null) {
            sb.append(ufedId);
        }

        String result = sb.toString();
        if (StringUtils.containsIgnoreCase(source, Chat.SOURCE_WHATSAPP)) {
            result = StringUtils.remove(result, WAContact.waSuffix);
        }

        return result;
    }


    private void loadChatAccountReference(IItemSearcher searcher) {

        if (model.getReferencedAccount() != null || model.getAccount() == null) {
            return;
        }

        String account = model.getAccount();
        String query = createUserAccountQuery(account, model, item, searcher);
        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one account for [{}]: {}", account, results);
            }
            model.setReferencedAccount(results.get(0));
            return;
        }

        logger.error("User Account reference was not found: {}", account);
    }

    public static String createUserAccountQuery(String identifier, BaseModel relatedModel, IItemReader relatedItem, IItemSearcher searcher) {

        // also use node of possible jid (e.g. "node@domain.com" -> "node")
        String identifierNode = StringUtils.substringBefore(identifier, '@');
        String possiblePhoneNumber = null;
        if (!identifierNode.isBlank() && !identifierNode.equals(identifier)) {
            possiblePhoneNumber = identifierNode;
        }

        String source = (String) relatedModel.getField("Source");

        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_USER_ACCOUNT_MIME.toString() + "\"" //
                + " && " + BasicProps.EVIDENCE_UUID + ":\"" + relatedItem.getDataSource().getUUID() + "\"" //
                + " && " + searcher.escapeQuery(UFED_META_PREFIX + "extractionId") + ":" + relatedModel.getExtractionId()  //
                + " && " + searcher.escapeQuery(UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && (" + searcher.escapeQuery(UFED_META_PREFIX + "UserID") + ":\"" + identifier + "\"" //
                +   ((possiblePhoneNumber != null) ?
                        (" || " + searcher.escapeQuery(UFED_META_PREFIX + "PhoneNumber") + ":\"" + possiblePhoneNumber + "\"") : "") //
                + " || " + searcher.escapeQuery(UFED_META_PREFIX + "Username") + ":\"" + identifier + "\"" //
                + ")";

        return query;
    }
}