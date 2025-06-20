package iped.engine.datasource.ufed;
import java.util.Stack;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.ufed.model.Attachment;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.ChatActivity;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.ContactEntry.PhoneNumber;
import iped.parsers.ufed.model.ContactEntry.UserID;
import iped.parsers.ufed.model.ContactPhoto;
import iped.parsers.ufed.model.Coordinate;
import iped.parsers.ufed.model.ForwardedMessageData;
import iped.parsers.ufed.model.GenericModel;
import iped.parsers.ufed.model.InstantMessage;
import iped.parsers.ufed.model.InstantMessageExtraData;
import iped.parsers.ufed.model.JumpTarget;
import iped.parsers.ufed.model.MessageLabel;
import iped.parsers.ufed.model.Party;
import iped.parsers.ufed.model.QuotedMessageData;
import iped.parsers.ufed.model.ReplyMessageData;
import iped.utils.DateUtil;


public class UfedModelHandler extends DefaultHandler {

    private static Logger logger = LoggerFactory.getLogger(UfedModelHandler.class);

    private final Stack<BaseModel> modelStack = new Stack<>();
    private final Stack<String> fieldNameStack = new Stack<>();
    private final Stack<String> fieldTypeStack = new Stack<>();
    private final StringBuilder elementValueBuilder = new StringBuilder();
    private JumpTarget currentJumpTarget;

    private XMLReader xmlReader;
    private ContentHandler parentHandler;
    private UfedModelListener listener;
    private boolean listOnly;


    public interface UfedModelListener {
        void onModelStarted(BaseModel model, Attributes attr);
        void onModelCompleted(BaseModel model);
    }

    public UfedModelHandler(XMLReader xmlReader, ContentHandler parentHandler, UfedModelListener listener, boolean listOnly) {
        this.xmlReader = xmlReader;
        this.parentHandler = parentHandler;
        this.listener = listener;
        this.listOnly = listOnly;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (listOnly) {
            modelStack.push(null);
            return;
        }

        elementValueBuilder.setLength(0); // Clear builder for new element

        if ("model".equalsIgnoreCase(qName)) {
            String type = attributes.getValue("type");
            BaseModel newModel = createModelByType(type);

            for (int i = 0; i < attributes.getLength(); i++) {
                newModel.setAttribute(attributes.getQName(i), attributes.getValue(i));
            }
            newModel.setId(attributes.getValue("id"));

            if (!modelStack.isEmpty() && !fieldNameStack.isEmpty()) {
                addChildModel(modelStack.peek(), newModel, fieldNameStack.peek());
            } else {
                listener.onModelStarted(newModel, attributes);
            }

            modelStack.push(newModel);
        } else if ("field".equalsIgnoreCase(qName) || "modelField".equalsIgnoreCase(qName)
                   || "multiModelField".equalsIgnoreCase(qName) || "nodeField".equalsIgnoreCase(qName)) {
            fieldNameStack.push(attributes.getValue("name"));
            fieldTypeStack.push(attributes.getValue("type")); // Store the field's type
        } else if ("targetid".equalsIgnoreCase(qName)) {
            currentJumpTarget = new JumpTarget();
            currentJumpTarget.setIsModel("true".equalsIgnoreCase(attributes.getValue("ismodel")));
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        elementValueBuilder.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        // the list of models has ended, so pass the control back to parentHandler
        if (modelStack.isEmpty()) {
            xmlReader.setContentHandler(parentHandler);
            return;
        }

        if (listOnly) {
            modelStack.pop();
            if (modelStack.isEmpty()) {
                listener.onModelCompleted(null);
            }
            return;
        }

        if ("model".equalsIgnoreCase(qName)) {
            BaseModel completedModel = modelStack.pop();
            if (modelStack.isEmpty()) {
                // this model object has completed, notify the listener
                listener.onModelCompleted(completedModel);
            }
        } else if ("field".equalsIgnoreCase(qName) || "modelField".equalsIgnoreCase(qName)
                   || "multiModelField".equalsIgnoreCase(qName) || "nodeField".equalsIgnoreCase(qName)) {
            // Guard against empty stacks if XML is malformed
            if (!fieldNameStack.isEmpty()) {
                fieldNameStack.pop();
            }
            if (!fieldTypeStack.isEmpty()) {
                fieldTypeStack.pop();
            }
        } else if ("value".equalsIgnoreCase(qName)) {
            if (!modelStack.isEmpty() && !fieldNameStack.isEmpty()) {
                String stringValue = elementValueBuilder.toString().trim();
                String fieldType = fieldTypeStack.peek();
                Object parsedValue = parseValue(stringValue, fieldType);
                if (parsedValue != null) {
                    modelStack.peek().setField(fieldNameStack.peek(), parsedValue);
                }
            }
        } else if ("id".equalsIgnoreCase(qName)) {
            // Special handling for the <id> tag inside a <nodeField name="PhotoNode">
            if (!fieldNameStack.isEmpty() && "PhotoNode".equals(fieldNameStack.peek())) {
                if (!modelStack.isEmpty() && modelStack.peek() instanceof ContactPhoto) {
                    ContactPhoto photo = (ContactPhoto) modelStack.peek();
                    photo.setField("PhotoNodeId", elementValueBuilder.toString().trim());
                }
            }
        } else if ("targetid".equalsIgnoreCase(qName)) {
            if (currentJumpTarget != null && !modelStack.isEmpty()) {
                currentJumpTarget.setId(elementValueBuilder.toString().trim());
                modelStack.peek().getJumpTargets().add(currentJumpTarget);
                currentJumpTarget = null; // Reset
            }
        }
    }

    private Object parseValue(String value, String type) {
        if (StringUtils.isAnyBlank(value, type)) {
            return value;
        }

        try {
            switch (type) {
                case "TimeStamp": return DateUtil.tryToParseDate(value);
                case "Boolean": return Boolean.valueOf(value);
                case "Double": return value.replace(',', '.');
                default: return value;
            }
        } catch (Exception e) {
            logger.error("Could not parse value '{}' for type '{}'. Defaulting to String. [{}]", value, type, e.getMessage());
            return value;
        }
    }

    private BaseModel createModelByType(String type) {
        if (type == null) {
            return new GenericModel("null");
        }
        switch (type) {
            case "Chat": return new Chat();
            case "InstantMessage": {
                if (!modelStack.isEmpty() && modelStack.peek() instanceof Chat) {
                    return new InstantMessage((Chat) modelStack.peek());
                } else {
                    return new InstantMessage();
                }
            }
            case "Party": return new Party();
            case "QuotedMessageData": return new QuotedMessageData();
            case "Attachment": return new Attachment();
            case "ContactPhoto": return new ContactPhoto();
            case "MessageLabel": return new MessageLabel();
            case "Contact": return new Contact();
            case "UserID": return new UserID();
            case "PhoneNumber": return new PhoneNumber();
            case "Coordinate": return new Coordinate();
            case "ForwardedMessageData": return new ForwardedMessageData();
            case "ReplyMessageData": return new ReplyMessageData();
            case "ChatActivity": return new ChatActivity();
            default: {
                if (!modelStack.isEmpty() && modelStack.peek() instanceof Contact
                        && !fieldNameStack.isEmpty() && "Entries".equals(fieldNameStack.peek())) {
                    return new ContactEntry(type);
                } else {
                    return new GenericModel(type);
                }
            }
        }
    }

    private void addChildModel(BaseModel parent, BaseModel child, String fieldName) {
        if (parent instanceof Chat) {
            Chat chat = (Chat) parent;
            if ("Participants".equals(fieldName) && child instanceof Party) {
                chat.getParticipants().add((Party) child);
            } else if ("Messages".equals(fieldName) && child instanceof InstantMessage) {
                chat.getMessages().add((InstantMessage) child);
            } else if ("Photos".equals(fieldName) && child instanceof ContactPhoto) {
                chat.getPhotos().add((ContactPhoto) child);
            } else {
                logger.warn("Unrecognized Chat child '{}' ({}).", fieldName, child.getId());
                chat.getOthers().put(fieldName, chat);
            }
        } else if (parent instanceof InstantMessage) {
            InstantMessage message = (InstantMessage) parent;
            if ("From".equals(fieldName) && child instanceof Party) {
                message.setFrom((Party) child);
            } else if ("To".equals(fieldName) && child instanceof Party) {
                message.getTo().add((Party) child);
            } else if ("Attachments".equals(fieldName) && child instanceof Attachment) {
                message.getAttachments().add((Attachment) child);
            } else if ("SharedContacts".equals(fieldName) && child instanceof Contact) {
                message.getSharedContacts().add((Contact) child);
            } else if ("EmbeddedInstantMessage".equals(fieldName) && child instanceof InstantMessage) {
                message.setEmbeddedMessage((InstantMessage) child);
            } else if ("ActivityLog".equals(fieldName) && child instanceof ChatActivity) {
                message.setActivityLog((ChatActivity) child);
            } else if ("MessageExtraData".equals(fieldName)) {
                InstantMessageExtraData extraData = message.getExtraData();
                if(child instanceof MessageLabel) {
                    extraData.getMessageLabels().add((MessageLabel) child);
                } else if (child instanceof QuotedMessageData) {
                    extraData.setQuotedMessage((QuotedMessageData) child);
                } else if (child instanceof ForwardedMessageData) {
                    extraData.setForwardedMessage((ForwardedMessageData) child);
                } else if (child instanceof ReplyMessageData) {
                    extraData.setReplyMessage((ReplyMessageData) child);
                } else {
                    logger.error("Unknown InstantMessageExtraData child '{}' => {} (id={}). Ignoring...", fieldName, child.getClass().getSimpleName(), child.getId());
                }
            } else {
                logger.warn("Unrecognized InstantMessage child '{}' => {} (id={}).", fieldName, child.getClass().getName(), child.getId());
                message.getOthers().put(fieldName, child);
            }
        } else if (parent instanceof ForwardedMessageData) {
            if("OriginalSender".equals(fieldName)) {
                ((ForwardedMessageData) parent).setOriginalSender((Party) child);
            } else {
                logger.error("Unknown ForwardedMessageData child '{}' ({}). Ignoring...", fieldName, child.getId());
            }
        } else if (parent instanceof Contact) {
            Contact contact = (Contact) parent;
            if ("Entries".equals(fieldName) && child instanceof ContactEntry) {
                if (child instanceof UserID) {
                    contact.setUserID((UserID) child);
                } else if (child instanceof PhoneNumber) {
                    contact.setPhoneNumber((PhoneNumber) child);
                } else {
                    logger.warn("Unrecognized Contact entry '{}' => {} (id={}).", fieldName, child.getClass().getName(), child.getId());
                    contact.getOtherEntries().put(fieldName, (ContactEntry) child);
                }
            } else if ("Photos".equals(fieldName) && child instanceof ContactPhoto) {
                contact.getPhotos().add((ContactPhoto) child);
            } else {
                logger.warn("Unrecognized Contact child '{}' => {} (id={}).", fieldName, child.getClass().getName(), child.getId());
                contact.getOtherModelFields().put(fieldName, child);
            }
        } else if (parent instanceof ReplyMessageData) {
            if("InstantMessage".equals(fieldName) && child instanceof InstantMessage) {
                ((ReplyMessageData) parent).setInstantMessage((InstantMessage) child);
            } else {
                logger.error("Unknown ReplyMessageData child '{}' ({}). Ignoring...", fieldName, child.getId());
            }
        } else if (parent instanceof ChatActivity) {
            if("Participant".equals(fieldName) && child instanceof Party) {
                ((ChatActivity) parent).setParticipant((Party) child);
            } else {
                logger.error("Unknown ChatActivity child '{}' ({}). Ignoring...", fieldName, child.getId());
            }
        }
    }
}

