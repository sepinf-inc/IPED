package br.gov.pf.iped.webapi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.ContentHandler;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import gpinf.dev.data.EvidenceFile;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Documents")
@Path("/sources/{sourceID}/docs/{id}/text")
public class Text {
    
	@ApiOperation(value="Get document's content converted as text")
    @GET
    @Produces(MediaType.TEXT_PLAIN+"; charset=UTF-8")
	public static StreamingOutput content(
			@PathParam("sourceID") int sourceID, 
			@PathParam("id") int id) throws Exception{

    	IPEDSource source = Sources.multiSource.getAtomicSourceBySourceId(sourceID);
    	final EvidenceFile item = source.getItemByID(id);
    	final IndexerDefaultParser parser = new IndexerDefaultParser();
		final ParseContext context = getTikaContext(item, parser, source.getModuleDir());
    	final Metadata metadata = new Metadata();
        
    	ParsingTask.fillMetadata(item, metadata);
        parser.setPrintMetadata(false);
		
    	return new StreamingOutput() {
            @Override
			public void write(OutputStream arg0) throws IOException, WebApplicationException {
        		ContentHandler handler = new ToTextContentHandler(arg0, "UTF-8");
				try (TikaInputStream is = item.getTikaStream()){
					parser.parse(is, handler, metadata, context);
				} catch (Exception e) {
					throw new WebApplicationException(e);
				}
			}
		};
    }
    
    public static ParseContext getTikaContext(EvidenceFile item, Parser parser, File moduleDir) throws Exception {
        ParsingTask expander = new ParsingTask(item, (IndexerDefaultParser) parser);
        expander.init(Configuration.properties, new File(Configuration.configPath, "conf")); //$NON-NLS-1$
        ParseContext context = expander.getTikaContext(); 
        expander.setExtractEmbedded(false);
        context.set(OCROutputFolder.class, new OCROutputFolder(moduleDir));
        return context;
      }

}
