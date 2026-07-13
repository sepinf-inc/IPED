package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.CONVERSATION_ACCOUNT;
import static iped.properties.ExtraProperties.CONVERSATION_ADMINS;
import static iped.properties.ExtraProperties.CONVERSATION_ID;
import static iped.properties.ExtraProperties.CONVERSATION_IS_OWNER_ADMIN;
import static iped.properties.ExtraProperties.CONVERSATION_MESSAGES_COUNT;
import static iped.properties.ExtraProperties.CONVERSATION_NAME;
import static iped.properties.ExtraProperties.CONVERSATION_PARTICIPANTS;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_ID;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_NAME;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_PHONE;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_USERNAME;
import static iped.properties.ExtraProperties.CONVERSATION_TYPE;
import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ContactPhoto;
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

        // Chat ID
         metadata.set(CONVERSATION_ID, model.getFieldId());

         // Chat Type
         metadata.set(CONVERSATION_TYPE, model.getChatType());

         // Chat Name
         if (StringUtils.isNotBlank(model.getName())) {
             metadata.set(CONVERSATION_NAME, model.getName());
         }

         // Chat Messages Count
         metadata.set(CONVERSATION_MESSAGES_COUNT, (int) model.getMessages().stream().filter(m -> !m.isSystemMessage()).count());

        // Chat Account Info
        metadata.set(CONVERSATION_ACCOUNT, model.getAccount());
        model.getReferencedAccount().ifPresent(a -> {
            metadata.set(CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_ID, a.getUserID());
            metadata.set(CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_NAME, a.getName());
            metadata.set(CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_PHONE, a.getPhoneNumber());
            metadata.set(CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_USERNAME, a.getUsername());
        });

        // Chat Participants
        model.getParticipants().forEach(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata(CONVERSATION_PARTICIPANTS, metadata);
        });
        if (!model.getParticipants().isEmpty()) {
            metadata.add(CONVERSATION_PARTICIPANTS + ":count", Integer.toString(model.getParticipants().size()));
        }

        // Chat Owner Participant
        model.getPhoneOwnerParticipant().ifPresent(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata(UFED_META_PREFIX + "PhoneOwner", metadata);
        });

        // Chat Group Admin Participants
        model.getParticipants().stream().filter(Party::isGroupAdmin).forEach(p -> {
            new PartyHandler(p, model.getSource()).fillMetadata(CONVERSATION_ADMINS, metadata);
        });
        long groupAdminsCount = model.getParticipants().stream().filter(Party::isGroupAdmin).count();
        if (groupAdminsCount > 0) {
            metadata.set(CONVERSATION_ADMINS + ":count", Long.toString(groupAdminsCount));
        }

        // Chat Owner is Group Admin
        if (model.getParticipants().stream().filter(Party::isGroupAdmin).filter(Party::isPhoneOwner).findAny().isPresent()) {
            metadata.set(CONVERSATION_IS_OWNER_ADMIN, true);
        }
    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

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
    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {

        model.getReferencedAccount().ifPresent(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
        });

        model.getPhotos().stream().map(ContactPhoto::getReferencedFile).filter(Optional::isPresent).forEach(ref -> {
            addLinkedItem(linkedItems, ref.get().getItem(), searcher);
        });
        model.getParticipants().stream().map(Party::getReferencedContact).filter(Optional::isPresent).forEach(ref -> {
            addLinkedItem(linkedItems, ref.get().getItem(), searcher);
        });

        // references to messages referenced items are done in UfedChatParser.storeLinkedHashes() per chat-preview
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
            if (StringUtils.isNotBlank(source))
                sb.append(source).append(' ');
            else if (StringUtils.isNotBlank(model.getServiceIdentifier()))
                sb.append(model.getServiceIdentifier()).append(' ');
            else
                sb.append("App:" + Messages.getString("UfedChatParser.Unknown")).append(' ');

            if (StringUtils.isNotBlank(chatType)) {
                switch (chatType) {
                    case Chat.TYPE_ONEONONE:
                        sb.append("Chat").append(' ');
                        break;

                    case Chat.TYPE_GROUP:
                        sb.append(Messages.getString("UfedChatParser.Group")).append(' ');
                        break;

                    case Chat.TYPE_BROADCAST:
                        if (model.getParticipants().size() == 1 && StringUtils.containsAnyIgnoreCase(source, Chat.SOURCE_TELEGRAM, Chat.SOURCE_WHATSAPP)) {
                            sb.append(Messages.getString("UfedChatParser.Status"));
                        } else if (Chat.SOURCE_TELEGRAM.equalsIgnoreCase(source)) {
                            sb.append(Messages.getString("UfedChatParser.Channel"));
                        } else {
                            sb.append(Messages.getString("UfedChatParser.Broadcast"));
                        }
                        sb.append(' ');
                        break;

                    case Chat.TYPE_UNKNOWN:
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

        if (model.getAccount() == null) {
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
        } else {
            logger.warn("User Account reference was not found: {}", account);
        }
    }


}