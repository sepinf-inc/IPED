package iped.engine.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import iped.parsers.ares.AresParser;
import iped.parsers.bittorrent.BitTorrentResumeDatEntryParser;
import iped.parsers.bittorrent.BitTorrentResumeDatParser;
import iped.parsers.bittorrent.TorrentFileParser;
import iped.parsers.bittorrent.TransmissionResumeParser;
import iped.parsers.browsers.chrome.CacheIndexParser;
import iped.parsers.discord.DiscordParser;
import iped.parsers.emule.KnownMetParser;
import iped.parsers.emule.PartMetParser;
import iped.parsers.lnk.LNKShortcutParser;
import iped.parsers.mail.RFC822Parser;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.python.PythonParser;
import iped.parsers.shareaza.ShareazaDownloadParser;
import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.sqlite.SQLite3Parser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.threema.ThreemaParser;
import iped.parsers.ufed.UfedChatParser;
import iped.parsers.usnjrnl.UsnJrnlParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.properties.MediaTypes;

/**
 * Classe de definição de prioridade de processamento de itens com base no
 * mimeType. Para cada prioridade, é criada uma fila de processamento contendo
 * os itens com tal prioridade. MimeTypes sem prioridade definida recebem a
 * prioridade padrão zero. Primeiro são processados os itens da fila de
 * prioridade 0, depois da fila de prioridade 1 e assim por diante. Assim é
 * possível configurar dependências de processamento entre os itens.
 *
 * @author Nassif
 *
 */
public class QueuesProcessingOrder {

    /** Mapa do mimeType para sua prioridade de processamento */
    private static Map<MediaType, Integer> mediaTypes = installTypesToPostProcess();

    private static MediaTypeRegistry mediaRegistry;

    /** Definie as prioridades de processamento dos mimeTypes */
    private static Map<MediaType, Integer> installTypesToPostProcess() {
        Map<MediaType, Integer> mediaTypes = new HashMap<MediaType, Integer>();

        // support for embedded splitted images, must be before all other artifacts
        // so they are processed fine if found inside splitted disk images (#1726)
        mediaTypes.put(MediaTypes.E01_IMAGE, 1);
        mediaTypes.put(MediaTypes.EX01_IMAGE, 1);
        mediaTypes.put(MediaTypes.RAW_IMAGE, 1);
        mediaTypes.put(MediaTypes.VMDK_DESCRIPTOR, 1);

        // handle wal logs
        mediaTypes.put(SQLite3Parser.MEDIA_TYPE, 2);

        // links processed after theirs targets 
        mediaTypes.put(LNKShortcutParser.LNK_MEDIA_TYPE, 2);

        // must be after sqlite processing to find storage_db.db
        mediaTypes.put(SkypeParser.SKYPE_MIME, 3);

        //must be processed after all files to link the attachments
        mediaTypes.put(TelegramParser.TELEGRAM_USER_CONF, 2);
        mediaTypes.put(TelegramParser.TELEGRAM_DB, 3);
        mediaTypes.put(TelegramParser.TELEGRAM_DB_IOS, 3);

        mediaTypes.put(CacheIndexParser.CHROME_INDEX_MIME_TYPE, 2);
        mediaTypes.put(MediaType.parse(DiscordParser.CHAT_MIME_TYPE), 3);

        mediaTypes.put(MediaType.parse(KnownMetParser.EMULE_MIME_TYPE), 2);
        mediaTypes.put(MediaType.parse(PartMetParser.EMULE_PART_MET_MIME_TYPE), 2);
        mediaTypes.put(MediaType.parse(AresParser.ARES_MIME_TYPE), 2);
        mediaTypes.put(MediaType.parse(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE), 2);
        mediaTypes.put(MediaType.parse(ShareazaDownloadParser.SHAREAZA_DOWNLOAD_META), 2);

        mediaTypes.put(MediaType.parse(TorrentFileParser.TORRENT_FILE_MIME_TYPE), 2);
        mediaTypes.put(MediaType.parse(BitTorrentResumeDatParser.RESUME_DAT_MIME_TYPE), 3);
        mediaTypes.put(MediaType.parse(BitTorrentResumeDatEntryParser.RESUME_DAT_ENTRY_MIME_TYPE), 3);
        mediaTypes.put(MediaType.parse(TransmissionResumeParser.TRANSMISSION_RESUME_MIME_TYPE), 3);

        mediaTypes.put(WhatsAppParser.WA_DB, 2);
        mediaTypes.put(WhatsAppParser.MSG_STORE, 3);
        mediaTypes.put(WhatsAppParser.MSG_STORE_2, 4);
        mediaTypes.put(WhatsAppParser.CONTACTS_V2, 2);
        mediaTypes.put(WhatsAppParser.CHAT_STORAGE, 3);
        mediaTypes.put(WhatsAppParser.CHAT_STORAGE_2, 4);
        mediaTypes.put(ThreemaParser.CHAT_STORAGE, 3);
        mediaTypes.put(ThreemaParser.CHAT_STORAGE_F, 4);

        // required to load ContactPhotos and Attachments
        mediaTypes.put(MediaTypes.UFED_CONTACT_MIME, 2);
        mediaTypes.put(MediaTypes.UFED_USER_ACCOUNT_MIME, 2);
        mediaTypes.put(MediaTypes.UFED_EMAIL_MIME, 2);

        // required to load Attachments, Coordinates, Contacts and ContactPhotos
        mediaTypes.put(UfedChatParser.UFED_CHAT_MIME, 3);
        mediaTypes.put(MediaTypes.UFED_MESSAGE_MIME, 4);

        // avoid NPE when the parser gets the item from parseContext when external
        // parsing is on
        mediaTypes.put(UsnJrnlParser.USNJRNL_$J, 2);

        mediaTypes.put(Win10MailParser.WIN10_MAIL_DB, 2);
        mediaTypes.put(RFC822Parser.RFC822_PARTIAL0_MIME, 2);
        mediaTypes.put(RFC822Parser.RFC822_PARTIAL1_MIME, 2);

        return mediaTypes;
    }

    private static synchronized void setMediaRegistry() {

        if (mediaRegistry == null) {
            mediaRegistry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
        }

        // also install python parsers mediaType queue order
        mediaTypes.putAll(PythonParser.getMediaTypesToQueueOrder());
    }

    /** Obtém a prioridade de processamento do mimeType */
    public static int getProcessingQueue(MediaType mediaType) {

        if (mediaRegistry == null) {
            setMediaRegistry();
        }

        do {
            Integer priority = mediaTypes.get(mediaType);
            if (priority != null) {
                return priority;
            }
            mediaType = mediaRegistry.getSupertype(mediaType);

        } while (mediaType != null && !MediaType.OCTET_STREAM.equals(mediaType));

        return 0;
    }

    /** Obtém todas as prioridades de processamento configuradas */
    public static Set<Integer> getProcessingQueues() {
        Set<Integer> priorities = new TreeSet<Integer>();
        for (Integer p : mediaTypes.values())
            priorities.add(p);

        return priorities;
    }

}
