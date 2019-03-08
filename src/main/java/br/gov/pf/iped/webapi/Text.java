package br.gov.pf.iped.webapi;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.lucene.document.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.StreamSource;
import gpinf.dev.data.EvidenceFile;

@Path("/sources/{sourceID}/docs/{id}/text")
public class Text {
    @GET
    @Produces(MediaType.TEXT_PLAIN+"; charset=UTF-8")
	public static StreamingOutput content(
			@PathParam("sourceID") int sourceID, 
			@PathParam("id") int id) throws IOException{
    	
    	IPEDSource source = Sources.get(sourceID);
		int luceneID = source.getLuceneId(id);
		Document doc = source.getReader().document(luceneID);
    	final String contentType = doc.getField(IndexItem.CONTENTTYPE).stringValue();
    	final EvidenceFile item = source.getItemByID(id);
		final ParseContext context = Text.getTikaContext(item);
    	final Metadata metadata = new Metadata();
		metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, contentType);
		metadata.set(Metadata.RESOURCE_NAME_KEY, item.getName());
		
		final TikaInputStream is = item.getTikaStream();
		final IndexerDefaultParser parser = new IndexerDefaultParser();
		parser.setPrintMetadata(false);

    	return new StreamingOutput() {
            @Override
			public void write(OutputStream arg0) throws IOException, WebApplicationException {
        		ContentHandler handler = new ToTextContentHandler(arg0, "UTF-8");
				try {
					parser.parse(is, handler, metadata, context);
				} catch (Exception e) {
					throw new WebApplicationException(e);
				}
			}
		};
    }
    
    private static Parser autoParser = new IndexerDefaultParser();
    
	private static ParseContext getTikaContext(EvidenceFile item) throws IOException{
		ParseContext context = new ParseContext();
		context.set(Parser.class, autoParser);
		context.set(ItemInfo.class, ItemInfoFactory.getItemInfo(item));
		
		// Tratamento p/ acentos de subitens de ZIP
		ArchiveStreamFactory factory = new ArchiveStreamFactory();
		factory.setEntryEncoding("Cp850");
		context.set(ArchiveStreamFactory.class, factory);
		
		context.set(StreamSource.class, (StreamSource)item);
		return context;
	}

}
