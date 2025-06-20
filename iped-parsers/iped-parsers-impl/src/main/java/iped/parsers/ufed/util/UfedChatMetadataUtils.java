package iped.parsers.ufed.util;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.DateUtils;

import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.Party;
import iped.properties.ExtraProperties;

public class UfedChatMetadataUtils {

    public static void fillChatMetadata(Chat chat, Metadata metadata) {

        // Chat -> Participants
        chat.getParticipants().forEach(p -> {
            fillParticipantMetadata(p, metadata, "participants");
        });

        // Chat -> Phone Owner participant
        chat.getPhoneOwnerParticipant().ifPresent(p -> {
            fillParticipantMetadata(p, metadata, "phoneOwner");
        });

        // Chat -> Group Admin participants
        chat.getParticipants().stream().filter(Party::isGroupAdmin).forEach(p -> {
            fillParticipantMetadata(p, metadata, "groupAdmins");
        });

        fillCommonMetadata(chat, metadata);
    }

    public static Metadata createGenericMetadata(BaseModel model) {
        Metadata metadata = new Metadata();
        fillCommonMetadata(model, metadata);
        return metadata;
    }

    public static Metadata createInstantMessageMetadata(InstantMessage message) {
        Metadata metadata = new Metadata();
        fillInstantMessageMetadata(message, metadata);
        return metadata;
    }

    public static void fillInstantMessageMetadata(InstantMessage message, Metadata metadata) {

        fillCommonMetadata(message, metadata);

        metadata.set(ExtraProperties.PARENT_VIEW_POSITION, Integer.toString(message.getSourceIndex()));
        if (!message.getAttachments().isEmpty()) {
            metadata.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, message.getAttachments().size());
        }

        // Instant Message -> From
        message.getFrom().ifPresent(from -> {
            fillParticipantMetadata(from, metadata, "from");
        });

        // Instant Message -> To
        if (!message.getTo().isEmpty()) {
            message.getTo().forEach(to -> {
                fillParticipantMetadata(to, metadata, "to");
            });
        } else if (message.getChat() != null) {

            // get participants that are not 'from'
            List<Party> otherParticipants;
            if (message.getFrom().isPresent()) {
                otherParticipants = message.getChat().getParticipants().stream().filter(p -> {
                    return !p.equals(message.getFrom().get());
                }).collect(Collectors.toList());
            } else {
                otherParticipants = message.getChat().getParticipants();
            }

            if (otherParticipants.size() == 1) {
                fillParticipantMetadata(otherParticipants.get(0), metadata, "to");
            } if (otherParticipants.size() > 2) {
                metadata.add(ExtraProperties.UFED_META_PREFIX + "to", UfedChatStringUtils.getChatTitle(message.getChat(), false, false));
            }
        }

        // Instant Message -> Label
        message.getExtraData().getMessageLabels().forEach(l -> {
            metadata.add(ExtraProperties.UFED_META_PREFIX + "label", l.getLabel());
        });

        // Instant Message -> Forwarded
        message.getExtraData().getForwardedMessage().ifPresent(fw -> {
            if (fw.getOriginalSender() != null) {
                fillParticipantMetadata(fw.getOriginalSender(), metadata, "forwarded:originalSender");
            }

            metadata.add(ExtraProperties.UFED_META_PREFIX + "label", fw.getLabel());

            fillFieldsMetadata(fw, metadata, "forwarded", Set.of("Label"));
        });

        // Instant Message -> Replied
        message.getExtraData().getReplyMessage().ifPresent(replied -> {
            metadata.add(ExtraProperties.UFED_META_PREFIX + "label", replied.getLabel());

            fillFieldsMetadata(replied, metadata, "reply", Set.of("Label"));

            if (replied.getInstantMessage() != null) {
                metadata.add(ExtraProperties.UFED_META_PREFIX + "reply:referenceId", replied.getInstantMessage().getId());
            }
        });

        // Instant Message -> Quoted (can be Forwarded or Reply - depends on the label)
        message.getExtraData().getQuotedMessage().ifPresent(quoted -> {
            String type = StringUtils.firstNonBlank(StringUtils.uncapitalize(quoted.getLabel()), "quoted");
            fillFieldsMetadata(quoted, metadata, type, Set.of("Label"));
        });
    }

    private static void fillParticipantMetadata(Party p, Metadata metadata, String type) {
        if (p == null) {
            return;
        }
        metadata.add(ExtraProperties.UFED_META_PREFIX + type, UfedChatStringUtils.getPartyString(p));
        metadata.add(ExtraProperties.UFED_META_PREFIX + type + ":id", p.getIdentifier());
        metadata.add(ExtraProperties.UFED_META_PREFIX + type + ":name", p.getName());
    }

    public static void fillCommonMetadata(BaseModel model, Metadata metadata) {

        if (model.isDeleted()) {
            metadata.set(ExtraProperties.DELETED, Boolean.toString(true));
        }

        // add attributes
        model.getAttributes().entrySet().stream() //
            .filter(e -> !StringUtils.equalsAny(e.getKey(), "type")) // ignore some attributes
            .forEach(e -> {
                metadata.set(ExtraProperties.UFED_META_PREFIX + e.getKey(), e.getValue());
            });

        // add fields
        fillFieldsMetadata(model, metadata);

        // add jumpTargets
        model.getJumpTargets().forEach(jt -> {
            metadata.add(ExtraProperties.UFED_JUMP_TARGETS, jt.getId());
        });
    }

    private static void fillFieldsMetadata(BaseModel model, Metadata metadata) {
        fillFieldsMetadata(model, metadata, "", Collections.emptySet());
    }

    private static void fillFieldsMetadata(BaseModel model, Metadata metadata, String subPrefix, Set<String> fieldsToIgnore) {
        model.getFields().forEach((key, value) -> {

            if (fieldsToIgnore.contains(key)) {
                return;
            }

            // avoid conflict between "Id" field and "id" attribute
            if ("Id".equals(key)) {
                key = model.getModelType() + key;
            }

            key = StringUtils.uncapitalize(key);

            String prefix = subPrefix;
            if (!prefix.isEmpty()) {
                prefix = prefix + ":";
            }

            if (value instanceof Date) {
                metadata.add(ExtraProperties.UFED_META_PREFIX + prefix + key, DateUtils.formatDate((Date) value));
            } else {
                metadata.add(ExtraProperties.UFED_META_PREFIX + prefix + key, value.toString());
            }
        });
    }

}
