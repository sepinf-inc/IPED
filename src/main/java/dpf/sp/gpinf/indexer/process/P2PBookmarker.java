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
import dpf.sp.gpinf.indexer.Messages;
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
		
		LOGGER.info("Searching items shared by P2P..."); //$NON-NLS-1$

		HashMap<String, P2PProgram> p2pPrograms = new HashMap<String, P2PProgram>();
		
		p2pPrograms.put(KnownMetParser.EMULE_MIME_TYPE, new P2PProgram(HashTask.HASH.EDONKEY.toString(), "Emule")); //$NON-NLS-1$
		p2pPrograms.put(AresParser.ARES_MIME_TYPE, new P2PProgram(HashTask.HASH.SHA1.toString(), "Ares")); //$NON-NLS-1$
		p2pPrograms.put(ShareazaLibraryDatParser.LIBRARY_DAT_MIME_TYPE, new P2PProgram(HashTask.HASH.MD5.toString(), "Shareaza")); //$NON-NLS-1$
		p2pPrograms.put(WhatsAppParser.WHATSAPP_CHAT.toString(), new P2PProgram(HashTask.HASH.SHA256.toString(), "WhatsApp")); //$NON-NLS-1$
		p2pPrograms.put(SkypeParser.FILETRANSFER_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype")); //$NON-NLS-1$
		p2pPrograms.put(SkypeParser.CONVERSATION_MIME_TYPE, new P2PProgram(IndexItem.HASH, "Skype")); //$NON-NLS-1$
		
		IPEDSource ipedSrc = new IPEDSource(caseDir);
		String queryText = ExtraProperties.SHARED_HASHES + ":*"; //$NON-NLS-1$
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
				    hashes.append(hash).append(" "); //$NON-NLS-1$
				
				StringBuilder queryBuilder = new StringBuilder();
				queryBuilder.append(program.hashName + ":("); //$NON-NLS-1$
				queryBuilder.append(hashes.toString());
				queryBuilder.append(")"); //$NON-NLS-1$
				searcher = new IPEDSearcher(ipedSrc, queryBuilder.toString());
				
				SearchResult result = searcher.search();
				LOGGER.info("Items shared by " + program.appName + " found: " + result.getLength()); //$NON-NLS-1$ //$NON-NLS-2$
				if(result.getLength() == 0)
					continue;
				
				int labelId = ipedSrc.getMarcadores().newLabel(Messages.getString("P2PBookmarker.P2PBookmarkPrefix") + program.appName); //$NON-NLS-1$
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
