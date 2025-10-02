package iped.engine.task;

import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_ID;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_NAME;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_PHONE;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_USERNAME;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import iped.engine.data.IPEDSource;
import iped.engine.localization.Messages;
import iped.engine.search.IPEDSearcher;
import iped.engine.task.index.IndexItem;
import iped.parsers.ares.AresParser;
import iped.parsers.bittorrent.BitTorrentResumeDatEntryParser;
import iped.parsers.bittorrent.BitTorrentResumeDatParser;
import iped.parsers.bittorrent.TransmissionResumeParser;
import iped.parsers.chat.PartyStringBuilderFactory;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.emule.PartMetParser;
import iped.parsers.gdrive.GDriveCloudGraphParser;
import iped.parsers.gdrive.GDriveSnapshotParser;
import iped.parsers.shareaza.ShareazaDownloadParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.threema.ThreemaParser;
import iped.parsers.ufed.UfedChatParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.search.SearchResult;

public class P2PBookmarker {

    private static Logger LOGGER = LoggerFactory.getLogger(P2PBookmarker.class);

    private boolean isIpedReport = false;

    public P2PBookmarker(ICaseData caseData) {
        isIpedReport = caseData.isIpedReport();
    }

    class P2PProgram {
        final List<String> hashNames;
        final String appName;
        final Color color;

        public P2PProgram(String hashName, String appName) {
            this(hashName, appName, null);
        }

        public P2PProgram(String hashName, String appName, Color color) {
            this(Collections.singletonList(hashName), appName, color);
        }

        public P2PProgram(List<String> hashNames, String appName, Color color) {
            this.hashNames = hashNames;
            this.appName = appName;
            this.color = color;
        }
    }

    public void createBookmarksForSharedFiles(File caseDir) {

        if (isIpedReport)
            return;

        LOGGER.info("Searching for shared items...");

        HashMap<String, P2PProgram> p2pPrograms = new HashMap<String, P2PProgram>();

        P2PProgram progEMule = new P2PProgram(HashTask.HASH.EDONKEY.toString(), "Emule", new Color(140, 75, 30));
        p2pPrograms.put(KnownMetParser.EMULE_MIME_TYPE, progEMule);
        p2pPrograms.put(PartMetParser.EMULE_PART_MET_MIME_TYPE, progEMule);

        p2pPrograms.put(AresParser.ARES_MIME_TYPE,
                new P2PProgram(HashTask.HASH.SHA1.toString(), "Ares", new Color(238, 173, 0)));

        List<String> shareazaHashes = Arrays.asList(HashTask.HASH.MD5.toString(), HashTask.HASH.SHA1.toString(), HashTask.HASH.EDONKEY.toString());
        p2pPrograms.put(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE,
                new P2PProgram(shareazaHashes, "Shareaza", new Color(170, 20, 20)));
       
        p2pPrograms.put(ShareazaDownloadParser.SHAREAZA_DOWNLOAD_META,
                new P2PProgram(shareazaHashes, "Shareaza SD", new Color(170, 20, 20)));

        p2pPrograms.put(WhatsAppParser.WHATSAPP_CHAT.toString(),
                new P2PProgram(HashTask.HASH.SHA256.toString(), "WhatsApp", new Color(32, 146, 90)));

        p2pPrograms.put(UfedChatParser.UFED_CHAT_PREVIEW_MIME.toString(),
                new P2PProgram(IndexItem.HASH.toString(), "UFED_Chats", new Color(0, 160, 160)));

        P2PProgram progSkype = new P2PProgram(IndexItem.HASH, "Skype", new Color(50, 150, 220));
        p2pPrograms.put(SkypeParser.FILETRANSFER_MIME_TYPE, progSkype);
        p2pPrograms.put(SkypeParser.CONVERSATION_MIME_TYPE, progSkype);

        p2pPrograms.put(TelegramParser.TELEGRAM_CHAT.toString(),
                new P2PProgram(IndexItem.HASH, "Telegram", new Color(120, 190, 250)));

        p2pPrograms.put(ThreemaParser.THREEMA_CHAT.toString(), new P2PProgram(IndexItem.HASH, "Threema")); // $NON-NLS-1$

        List<String> torrentHashes = Arrays.asList(IndexItem.HASH, HashTask.HASH.MD5.toString(),
                HashTask.HASH.SHA1.toString(), HashTask.HASH.EDONKEY.toString());
        p2pPrograms.put(BitTorrentResumeDatParser.RESUME_DAT_MIME_TYPE,
                new P2PProgram(torrentHashes, "Torrent", new Color(0, 160, 60)));
        p2pPrograms.put(BitTorrentResumeDatEntryParser.RESUME_DAT_ENTRY_MIME_TYPE,
                new P2PProgram(torrentHashes, "Torrent", new Color(0, 160, 60)));
        p2pPrograms.put(TransmissionResumeParser.TRANSMISSION_RESUME_MIME_TYPE,
                new P2PProgram(torrentHashes, "Transmission", new Color(0, 180, 0)));

        P2PProgram progGDrive = new P2PProgram(HashTask.HASH.MD5.toString(), "GoogleDrive");
        p2pPrograms.put(GDriveCloudGraphParser.GDRIVE_CLOUD_GRAPH_REG.toString(), progGDrive);
        p2pPrograms.put(GDriveSnapshotParser.GDRIVE_SNAPSHOT_REG.toString(), progGDrive);

        IPEDSource ipedSrc = new IPEDSource(caseDir);
        String queryText = ExtraProperties.SHARED_HASHES + ":* OR " + ExtraProperties.SHARED_ITEMS + ":*";
        IPEDSearcher searcher = new IPEDSearcher(ipedSrc, queryText);
        try {
            SearchResult p2pItems = searcher.search();
            for (int i = 0; i < p2pItems.getLength(); i++) {
                int luceneId = ipedSrc.getLuceneId(p2pItems.getId(i));
                Document doc = ipedSrc.getReader().document(luceneId);
                String mediaType = doc.get(IndexItem.CONTENTTYPE);
                P2PProgram program = lookupProgram(mediaType, p2pPrograms);
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
                if (isHash) {
                    for (String hash : program.hashNames) {
                        queryBuilder.append(hash + ":("); //$NON-NLS-1$
                        queryBuilder.append(items.toString());
                        queryBuilder.append(") "); //$NON-NLS-1$
                    }
                } else {
                    queryBuilder.append(items.toString());
                }
                queryBuilder.append(")"); //$NON-NLS-1$
                searcher = new IPEDSearcher(ipedSrc, queryBuilder.toString());

                SearchResult result = searcher.search();
                LOGGER.info("Items shared by " + program.appName + " found: " + result.getLength()); //$NON-NLS-1$ //$NON-NLS-2$
                if (result.getLength() == 0)
                    continue;

                String bookmarkSufix = program.appName;
                if (MediaTypes.isInstanceOf(MediaType.parse(mediaType), UfedChatParser.UFED_CHAT_PREVIEW_MIME)) {
                    String source = doc.get(ExtraProperties.UFED_META_PREFIX + "Source");
                    if (source != null)
                        bookmarkSufix = source;
                    String account = doc.get(ExtraProperties.CONVERSATION_ACCOUNT);
                    if (StringUtils.isNotBlank(account)) {
                        if (source != null) {
                            String formattedAccount = PartyStringBuilderFactory
                                    .getBuilder(source)
                                    .withUserId(doc.get(ExtraProperties.CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_ID))
                                    .withName(doc.get(ExtraProperties.CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_NAME))
                                    .withPhoneNumber(doc.get(ExtraProperties.CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_PHONE))
                                    .withUsername(doc.get(ExtraProperties.CONVERSATION_ACCOUNT + CONVERSATION_SUFFIX_USERNAME))
                                    .build();
                            if (StringUtils.isNotBlank(formattedAccount)) {
                                account = formattedAccount;
                            }
                        }
                        bookmarkSufix += " by " + account;
                    }
                }

                int labelId = ipedSrc.getBookmarks()
                        .newBookmark(Messages.getString("P2PBookmarker.P2PBookmarkPrefix") + bookmarkSufix); //$NON-NLS-1$
                if (program.color != null) {
                    ipedSrc.getBookmarks().setBookmarkColor(labelId, program.color);
                }
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

    private P2PProgram lookupProgram(String mediaTypeStr, HashMap<String, P2PProgram> p2pPrograms) {

        if (p2pPrograms.containsKey(mediaTypeStr)) {
            return p2pPrograms.get(mediaTypeStr);
        }

        MediaType mediaType = MediaType.parse(mediaTypeStr);
        P2PProgram program = p2pPrograms.entrySet().stream()
                .filter(e -> MediaTypes.isInstanceOf(mediaType, MediaType.parse(e.getKey())))
                .map(e -> e.getValue())
                .findFirst()
                .orElse(null);

        if (program != null) {
            p2pPrograms.put(mediaTypeStr, program);
        }

        return program;
    }
}
