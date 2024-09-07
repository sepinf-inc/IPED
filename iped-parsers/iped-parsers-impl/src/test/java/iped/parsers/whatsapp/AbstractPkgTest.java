package iped.parsers.whatsapp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.BaseItemSearchContext;
import iped.properties.ExtraProperties;

public abstract class AbstractPkgTest extends BaseItemSearchContext {

    protected EmbeddedWhatsAppParser whatsapptracker;

    protected ParseContext getContext(String resource) throws IOException {
        whatsapptracker = new EmbeddedWhatsAppParser();
        ParseContext whatsappContext = super.getContext(resource);
        whatsappContext.set(Parser.class, whatsapptracker);
        return whatsappContext;

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedWhatsAppParser extends AbstractParser {
        protected List<String> title = new ArrayList<String>();
        protected List<String> type = new ArrayList<String>();
        protected List<String> username = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useraccount = new ArrayList<String>();
        protected List<String> usernotes = new ArrayList<String>();
        protected List<List<String>> participants = new ArrayList<>();
        protected List<List<String>> admins = new ArrayList<>();
        protected List<String> messagefrom = new ArrayList<String>();
        protected List<String> messagebody = new ArrayList<String>();
        protected List<String> messageto = new ArrayList<String>();
        protected List<String> messagedate = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            if (metadata.get(TikaCoreProperties.TITLE) != null)
                title.add(metadata.get(TikaCoreProperties.TITLE));
            if (metadata.get(ExtraProperties.CONVERSATION_TYPE) != null)
                type.add(metadata.get(ExtraProperties.CONVERSATION_TYPE));
            if (metadata.get(ExtraProperties.USER_NAME) != null)
                username.add(metadata.get(ExtraProperties.USER_NAME));
            if (metadata.get(ExtraProperties.USER_PHONE) != null)
                userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            if (metadata.get(ExtraProperties.USER_ACCOUNT) != null)
                useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
            if (metadata.get(ExtraProperties.USER_NOTES) != null)
                usernotes.add(metadata.get(ExtraProperties.USER_NOTES));
            if (metadata.get(ExtraProperties.CONVERSATION_PARTICIPANTS) != null) {
                participants.add(Arrays.asList(metadata.getValues(ExtraProperties.CONVERSATION_PARTICIPANTS)));
                admins.add(Arrays.asList(metadata.getValues(ExtraProperties.CONVERSATION_ADMINS)));
            }
            if (metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM) != null)
                messagefrom.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM));
            if (metadata.get(ExtraProperties.MESSAGE_BODY) != null)
                messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
            if (metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO) != null)
                messageto.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO));
            if (metadata.get(ExtraProperties.MESSAGE_DATE) != null)
                messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
        }
    }
}
