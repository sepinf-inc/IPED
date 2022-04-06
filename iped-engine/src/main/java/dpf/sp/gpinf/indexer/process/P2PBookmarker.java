package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.ap.gpinf.telegramextractor.TelegramParser;
import dpf.inc.sepinf.gdrive.parsers.GDriveCloudGraphParser;
import dpf.inc.sepinf.gdrive.parsers.GDriveSnapshotParser;
import dpf.mg.udi.gpinf.shareazaparser.ShareazaLibraryDatParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.parsers.AresParser;
import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.parsers.PartMetParser;
import dpf.sp.gpinf.indexer.parsers.ufed.UFEDChatParser;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.ICaseData;
import iped3.search.SearchResult;
import iped3.util.ExtraProperties;

public class P2PBookmarker {

    private static Logger LOGGER = LoggerFactory.getLogger(P2PBookmarker.class);

    private boolean isIpedReport = false;

    public P2PBookmarker(ICaseData caseData) {
        isIpedReport = caseData.isIpedReport();
    }

    class P2PProgram {
        String hashName;
        String appName;

        public P2PProgram(String hashName, String appName) {
            this.hashName = hashName;
            this.appName = appName;
        }
    }

    public void createBookmarksForSharedFiles(File caseDir) {

        if (isIpedReport)
            return;

        LOGGER.info("Searching items shared by P2P..."); //$NON-NLS-1$

        HashMap<String, P2PProgram> p2pPrograms = new HashMap<String, P2PProgram>();

        p2pPrograms.put(KnownMetParser.EMULE_MIME_TYPE, new P2PProgram(HashTask.HASH.EDONKEY.toString(), "Emule")); //$NON-NLS-1$
        p2pPrograms.put(PartMetParser.EMULE_PART_MET_MIME_TYPE, new P2PProgram(HashTask.HASH.EDONKEY.toString(), "Emule")); //$NON-NLS-1$
        p2pPrograms.put(AresParser.ARES_MIME_TYPE, new P2PProgram(HashTask.HASH.SHA1.toString(), "Ares")); //$NON-NLS-1$
        p2pPrograms.put(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE,
                new P2PProgram(HashTask.HASH.MD5.toString(), "Shareaza")); //$NON-NLS-1$
        p2pPrograms.put(WhatsAppParser.WHATSAPP_CHAT.toString(),
                new P2PProgram(HashTask.HASH.SHA256.toString(), "WhatsApp")); //$NON-NLS-1$
        p2pPrograms.put(UFEDChatParser.UFED_CHAT_PREVIEW_MIME.toString(),
                new P2PProgram(IndexItem.HASH.toString(), "UFED_Chats")); //$NON-NLS-1$
        p2pPrograms.put(SkypeParser.FILETRANSFER_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype")); //$NON-NLS-1$
        p2pPrograms.put(SkypeParser.CONVERSATION_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype")); //$NON-NLS-1$
        p2pPrograms.put(TelegramParser.TELEGRAM_CHAT.toString(), new P2PProgram(IndexItem.HASH, "Telegram")); // $NON-NLS-1$
        p2pPrograms.put(GDriveCloudGraphParser.GDRIVE_CLOUD_GRAPH_REG.toString(), new P2PProgram(HashTask.HASH.MD5.toString(), "GoogleDrive"));
        p2pPrograms.put(GDriveSnapshotParser.GDRIVE_SNAPSHOT_REG.toString(), new P2PProgram(HashTask.HASH.MD5.toString(), "GoogleDrive"));

        IPEDSource ipedSrc = new IPEDSource(caseDir);
        String queryText = ExtraProperties.SHARED_HASHES + ":*"; //$NON-NLS-1$
        IPEDSearcher searcher = new IPEDSearcher(ipedSrc, queryText);
        try {
            SearchResult p2pItems = searcher.search();
            for (int i = 0; i < p2pItems.getLength(); i++) {
                int luceneId = ipedSrc.getLuceneId(p2pItems.getId(i));
                Document doc = ipedSrc.getReader().document(luceneId);
                String mediaType = doc.get(IndexItem.CONTENTTYPE);
                P2PProgram program = p2pPrograms.get(mediaType);
                if (program == null) {
                    continue;
                }
                String[] sharedItems = doc.getValues(ExtraProperties.SHARED_HASHES);
                boolean isHash = true;
                if (sharedItems.length == 0) {
                    isHash = false;
                    sharedItems = doc.getValues(ExtraProperties.SHARED_ITEMS);
                }
                StringBuilder items = new StringBuilder();
                for (String item : sharedItems) {
                    if (!isHash)
                        items.append("(");
                    items.append(item).append(" "); //$NON-NLS-1$
                    if (!isHash)
                        items.append(") ");
                }
                StringBuilder queryBuilder = new StringBuilder();
                queryBuilder.append(IndexItem.LENGTH + ":[3 TO *] AND ("); //$NON-NLS-1$
                if (isHash)
                    queryBuilder.append(program.hashName + ":("); //$NON-NLS-1$
                queryBuilder.append(items.toString());
                if (isHash)
                    queryBuilder.append(")"); //$NON-NLS-1$
                queryBuilder.append(")"); //$NON-NLS-1$
                searcher = new IPEDSearcher(ipedSrc, queryBuilder.toString());

                SearchResult result = searcher.search();
                LOGGER.info("Items shared by " + program.appName + " found: " + result.getLength()); //$NON-NLS-1$ //$NON-NLS-2$
                if (result.getLength() == 0)
                    continue;

                String bookmarkSufix = program.appName;
                if (UFEDChatParser.UFED_CHAT_PREVIEW_MIME.toString().equals(mediaType)) {
                    String source = doc.get(ExtraProperties.UFED_META_PREFIX + "Source"); //$NON-NLS-1$
                    if (source != null)
                        bookmarkSufix = source;
                    String phoneOwner = doc.get(UFEDChatParser.META_PHONE_OWNER);
                    if (phoneOwner != null && !phoneOwner.isEmpty())
                        bookmarkSufix += " by " + phoneOwner; //$NON-NLS-1$
                }

                int labelId = ipedSrc.getBookmarks()
                        .newBookmark(Messages.getString("P2PBookmarker.P2PBookmarkPrefix") + bookmarkSufix); //$NON-NLS-1$
                ArrayList<Integer> ids = new ArrayList<Integer>();
                for (int j = 0; j < result.getLength(); j++)
                    ids.add(result.getId(j));

                ipedSrc.getBookmarks().addBookmark(ids, labelId);
                ipedSrc.getBookmarks().saveState(true);
            }
        } catch (Exception e1) {
            e1.printStackTrace();

        } finally {
            ipedSrc.close();
        }

    }

}
