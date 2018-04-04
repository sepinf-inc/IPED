package dpf.sp.gpinf.indexer.process;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.mime.MediaType;

import dpf.mg.udi.gpinf.shareazaparser.ShareazaLibraryDatParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.parsers.AresParser;
import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.parsers.ufed.UFEDChatParser;

/**
 * Classe de definição de prioridade de processamento de itens com base no mimeType.
 * Para cada prioridade, é criada uma fila de processamento contendo os itens com tal prioridade.
 * MimeTypes sem prioridade definida recebem a prioridade padrão zero.
 * Primeiro são processados os itens da fila de prioridade 0, depois da fila de prioridade 1 e assim por diante.
 * Assim é possível configurar dependências de processamento entre os itens. 
 * 
 * @author Nassif
 *
 */
public class MimeTypesProcessingOrder {
	
	/** Mapa do mimeType para sua prioridade de processamento */
	private static Map<MediaType, Integer> mediaTypes = installTypesToPostProcess();
	
	/** Definie as prioridades de processamento dos mimeTypes */
	private static Map<MediaType, Integer> installTypesToPostProcess(){
		
		Map<MediaType, Integer> mediaTypes = new HashMap<MediaType, Integer>();
		
		mediaTypes.put(SkypeParser.SKYPE_MIME, 1);
		
		mediaTypes.put(MediaType.parse(KnownMetParser.EMULE_MIME_TYPE), 1);
		mediaTypes.put(MediaType.parse(AresParser.ARES_MIME_TYPE), 1);
		mediaTypes.put(MediaType.parse(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE), 1);
		
		mediaTypes.put(WhatsAppParser.WA_DB, 1);
		mediaTypes.put(WhatsAppParser.MSG_STORE, 2);
		mediaTypes.put(WhatsAppParser.CONTACTS_V2, 1);
		mediaTypes.put(WhatsAppParser.CHAT_STORAGE, 2);
		
		mediaTypes.put(UFEDChatParser.UFED_CHAT_MIME, 1);
		
		return mediaTypes;
	}
	
	/** Obtém a prioridade de processamento do mimeType */
	public static int getProcessingPriority(MediaType mediaType){
		
		Integer priority = mediaTypes.get(mediaType);
		if(priority != null)
			return priority;
		else
			return 0;
	}
	
	/** Obtém todas as prioridades de processamento configuradas */
	public static Set<Integer> getProcessingPriorities(){
		Set<Integer> priorities = new TreeSet<Integer>();
		for(Integer p : mediaTypes.values())
			priorities.add(p);
		
		return priorities;
	}

}
