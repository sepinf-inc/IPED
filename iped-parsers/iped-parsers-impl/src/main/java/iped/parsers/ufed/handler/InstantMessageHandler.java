package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.COMMUNICATION_DIRECTION;
import static iped.properties.ExtraProperties.COMMUNICATION_FROM;
import static iped.properties.ExtraProperties.COMMUNICATION_TO;
import static iped.properties.ExtraProperties.MESSAGE_ATTACHMENT_COUNT;
import static iped.properties.ExtraProperties.PARENT_VIEW_POSITION;
import static iped.properties.ExtraProperties.UFED_COORDINATE_ID;
import static iped.properties.ExtraProperties.UFED_ID;
import static iped.properties.ExtraProperties.UFED_META_PREFIX;
import static iped.properties.ExtraProperties.UFED_SOURCE_MODELS;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.JumpTarget;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.model.QuotedMessageData;
import iped.parsers.ufed.model.ReplyMessageData;
import iped.parsers.util.ConversationConstants;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * Handles all processing logic for an InstantMessage model.
 */
public class InstantMessageHandler extends BaseModelHandler<InstantMessage> {

    private static final Logger logger = LoggerFactory.getLogger(InstantMessageHandler.class);

    private Map<String, IItemReader> cache;

    public InstantMessageHandler(InstantMessage model, IItemReader modelItem, Map<String, IItemReader> cache) {
        super(model, modelItem);
        this.cache = cache;
    }

    public InstantMessageHandler(InstantMessage model, IItemReader modelItem) {
        this(model, modelItem, new HashMap<>());
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {

        super.fillMetadata(prefix, metadata);

        if (model.getChat() != null) {
            metadata.set(PARENT_VIEW_POSITION, model.getAnchorId());
        }

        // own + forwarded embedded attachments, without duplicates
        List<Attachment> allAttachments = model.getAllAttachments();
        if (!allAttachments.isEmpty()) {
            metadata.set(MESSAGE_ATTACHMENT_COUNT, Integer.toString(allAttachments.size()));
        }

        // Message -> Direction
        model.getFrom().ifPresentOrElse(from -> {
            if (from.isPhoneOwner()) {
                metadata.set(COMMUNICATION_DIRECTION, ConversationConstants.DIRECTION_OUTGOING);
            } else {
                metadata.set(COMMUNICATION_DIRECTION, ConversationConstants.DIRECTION_INCOMING);
            }
        }, () -> {
            if (model.getTo().stream().filter(Party::isPhoneOwner).findAny().isPresent()) {
                metadata.set(COMMUNICATION_DIRECTION, ConversationConstants.DIRECTION_INCOMING);
            }
        });

        // Message -> From
        model.getFrom().ifPresent(from ->  {
            new PartyHandler(from, model.getSource()).fillMetadata(COMMUNICATION_FROM, metadata);
        });

        // Message -> To
        if (model.getTo().size() == 1) {
            new PartyHandler(model.getTo().get(0), model.getSource()).fillMetadata(COMMUNICATION_TO, metadata);

        } else if (model.getChat() != null) {

            // in case "To" is NOT set in InstantMessage, try to...
            List<Party> otherParticipants;
            if (model.getFrom().isPresent()) {

                // ...get other participants (without "from")
                otherParticipants = model.getChat().getParticipants().stream() //
                        .filter(p -> !p.equals(model.getFrom().get())) //
                        .collect(Collectors.toList()); //
            } else {
                // ...get other participants
                otherParticipants = model.getChat().getParticipants();
            }

            if (otherParticipants.size() == 1) {
                new PartyHandler(otherParticipants.get(0), model.getSource()).fillMetadata(COMMUNICATION_TO, metadata);

            } else if (otherParticipants.size() > 2) {

                // there is more than one "To", so use chat information
                metadata.add(COMMUNICATION_TO, new ChatHandler(model.getChat()).getTitle(false, false));

                String chatId = model.getChat().getFieldId();
                if (StringUtils.isNotBlank(chatId)) {
                    metadata.add(COMMUNICATION_TO  + ":id", chatId);
                }

                String chatName = model.getChat().getName();
                if (StringUtils.isNotBlank(chatName)) {
                    metadata.add(COMMUNICATION_TO + ":name", chatName);
                }
            }
        }

        // labels of the message itself only in "ufed:Label", without duplicates
        for (String label : model.getOwnLabels()) {
            metadata.add(UFED_META_PREFIX + "Label", label);
        }

        model.getExtraData().getForwardedMessage().ifPresent(fw -> {
            if (fw.getOriginalSender() != null) {
                new PartyHandler(fw.getOriginalSender(), model.getSource())
                    .fillMetadata(UFED_META_PREFIX + "Forwarded:originalSender", metadata);
            }

            fw.getFields().forEach((key, value) -> {
                if (!"Label".equals(key)) { // not written (see InstantMessage.getOwnLabels())
                    fillFieldMetadata("Forwarded:" + key, value, metadata, Collections.emptySet());
                }
            });
        });

        model.getExtraData().getReplyMessage().ifPresent(replied -> {

            replied.getFields().forEach((key, value) -> {
                if (!"Label".equals(key)) { // not written (see InstantMessage.getOwnLabels())
                    fillFieldMetadata("Reply:" + key, value, metadata, Collections.emptySet());
                }
            });

            if (replied.getInstantMessage() != null) {
                metadata.add(UFED_META_PREFIX + "Reply:referenceId", replied.getInstantMessage().getId());
            }
        });

        model.getExtraData().getQuotedMessage().ifPresent(quoted -> {
            // PA 10.10+ does not put "Label" inside QuotedMessageData anymore
            String type = quoted.getLabel();
            if (StringUtils.isBlank(type)) {
                type = model.hasLabel("Reply") ? "Reply" : model.hasLabel("Forwarded") ? "Forwarded" : "Quoted";
            }
            final String quotedType = type;

            quoted.getFields().forEach((key, value) -> {
                if (!"Label".equals(key)) { // not written (see InstantMessage.getOwnLabels())
                    fillFieldMetadata(quotedType + ":" + key, value, metadata, Collections.emptySet());
                }
            });
        });

        // labels of the quoted message (e.g. replying a forwarded message), in an explicit key,
        // since this message itself is not forwarded
        if (model.isReplyMessage()) {
            InstantMessage quotedMessage = model.findReplyMessage(model.getChat());
            if (quotedMessage != null) {
                for (String label : quotedMessage.getOwnLabels()) {
                    if (!"Default".equalsIgnoreCase(label)) {
                        metadata.add(UFED_META_PREFIX + "Reply:quotedMessageLabel", label);
                    }
                }
            }
        }

        if (model.isSystemMessage()) {
            metadata.set(UFED_META_PREFIX + "isSystemMessage", Boolean.toString(true));
        }

        if (model.getPosition() != null) {
            metadata.set(Geographic.LATITUDE, model.getPosition().getLatitude());
            metadata.set(Geographic.LONGITUDE, model.getPosition().getLongitude());
        }
    }

    private static final Set<String> IGNORED_FIELDS = Set.of("Label", "Labels");

    @Override
    protected Set<String> getIgnoredFields() {
        // written by fillMetadata() in "ufed:Label" without duplicates
        return IGNORED_FIELDS;
    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

        model.getFrom().ifPresent(from -> {
            new PartyHandler(from, model.getSource(), item, cache).loadReferences(searcher);
        });
        model.getTo().forEach(to -> {
            new PartyHandler(to, model.getSource(), item, cache).loadReferences(searcher);
        });
        model.getAttachments().stream().forEach(a -> {
            new AttachmentHandler(a, item).loadReferences(searcher);
        });
        model.getSharedContacts().stream().forEach(c -> {
            new ContactHandler(c).loadReferences(searcher);
        });

        model.getEmbeddedMessage().ifPresent(em -> {
            new InstantMessageHandler(em, item, cache).loadReferences(searcher);
        });
        model.getExtraData().getReplyMessage().map(ReplyMessageData::getInstantMessage).ifPresent(rm -> {
            new InstantMessageHandler(rm, item, cache).loadReferences(searcher);
        });

        loadLocationReference(searcher);

        loadFileReferenceInSourceModels(searcher);
    }

    @Override
    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {
        model.getFrom().flatMap(Party::getReferencedContact).ifPresent(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
        });
        model.getTo().stream().map(Party::getReferencedContact).filter(Optional::isPresent).map(Optional::get).forEach(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
        });

        model.getAllAttachments().stream().map(Attachment::getReferencedFile).filter(Objects::nonNull).forEach(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
            if (model.isFromPhoneOwner()) {
                addSharedHash(sharedHashes, ref.getItem());
            }
        });

        model.getSharedContacts().stream().map(Contact::getReferencedContact).filter(Optional::isPresent).map(Optional::get).forEach(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
        });

        if (model.getPosition() != null && model.getPosition().getReferencedLocation() != null) {
            addLinkedItem(linkedItems, model.getPosition().getReferencedLocation().getItem(), searcher);
        }
    }

    @Override
    public String getTitle() {

        return new StringBuilder()
                .append(StringUtils.firstNonBlank(model.getType(), "InstantMessage"))
                .append("-[")
                .append(StringUtils.firstNonBlank(model.getIdentifier(), model.getId()))
                .append("]")
                .toString();
    }


    private void loadLocationReference(IItemSearcher searcher) {

        if (model.getPosition() == null || model.getPosition().getReferencedLocation() != null) {
            return;
        }

        if (model.getPosition().getId() != null) {

            // the message and location shares the same "ufed:coordinate_id" that was added when merging in UfedXmlReader
            String query = searcher.escapeQuery(UFED_COORDINATE_ID) + ":\"" + model.getPosition().getId() + "\"";
            List<IItemReader> locationItems = searcher.search(query);
            if (!locationItems.isEmpty()) {
                if (locationItems.size() > 1) {
                    logger.warn("Found more than 1 location for coordinate: {}", locationItems);
                }
                model.getPosition().setReferencedLocation(locationItems.get(0));
                return;
            }
        }

        if (model.isLocationSharing()) {

            // the location item is referenced by jumptargets
            String[] jumpTargets = model.getJumpTargets().stream().map(JumpTarget::getId).toArray(String[]::new);
            if (jumpTargets.length > 0) {
                String query = BasicProps.CONTENTTYPE + ":\"application/x-ufed-location\" && " //
                        + searcher.escapeQuery(UFED_ID) + ":(\"" + StringUtils.join(jumpTargets, "\" \"") + "\")";
                List<IItemReader> locationItems = searcher.search(query);
                if (!locationItems.isEmpty()) {
                    if (locationItems.size() > 1) {
                        logger.warn("Found more than 1 location for jumptargets: {}", locationItems);
                    }
                    model.getPosition().setReferencedLocation(locationItems.get(0));
                    return;
                }
            }
        }

        logger.debug("Location reference was not found: {}", model);
    }

    private void loadFileReferenceInSourceModels(IItemSearcher searcher) {

        String referenceId;
        if (model.isForwardedMessage()
                && model.getAttachments().isEmpty()
                && (referenceId = model.getExtraData().getQuotedMessage().map(QuotedMessageData::getReferenceId).orElse(null)) != null
                && StringUtils.isNotBlank(referenceId)
                && model.findForwardedMessage(model.getChat()) == null) {

            String query = searcher.escapeQuery(UFED_SOURCE_MODELS) + ":\"" + referenceId + "\"";
            for (IItemReader result : searcher.searchIterable(query)) {

                // add a "fake" attachment related to file with referenceId
                Attachment attachment = new Attachment();
                attachment.setAttribute("file_id", result.getMetadata().get(UFED_ID));
                attachment.setField("comment", "Added by IPED from source models");
                attachment.setField("sourceModelReferenceId", referenceId);
                attachment.setField("ContentType", result.getMediaType().toString());
                attachment.setField("Filename", result.getName());
                attachment.setReferencedFile(result);
                model.getAttachments().add(attachment);
            }
        }
    }
}