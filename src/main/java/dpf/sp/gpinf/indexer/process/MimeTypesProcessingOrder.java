package dpf.sp.gpinf.indexer.process;

import java.util.HashSet;
import java.util.Set;

import org.apache.tika.mime.MediaType;

import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;

public class MimeTypesProcessingOrder {
	
	private static Set<MediaType> mediaTypes = installTypesToPostProcess();
	
	private static Set<MediaType> installTypesToPostProcess(){
		
		Set<MediaType> mediaTypes = new HashSet<MediaType>();
		
		mediaTypes.add(WhatsAppParser.MSG_STORE);
		mediaTypes.add(WhatsAppParser.WA_DB);
		mediaTypes.add(SkypeParser.SKYPE_MIME);
		
		return mediaTypes;
	}
	
	public static boolean isToProcessAtEnd(MediaType mediaType){
		
		if(mediaTypes.contains(mediaType))
			return true;
		else
			return false;
	}

}
