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
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Party;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.WAContact;
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

        // Chat Account Info
        model.getReferencedAccount().ifPresent(a -> {
            metadata.add(UFED_META_PREFIX + "Account:id", a.getUserID());
            metadata.add(UFED_META_PREFIX + "Account:name", a.getName());
            metadata.add(UFED_META_PREFIX + "Account:phoneNumber", a.getPhoneNumber());
            metadata.add(UFED_META_PREFIX + "Account:username", a.getUsername());
        });

        // Chat Participants
        model.getParticipants().forEach(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata("Participants", metadata);
        });
        if (!model.getParticipants().isEmpty()) {
            metadata.add(UFED_META_PREFIX + "Participants:count", Integer.toString(model.getParticipants().size()));
        }

        // Chat Owner Participant
        model.getPhoneOwnerParticipant().ifPresent(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata("PhoneOwner", metadata);
        });

        // Chat Group Admin Participants
        model.getParticipants().stream().filter(Party::isGroupAdmin).forEach(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata("GroupAdmins", metadata);
        });
        long groupAdminsCount = model.getParticipants().stream().filter(Party::isGroupAdmin).count();
        if (groupAdminsCount > 0) {
            metadata.add(UFED_META_PREFIX + "GroupAdmins:count", Long.toString(groupAdminsCount));
        }
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {

        loadChatAccountReference(searcher);

        model.getPhotos().forEach(photo -> {
            new ContactPhotoHandler(photo).loadReferences(searcher);
        });

        Map<String, IItemReader> participantsCache = new HashMap<>();

        model.getParticipants().forEach(participant -> {
            new PartyHandler(participant, model.getSource(), item, participantsCache).loadReferences(searcher);
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

            outputOwnerParticipant = outputOwnerParticipant && ownerPartipant != null;
            String ownerParticipantStr = "";
            if (outputOwnerParticipant) {
                ownerParticipantStr = new PartyHandler(ownerPartipant, model.getSource()).getTitle();
                outputOwnerParticipant = StringUtils.isNotBlank(ownerParticipantStr);
            }

            // append other participant
            String otherParticipantStr = new PartyHandler(otherParticipants.get(0), model.getSource()).getTitle();
            boolean otherSurroundedByBrackets = otherParticipantStr.startsWith("[") && otherParticipantStr.endsWith("]");
            if (outputOwnerParticipant && !otherSurroundedByBrackets) {
                sb.append("[");
            }
            sb.append(otherParticipantStr);
            if (outputOwnerParticipant && !otherSurroundedByBrackets) {
                sb.append("]");
            }

            // append owner participant
            if (outputOwnerParticipant) {
                boolean ownerSurroundedByBrackets = ownerParticipantStr.startsWith("[") && ownerParticipantStr.endsWith("]");

                sb.append(" - ");
                if (!ownerSurroundedByBrackets) {
                    sb.append("[");
                }
                sb.append(ownerParticipantStr);
                if (!ownerSurroundedByBrackets) {
                    sb.append("]");
                }
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

        if (model.getReferencedAccount().isPresent() || model.getAccount() == null) {
            return;
        }

        String account = model.getAccount();
        String query = AccountableHandler.createAccountableQuery(account, model.getSource(), MediaTypes.UFED_USER_ACCOUNT_MIME, model, item, searcher);
        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one account for [{}]: {}", account, results);
            }
            model.setReferencedAccount(results.get(0));
            return;
        }
        logger.warn("User Account reference was not found: {}", account);
    }


}