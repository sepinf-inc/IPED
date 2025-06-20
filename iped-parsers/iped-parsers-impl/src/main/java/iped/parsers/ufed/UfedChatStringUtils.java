package iped.parsers.ufed;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.Party;
import iped.parsers.util.Messages;
import iped.parsers.whatsapp.WAContact;

public class UfedChatStringUtils {

    public static String getChatTitle(Chat chat, boolean outputPrefix, boolean outputOwnerParticipant) {
        String source = chat.getSource();
        String chatType = chat.getChatType();
        String name = chat.getName();
        String chatId = chat.getFieldId();
        String ufedId = chat.getId();

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
                    if (chat.getParticipants().size() == 1 && StringUtils.containsAnyIgnoreCase(source, Chat.SOURCE_TELEGRAM, Chat.SOURCE_WHATSAPP)) {
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

        Party ownerPartipant = chat.getPhoneOwnerParticipant().orElse(null);
        List<Party> otherParticipants = chat.getOtherParticipants();

        if (name != null) {
            sb.append(name);
            if (chatId != null) {
                sb.append(" (ID:").append(chatId).append(")");
            }
        } else if (otherParticipants.size() == 1) {

            // append other participant
            if (outputOwnerParticipant) {
                sb.append("[");
            }
            sb.append(getPartyString(otherParticipants.get(0)));
            if (outputOwnerParticipant) {
                sb.append("]");
            }

            // append owner participant
            if (ownerPartipant != null && outputOwnerParticipant) {
                sb.append(" - [").append(getPartyString(ownerPartipant)).append("]");
            }
        } else if (chatId != null) {
            sb.append("ID:").append(chatId);
        } else if (ufedId != null) {
            sb.append(ufedId);
        }

        String result = sb.toString();
        if (StringUtils.containsIgnoreCase(source, Chat.SOURCE_WHATSAPP)) {
            result = StringUtils.remove(result, WAContact.waSuffix);
        }

        return result;
    }

    public static String getPartyString(Party party) {
        if (party == null) {
            return null;
        }
        if (StringUtils.isAllBlank(party.getName(), party.getIdentifier())) {
            return "";
        } else if (StringUtils.isNoneBlank(party.getName(), party.getIdentifier())) {
            return String.format("%s (%s)", party.getName(), party.getIdentifier());
        } else {
            return String.format("%s", StringUtils.firstNonBlank(party.getName(), party.getIdentifier()));
        }
    }

    public static String getInstantMessageTitle(InstantMessage message) {
        return new StringBuilder()
                .append(StringUtils.firstNonBlank(message.getType(), "InstantMessage")) //
                .append("-[") //
                .append(StringUtils.firstNonBlank(message.getIdentifier(), message.getId())) //
                .append("]") //
                .toString();
    }

    public static String getAttachmentTitle(Attachment attachment) {
        return new StringBuilder()
                .append("Attachment") //
                .append("-[") //
                .append(StringUtils.firstNonBlank(attachment.getFilename(), attachment.getId())) //
                .append("]") //
                .toString();
    }

    public static String getContactTitle(Contact contact) {
        return new StringBuilder()
                .append("Contact")
                .append(contact.getType() != null ? "-" : "")
                .append(StringUtils.defaultString(contact.getType())) //
                .append("-[") //
                .append(StringUtils.firstNonBlank(
                        contact.getName(),
                        contact.getUserID().map(ContactEntry::getValue).orElse(null),
                        contact.getPhoneNumber().map(ContactEntry::getValue).orElse(null),
                        contact.getId())
                        )
                .append("]") //
                .toString();
    }
}
