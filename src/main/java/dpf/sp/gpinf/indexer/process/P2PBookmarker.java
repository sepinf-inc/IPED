package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.mg.udi.gpinf.shareazaparser.ShareazaLibraryDatParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;
import dpf.sp.gpinf.indexer.parsers.AresParser;
import dpf.sp.gpinf.indexer.parsers.KnownMetParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.SearchResult;
import gpinf.dev.data.CaseData;

public class P2PBookmarker {
	
	private static Logger LOGGER = LoggerFactory.getLogger(P2PBookmarker.class);
	
	private boolean isIpedReport = false;
	
	public P2PBookmarker(CaseData caseData){
		isIpedReport = caseData.isIpedReport();
	}
	
	class P2PProgram{
	    String hashName;
		String appName;
		
		public P2PProgram(String hashName, String appName){
			this.hashName = hashName;
			this.appName = appName;
		}
	}

	public void createBookmarksForSharedFiles(File caseDir) {
		
		if(isIpedReport)
			return;
		
		LOGGER.info("Pesquisando itens compartilhados via P2P...");

		HashMap<String, P2PProgram> p2pPrograms = new HashMap<String, P2PProgram>();
		
		p2pPrograms.put(KnownMetParser.EMULE_MIME_TYPE, new P2PProgram(HashTask.HASH.EDONKEY.toString(), "Emule"));
		p2pPrograms.put(AresParser.ARES_MIME_TYPE, new P2PProgram(HashTask.HASH.SHA1.toString(), "Ares"));
		p2pPrograms.put(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE, new P2PProgram(HashTask.HASH.MD5.toString(), "Shareaza"));
		p2pPrograms.put(WhatsAppParser.WHATSAPP_CHAT.toString(), new P2PProgram(HashTask.HASH.SHA256.toString(), "WhatsApp"));
		p2pPrograms.put(SkypeParser.FILETRANSFER_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype"));
		p2pPrograms.put(SkypeParser.CONVERSATION_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype"));
		
		IPEDSource ipedSrc = new IPEDSource(caseDir);
		String queryText = ExtraProperties.SHARED_HASHES + ":*";
		IPEDSearcher searcher = new IPEDSearcher(ipedSrc, queryText);
		try {
			SearchResult p2pItems = searcher.search();
			for (int i = 0; i < p2pItems.getLength(); i++){
				int luceneId = ipedSrc.getLuceneId(p2pItems.getId(i));
				Document doc = ipedSrc.getReader().document(luceneId);
				String mediaType = doc.get(IndexItem.CONTENTTYPE);
				P2PProgram program = p2pPrograms.get(mediaType);
				String[] sharedHashes = doc.getValues(ExtraProperties.SHARED_HASHES);
				StringBuilder hashes = new StringBuilder();
				for(String hash : sharedHashes)
				    hashes.append(hash).append(" ");
				
				StringBuilder queryBuilder = new StringBuilder();
				queryBuilder.append(program.hashName + ":(");
				queryBuilder.append(hashes.toString());
				queryBuilder.append(")");
				searcher = new IPEDSearcher(ipedSrc, queryBuilder.toString());
				
				SearchResult result = searcher.search();
				LOGGER.info("Itens compartilhados via " + program.appName + " encontrados: " + result.getLength());
				if(result.getLength() == 0)
					continue;
				
				int labelId = ipedSrc.getMarcadores().newLabel("Provavelmente Compartilhados via " + program.appName);
				ArrayList<Integer> ids = new ArrayList<Integer>();
				for (int j = 0; j < result.getLength(); j++)
					ids.add(result.getId(j));
				
				ipedSrc.getMarcadores().addLabel(ids, labelId);
				ipedSrc.getMarcadores().saveState();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			
		}finally{
			ipedSrc.close();
		}
		
	}

}
