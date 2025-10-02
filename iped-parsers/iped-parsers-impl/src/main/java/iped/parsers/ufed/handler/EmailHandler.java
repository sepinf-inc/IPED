package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.COMMUNICATION_DIRECTION;
import static iped.properties.ExtraProperties.MESSAGE_ATTACHMENT_COUNT;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;

import iped.data.IItemReader;
import iped.parsers.chat.EmailPartyStringBuilder;
import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.Email;
import iped.parsers.ufed.model.Party;
import iped.parsers.util.ConversationConstants;
import iped.search.IItemSearcher;

public class EmailHandler extends BaseModelHandler<Email> {

    public EmailHandler(Email model, IItemReader item) {
        super(model, item);
    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

        model.getAttachments().forEach(a -> {
            new AttachmentHandler(a, item).loadReferences(searcher);
        });
    }

    @Override
    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {
        model.getAttachments().stream().map(Attachment::getReferencedFile).filter(Objects::nonNull).forEach(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
            if (model.isFromPhoneOwner()) {
                addSharedHash(sharedHashes, ref.getItem());
            }
        });
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {
        super.fillMetadata(prefix, metadata);

        if (!model.getAttachments().isEmpty()) {
            metadata.set(MESSAGE_ATTACHMENT_COUNT, Integer.toString(model.getAttachments().size()));
        }

        //  Direction
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

        //  From
        model.getFrom().ifPresent(from ->  {
            metadata.add(Message.MESSAGE_FROM, new EmailPartyStringBuilder().withName(from.getName()).withUserId(from.getIdentifier()).build());
            metadata.add(Message.MESSAGE_FROM_NAME, from.getName());
            metadata.add(Message.MESSAGE_FROM_EMAIL, from.getIdentifier());
        });

        //  To
        model.getTo().forEach(to -> {
            metadata.add(Message.MESSAGE_TO, new EmailPartyStringBuilder().withName(to.getName()).withUserId(to.getIdentifier()).build());
            metadata.add(Message.MESSAGE_TO_NAME, to.getName());
            metadata.add(Message.MESSAGE_TO_EMAIL, to.getIdentifier());
        });

        // Cc
        model.getBcc().forEach(to -> {
            metadata.add(Message.MESSAGE_CC, new EmailPartyStringBuilder().withName(to.getName()).withUserId(to.getIdentifier()).build());
            metadata.add(Message.MESSAGE_CC_NAME, to.getName());
            metadata.add(Message.MESSAGE_CC_EMAIL, to.getIdentifier());
        });

        // Bcc
        model.getBcc().forEach(to -> {
            metadata.add(Message.MESSAGE_BCC, new EmailPartyStringBuilder().withName(to.getName()).withUserId(to.getIdentifier()).build());
            metadata.add(Message.MESSAGE_BCC_NAME, to.getName());
            metadata.add(Message.MESSAGE_BCC_EMAIL, to.getIdentifier());
        });

        // Message-Recipient-Address
        Stream.of(model.getTo(), model.getCc(), model.getBcc())
                .flatMap(Collection::stream)
                .map(Party::getIdentifier)
                .distinct()
                .forEach(addr -> metadata.add(Message.MESSAGE_RECIPIENT_ADDRESS, addr));
    }

    @Override
    public String getTitle() {
        return new StringBuilder()
                .append("Email") //
                .append("-[") //
                .append(StringUtils.firstNonBlank(model.getSubject(), model.getId())) //
                .append("]") //
                .toString();
    }
}
