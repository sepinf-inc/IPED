package iped.parsers.ufed.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.JumpTarget;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.model.ReplyMessageData;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;

public class UfedModelReferenceLoader {

    private static Logger logger = LoggerFactory.getLogger(UfedModelReferenceLoader.class);

    private BaseModel model;

    public static UfedModelReferenceLoader build(BaseModel model) {
        return new UfedModelReferenceLoader(model);
    }

    private UfedModelReferenceLoader(BaseModel model) {
        this.model = model;
    }

    public void load(IItemSearcher searcher) {

        if (model instanceof Chat) {
            loadChatReferences((Chat) model, searcher);

        } else if (model instanceof InstantMessage) {
            loadInstantMessageReferences((InstantMessage) model, searcher);

        } else if (model instanceof Contact) {
            loadContactReference((Contact) model, searcher);

        }
    }

    private void loadChatReferences(Chat chat, IItemSearcher searcher) {

        // load chat account
        loadAccountReference(chat, searcher);

        // load participants
        Map<String, IItemReader> participantsCache = new HashMap<>();
        chat.getParticipants().forEach(participant -> {
            loadPartyReference(participant, searcher, chat, participantsCache);
        });

        // load photo thumb
        chat.getPhotos().stream().forEach(p -> {
            loadContactPhotoData(p, searcher);
        });

        // load message items
        chat.getMessages().stream().forEach(m -> {
            loadInstantMessageReferences(m, searcher, chat, participantsCache);
        });
    }

    private void loadAccountReference(Chat chat, IItemSearcher searcher) {
        String account = chat.getAccount();
        if (StringUtils.isBlank(account)) {
            return;
        }

        String source = chat.getSource();
        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_USER_ACCOUNT_MIME.toString() + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && (" + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "UserID") + ":\"" + account + "\"" //
                + " || " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "PhoneNumber") + ":\"" + account
                + "\"" //
                + " || " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Username") + ":\"" + account + "\"" //
                + ")";

        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one account for [{}]: {}", account, results);
            }
            chat.setReferencedAccount(results.get(0));
        }
    }

    private void loadContactPhotoData(ContactPhoto photo, IItemSearcher searcher) {
        if (photo.getPhotoNodeId() == null) {
            return;
        }

        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + photo.getPhotoNodeId() + "\"";
        List<IItemReader> result = searcher.search(query);
        if (!result.isEmpty()) {
            IItemReader contactPhoto = result.get(0);
            photo.setImageData(contactPhoto.getThumb());
        }
    }

    private void loadInstantMessageReferences(InstantMessage message, IItemSearcher searcher) {
        Map<String, IItemReader> participantsCache = new HashMap<>();
        loadInstantMessageReferences(message, searcher, message.getChat(), participantsCache);
    }


    private void loadInstantMessageReferences(InstantMessage message, IItemSearcher searcher, Chat chat, Map<String, IItemReader> cache) {

        message.getFrom().ifPresent(from -> {
            loadPartyReference(from, searcher, chat, cache);
        });
        message.getTo().forEach(to -> {
            loadPartyReference(to, searcher, chat, cache);
        });
        message.getAttachments().stream().forEach(a -> {
            loadAttachmentReference(a, searcher);
        });
        message.getSharedContacts().stream().forEach(c -> {
            loadContactReference(c, searcher);
        });
        loadLocationReference(message, searcher);
        message.getEmbeddedMessage().ifPresent(em -> {
            loadInstantMessageReferences(em, searcher, chat, cache);
        });
        message.getExtraData().getReplyMessage().map(ReplyMessageData::getInstantMessage).ifPresent(rm -> {
            loadInstantMessageReferences(rm, searcher, chat, cache);
        });
    }

    private void loadPartyReference(Party party, IItemSearcher searcher, Chat chat, Map<String, IItemReader> cache) {

        String identifier = party.getIdentifier();
        if (cache.containsKey(identifier)) {
             party.setReferencedContact(cache.get(identifier));
             return;
        }

        String account = chat.getAccount();
        String source = chat.getSource();
        String query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_CONTACT_MIME.toString() + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Account") + ":\"" + account + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Source") + ":\"" + source + "\"" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "Type") + ":ChatParticipant" //
                + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "UserID") + ":\"" + identifier + "\"";

        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            if (results.size() > 1) {
                logger.warn("Found more than one participant for [{}]: {}", account, results);
            }
            IItemReader result = results.get(0);
            cache.put(identifier, result);
            party.setReferencedContact(result);
        }
    }

    private void loadAttachmentReference(Attachment attachment, IItemSearcher searcher) {
        if (attachment.getFileId() == null) {
            return;
        }

        // attachment "ufed:file_id" metadata contains the "ufed:id" metadata of the file
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + attachment.getFileId() + "\"";
        List<IItemReader> fileItems = searcher.search(query);
        if (!fileItems.isEmpty()) {
            if (fileItems.size() > 1) {
                logger.warn("Found more than 1 file for attachment: {}", fileItems);
            }
            attachment.setReferencedFile(fileItems.get(0));
        }
    }

    private void loadContactReference(Contact contact, IItemSearcher searcher) {
        if (contact.getId() == null) {
            return;
        }

        // shared contact and indexed contact have the same "ufed:id" metadata
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + contact.getId() + "\"";
        List<IItemReader> contactItems = searcher.search(query);
        if (!contactItems.isEmpty()) {
            if (contactItems.size() > 1) {
                logger.warn("Found more than 1 contact for shared contact: {}", contactItems);
            }
            contact.setReferencedContact(contactItems.get(0));
        }
    }

    private void loadLocationReference(InstantMessage message, IItemSearcher searcher) {
        if (message.getPosition() == null || message.getPosition().getId() == null) {
            return;
        }

        {
            // the message and location shares the same "ufed:coordinate_id" that was added when merging in UfedXmlReader
            String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + message.getPosition().getId() + "\"";
            List<IItemReader> locationItems = searcher.search(query);
            if (!locationItems.isEmpty()) {
                if (locationItems.size() > 1) {
                    logger.warn("Found more than 1 location for coordinate: {}", locationItems);
                }
                message.getPosition().setReferencedLocation(locationItems.get(0));
                return;
            }
        }

        if (message.isLocationSharing()) {

            // the location item is referenced by jumptargets
            String[] jumpTargets = message.getJumpTargets().stream().map(JumpTarget::getId).toArray(String[]::new);
            if (jumpTargets.length > 0) {
                String query = BasicProps.CONTENTTYPE + ":\"application/x-ufed-location\" && " //
                        + searcher.escapeQuery(ExtraProperties.UFED_ID) + ":(\"" + StringUtils.join(jumpTargets, "\" \"") + "\")";
                List<IItemReader> locationItems = searcher.search(query);
                if (!locationItems.isEmpty()) {
                    if (locationItems.size() > 1) {
                        logger.warn("Found more than 1 location for jumptargets: {}", locationItems);
                    }
                    message.getPosition().setReferencedLocation(locationItems.get(0));
                }
            }
        }
    }

}
