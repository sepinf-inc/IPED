package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.lucene.document.Document;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.Statistics;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.Worker.IdLenPair;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IndexerContext;

public class IndexTask extends AbstractTask{

	public static boolean indexFileContents = true;
	public static boolean indexUnallocated = false;
	
	Manager manager;
	private Statistics stats;
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	public IndexTask(Worker worker){
		super(worker);
		this.manager = worker.manager;
		this.stats = worker.stats;
	}
	
	public void process(EvidenceFile evidence) throws IOException{
		
		if(!evidence.isToIndex() || evidence.isQueueEnd()){
			if (evidence.isSubItem()) {
				if (!evidence.getFile().delete())
					System.out.println(new Date() + "\t[AVISO]\t" + worker.getName() + " Falha ao deletar " + evidence.getFile().getAbsolutePath());
			}
			return;
		}
			
		stats.updateLastId(evidence.getId());
		
		String textCache = evidence.getParsedTextCache();
		if (textCache != null) {
			Document doc;
			if (indexFileContents)
				doc = IndexItem.Document(evidence, new StringReader(textCache), dateFormat);
			else
				doc = IndexItem.Document(evidence, null, dateFormat);
			
			worker.writer.addDocument(doc);
			manager.textSizes.add(new IdLenPair(evidence.getId(), textCache.length()));

		} else {
			
			Metadata metadata = getMetadata(evidence);
			ParseContext context = getTikaContext(evidence, evidence.isParsed());
			
			TikaInputStream tis = null;
			try {
				tis = evidence.getTikaStream();
			} catch (IOException e) {
				System.out.println(new Date() + "\t[ALERTA]\t" + Thread.currentThread().getName() + " Erro ao abrir: " + evidence.getPath() + " " + e.toString());
			}
			
			ParsingReader reader = null;
			if ( indexFileContents && tis != null && 
				(indexUnallocated || !CarveTask.UNALLOCATED_MIMETYPE.equals(evidence.getMediaType())))
					reader = new ParsingReader(worker.autoParser, tis, metadata, context);
			
			Document doc = IndexItem.Document(evidence, reader, dateFormat);
			int fragments = 0;
			try{
				/* Indexa os arquivos dividindo-os em fragmentos, pois a lib de
				 * indexação consome mta memória com documentos grandes
				 */
				do {
					if (++fragments > 1) {
						stats.incSplits();
						if (fragments == 2)
							manager.remindSplitedDoc(evidence.getId());

						if (IndexFiles.getInstance().verbose)
							System.out.println(new Date() + "\t[INFO]\t" + Thread.currentThread().getName() + "  Dividindo texto de " + evidence.getPath());
					}

					worker.writer.addDocument(doc);

				} while (!Thread.currentThread().isInterrupted() && reader != null && reader.nextFragment());
				
			}finally{
				if (reader != null)
					reader.reallyClose();
				IOUtil.closeQuietly(tis);
			}
			
			if (reader != null)
				manager.textSizes.add(new IdLenPair(evidence.getId(), reader.getTotalTextSize()));
			else
				manager.textSizes.add(new IdLenPair(evidence.getId(), 0));

		}

		
	}
	
	private Metadata getMetadata(EvidenceFile evidence){
		Metadata metadata = new Metadata();
		Long len = evidence.getLength();
		if(len == null)
			len = 0L;
		metadata.set(Metadata.CONTENT_LENGTH, len.toString());
		metadata.set(Metadata.RESOURCE_NAME_KEY, evidence.getName());
		metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, evidence.getMediaType().toString());
		if (evidence.isTimedOut())
			metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");
		return metadata;
	}
	
	private ParseContext getTikaContext(EvidenceFile evidence, final boolean parsed) {
		// DEFINE CONTEXTO: PARSING RECURSIVO, ETC
		ParseContext context = new ParseContext();
		context.set(Parser.class, worker.autoParser);
		IndexerContext indexerContext = new IndexerContext(evidence);
		context.set(IndexerContext.class, indexerContext);
		context.set(EvidenceFile.class, evidence);
		context.set(EmbeddedDocumentExtractor.class, new ExpandContainerTask(context) {
			@Override
			public boolean shouldParseEmbedded(Metadata arg0) {
				return !parsed;
			}
		});
		
		// Tratamento p/ acentos de subitens de ZIP
		ArchiveStreamFactory factory = new ArchiveStreamFactory();
		factory.setEntryEncoding("Cp850");
		context.set(ArchiveStreamFactory.class, factory);
					
		/*PDFParserConfig config = new PDFParserConfig();
		config.setExtractInlineImages(true);
		context.set(PDFParserConfig.class, config);
		*/
		
		return context;
	}

	@Override
	public void init(Properties properties, File confDir) throws Exception {

		String value = properties.getProperty("indexFileContents");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			indexFileContents = Boolean.valueOf(value);
		
		value = properties.getProperty("indexUnallocated");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			indexUnallocated = Boolean.valueOf(value);
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
