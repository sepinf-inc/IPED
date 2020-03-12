package dpf.sp.gpinf.indexer.process;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import dpf.mg.udi.gpinf.shareazaparser.ShareazaLibraryDatParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.parsers.AresParser;
import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import dpf.sp.gpinf.indexer.parsers.ufed.UFEDChatParser;

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
public class MimeTypesProcessingOrder {

    /** Mapa do mimeType para sua prioridade de processamento */
    private static Map<MediaType, Integer> mediaTypes = installTypesToPostProcess();
    
    private static MediaTypeRegistry mediaRegistry;

    /** Definie as prioridades de processamento dos mimeTypes */
    private static Map<MediaType, Integer> installTypesToPostProcess() {

        Map<MediaType, Integer> mediaTypes = new HashMap<MediaType, Integer>();
        
        //handle wal logs
        mediaTypes.put(SQLite3Parser.MEDIA_TYPE, 1);

        mediaTypes.put(SkypeParser.SKYPE_MIME, 1);

        mediaTypes.put(MediaType.parse(KnownMetParser.EMULE_MIME_TYPE), 1);
        mediaTypes.put(MediaType.parse(AresParser.ARES_MIME_TYPE), 1);
        mediaTypes.put(MediaType.parse(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE), 1);

        mediaTypes.put(WhatsAppParser.WA_DB, 1);
        mediaTypes.put(WhatsAppParser.MSG_STORE, 2);
        mediaTypes.put(WhatsAppParser.CONTACTS_V2, 1);
        mediaTypes.put(WhatsAppParser.CHAT_STORAGE, 2);

        mediaTypes.put(UFEDChatParser.UFED_CHAT_MIME, 1);
        mediaTypes.put(UFEDChatParser.UFED_CHAT_WA_MIME, 1);

        return mediaTypes;
    }
    
    private static synchronized void setMediaRegistry() {
        if(mediaRegistry == null) {
            mediaRegistry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
        }
    }

    /** Obtém a prioridade de processamento do mimeType */
    public static int getProcessingPriority(MediaType mediaType) {
        
        if(mediaRegistry == null) {
            setMediaRegistry();
        }

        do {
            Integer priority = mediaTypes.get(mediaType);
            if (priority != null) {
                return priority;
            }
            mediaType = mediaRegistry.getSupertype(mediaType);
                    
        }while(!MediaType.OCTET_STREAM.equals(mediaType));

        return 0;
    }

    /** Obtém todas as prioridades de processamento configuradas */
    public static Set<Integer> getProcessingPriorities() {
        Set<Integer> priorities = new TreeSet<Integer>();
        for (Integer p : mediaTypes.values())
            priorities.add(p);

        return priorities;
    }

}
