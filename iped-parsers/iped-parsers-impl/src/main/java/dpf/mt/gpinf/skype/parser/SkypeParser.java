package dpf.mt.gpinf.skype.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para banco de dados do Skype
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */
public class SkypeParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public static final MediaType SKYPE_MIME = MediaType.application("sqlite-skype"); //$NON-NLS-1$
    public static final MediaType SKYPE_MIME_V12 = MediaType.application("sqlite-skype-v12"); //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<MediaType>();
    
    static {
    	SUPPORTED_TYPES.add(SKYPE_MIME);
    	SUPPORTED_TYPES.add(SKYPE_MIME_V12);
    }

    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private static final String CONTACT_MIME_TYPE = "contact/x-skype-contact"; //$NON-NLS-1$
    private static final String ACCOUNT_MIME_TYPE = "contact/x-skype-account"; //$NON-NLS-1$
    private static final String MESSAGE_MIME_TYPE = "message/x-skype-message"; //$NON-NLS-1$
    public static final String FILETRANSFER_MIME_TYPE = "message/x-skype-filetransfer"; //$NON-NLS-1$
    public static final String CONVERSATION_MIME_TYPE = "message/x-skype-conversation"; //$NON-NLS-1$

    private boolean extractMessageItems = false;

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        TemporaryResources tmp = new TemporaryResources();
        SkypeStorage sqlite = null;

        IItemSearcher searcher = context.get(IItemSearcher.class);

        String filePath = ""; //$NON-NLS-1$
        ItemInfo itemInfo = context.get(ItemInfo.class);
        if (itemInfo != null)
            filePath = itemInfo.getPath();

        final TikaInputStream tis = TikaInputStream.get(stream, tmp);
        try {
            //call here instead of catch clause because calls, videos and other info are not parsed currently
            sqliteParser.parse(tis, handler, metadata, context);

            if (extractor.shouldParseEmbedded(metadata)) {
                sqlite = new SkypeStorageFactory().createFromMediaType(tis, metadata, context, filePath);
                
                if (searcher != null)
                    sqlite.searchMediaCache(searcher);

                ReportGenerator r = new ReportGenerator(handler, metadata, sqlite.getSkypeName());

                Collection<SkypeContact> contatos = sqlite.extraiContatos();

                for (SkypeContact c : contatos) {
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CONTACT_MIME_TYPE);
                    cMetadata.set(HttpHeaders.CONTENT_TYPE, CONTACT_MIME_TYPE);
                    String name = c.getFullName();
                    if (name == null || name.trim().isEmpty())
                        name = c.getDisplayName();
                    if (name == null || name.trim().isEmpty())
                        name = c.getSkypeName();
                    cMetadata.set(TikaCoreProperties.TITLE, name);
                    cMetadata.set(ExtraProperties.USER_NAME, name);
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getSkypeName());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, "Skype"); //$NON-NLS-1$
                    cMetadata.set(ExtraProperties.USER_EMAIL, c.getEmail());
                    cMetadata.set(ExtraProperties.USER_PHONE, c.getAssignedPhone());
                    cMetadata.set(ExtraProperties.USER_BIRTH, c.getBirthday());
                    cMetadata.set(ExtraProperties.USER_ADDRESS, c.getCity());
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getAbout());
                    if(c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(c.getAvatar()));
                    }

                    if (extractor.shouldParseEmbedded(cMetadata)) {
                        ByteArrayInputStream chatStream = new ByteArrayInputStream(r.generateSkypeContactHtml(c));
                        extractor.parseEmbedded(chatStream, handler, cMetadata, false);
                    }
                }

                Collection<SkypeConversation> convs = sqlite.extraiMensagens();

                int msgCount = 0;
                for (SkypeConversation conv : convs) {

                    if (conv.getMessages() == null) {
                        /* se não houver mensagens na conversa não a processa */
                        continue;
                    }
                    msgCount += conv.getMessages().size();
                    if (conv.getMessages().size() <= 0) {
                        /* se não houver mensagens na conversa não a processa */
                        continue;
                    }

                    /* adiciona a conversação */
                    Metadata chatMetadata = new Metadata();
                    chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CONVERSATION_MIME_TYPE);
                    chatMetadata.set(HttpHeaders.CONTENT_TYPE, CONVERSATION_MIME_TYPE);
                    chatMetadata.set(TikaCoreProperties.TITLE, conv.getTitle());
                    chatMetadata.set(TikaCoreProperties.CREATED, conv.getCreationDate());
                    chatMetadata.set(TikaCoreProperties.MODIFIED, conv.getLastActivity());

                    storeSharedHashes(conv, chatMetadata);

                    if (extractor.shouldParseEmbedded(chatMetadata)) {
                        ByteArrayInputStream chatStream = new ByteArrayInputStream(
                                r.generateSkypeConversationHtml(conv));
                        extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                    }

                    /* adiciona as mensagens */
                    if (extractMessageItems)
                        for (SkypeMessage sm : conv.getMessages()) {
                            chatMetadata = new Metadata();
                            chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MESSAGE_MIME_TYPE);
                            chatMetadata.set(HttpHeaders.CONTENT_TYPE, MESSAGE_MIME_TYPE);
                            chatMetadata.set(TikaCoreProperties.TITLE, sm.getTitle());
                            chatMetadata.set(TikaCoreProperties.CREATED, sm.getData());
                            if (sm.getDataEdicao() != null) {
                                chatMetadata.set(TikaCoreProperties.MODIFIED, sm.getDataEdicao());
                            }

                            if (extractor.shouldParseEmbedded(chatMetadata)) {
                                ByteArrayInputStream chatStream = new ByteArrayInputStream(
                                        r.generateSkypeMessageHtml(sm));
                                extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                            }
                        }
                }

                Collection<SkypeFileTransfer> transfers = sqlite.extraiTransferencias();

                for (SkypeFileTransfer t : transfers) {
                    /* add file transfers */
                    Metadata tMetadata = new Metadata();
                    tMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, FILETRANSFER_MIME_TYPE);
                    tMetadata.set(HttpHeaders.CONTENT_TYPE, FILETRANSFER_MIME_TYPE);
                    tMetadata.set(TikaCoreProperties.TITLE,
                            Messages.getString("SkypeParser.SkypeTransfer") + t.getFilename()); //$NON-NLS-1$
                    tMetadata.set(TikaCoreProperties.CREATED, t.getStart());
                    tMetadata.set(TikaCoreProperties.MODIFIED, t.getFinish());

                    if (searcher != null) {
                        t.setItemQuery(getItemQuery(t, searcher));
                        t.setItem(getItem(t.getItemQuery(), searcher));
                    }

                    if (t.getItem() != null) {
                        // do not bookmark small items with common hashes
                        if (t.getItem().getLength() >= 512) {
                            tMetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + t.getItem().getHash());
                            if (t.getFrom().equals(sqlite.getSkypeName()))
                                tMetadata.add(ExtraProperties.SHARED_HASHES, t.getItem().getHash());
                        }
                    }

                    if (extractor.shouldParseEmbedded(tMetadata)) {
                        ByteArrayInputStream chatStream = new ByteArrayInputStream(
                                r.generateSkypeTransferenciaHtml(t, searcher));
                        extractor.parseEmbedded(chatStream, handler, tMetadata, false);
                    }
                }

                // cria o item que representa a conta do usuário (Account)
                Metadata meta = new Metadata();
                meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, ACCOUNT_MIME_TYPE);
                meta.set(HttpHeaders.CONTENT_TYPE, ACCOUNT_MIME_TYPE);
                SkypeAccount a = sqlite.getAccount();
                String name = a.getFullname();
                if (name == null || name.trim().isEmpty())
                    name = a.getSkypeName();

                meta.set(TikaCoreProperties.TITLE, name);
                meta.set(ExtraProperties.USER_NAME, name);
                meta.set(ExtraProperties.USER_ACCOUNT, a.getSkypeName());
                meta.set(ExtraProperties.USER_ACCOUNT_TYPE, "Skype"); //$NON-NLS-1$
                meta.set(ExtraProperties.USER_EMAIL, a.getEmail());
                meta.set(ExtraProperties.USER_BIRTH, a.getBirthday());
                meta.set(ExtraProperties.USER_NOTES, a.getAbout());
                if(a.getPhoneHome() != null) meta.add(ExtraProperties.USER_PHONE, a.getPhoneHome());
                if(a.getPhoneOffice() != null) meta.add(ExtraProperties.USER_PHONE, a.getPhoneOffice());
                if(a.getPhoneMobile() != null) meta.add(ExtraProperties.USER_PHONE, a.getPhoneMobile());
                if(a.getCity() != null) meta.add(ExtraProperties.USER_ADDRESS, a.getCity());
                if(a.getProvince() != null) meta.add(ExtraProperties.USER_ADDRESS, a.getProvince());
                if(a.getCountry() != null) meta.add(ExtraProperties.USER_ADDRESS, a.getCountry());
                if(a.getAvatar() != null) {
                    meta.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(a.getAvatar()));
                }

                if (extractor.shouldParseEmbedded(meta)) {
                    ByteArrayInputStream chatStream = new ByteArrayInputStream(
                            r.generateSkypeAccountHtml(a, contatos.size(), transfers.size(), msgCount));
                    extractor.parseEmbedded(chatStream, handler, meta, false);
                }

            }

        } catch (Exception e) {
            throw new TikaException("SkypeParserException Exception", e); //$NON-NLS-1$

        } finally {
            IOUtil.closeQuietly(sqlite);
            tmp.dispose();
        }

    }

    private void storeSharedHashes(SkypeConversation conv, Metadata metadata) {
        for (SkypeMessage sm : conv.getMessages()) {
            if (sm.getAnexoUri() != null && sm.getAnexoUri().getCacheFile() != null)
                // do not bookmark small items with common hashes
                if (sm.getAnexoUri().getCacheFile().getLength() >= 512) {
                    metadata.add(ExtraProperties.LINKED_ITEMS,
                            BasicProps.HASH + ":" + sm.getAnexoUri().getCacheFile().getHash());
                    if (sm.isFromMe())
                        metadata.add(ExtraProperties.SHARED_HASHES, sm.getAnexoUri().getCacheFile().getHash());
                }
        }

    }

    private String getItemQuery(SkypeFileTransfer c, IItemSearcher searcher) {
        String sizeQuery = BasicProps.LENGTH + ":" + c.getFileSize(); //$NON-NLS-1$
        if (c.getFilePath() != null && !c.getFilePath().trim().isEmpty()) {
            String path = c.getFilePath().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
            if (path.indexOf(':') == 1)
                path = path.substring(3);
            if (searcher != null)
                path = searcher.escapeQuery(path);
            String query = sizeQuery + " && " + BasicProps.PATH + ":\"" + path + "\""; //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
            if (!searcher.search(query).isEmpty())
                return query;
        }
        String name = searcher != null ? searcher.escapeQuery(c.getFilename()) : c.getFilename();
        return sizeQuery + " && " + BasicProps.NAME + ":\"" + name + "\""; //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
    }

    private IItemBase getItem(String query, IItemSearcher searcher) {
        List<IItemBase> items = searcher.search(query);
        if (items.size() > 0)
            return items.get(0);
        else
            return null;
    }

}