package br.gov.pf.iped.webapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.sleuthkit.datamodel.TskCoreException;

import dpf.sp.gpinf.indexer.search.IPEDSource;
import gpinf.dev.data.EvidenceFile;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Documents")
@Path("sources/{sourceID}/docs/{id}/thumb")
public class Thumbnail {

	@ApiOperation(value="Get document's thumbnail")
    @GET
    @Produces("image/jpg")
    public StreamingOutput content(
            @PathParam("sourceID") int sourceID,
            @PathParam("id") int id)
                    throws TskCoreException, IOException, URISyntaxException{

        IPEDSource source = Sources.multiSource.getAtomicSourceBySourceId(sourceID);
        EvidenceFile item = source.getItemByID(id);
        final byte[] thumb = item.getThumb() != null ? item.getThumb() : new byte[0];
        return new StreamingOutput() {
            @Override
            public void write(OutputStream arg0) throws IOException, WebApplicationException {
                IOUtils.copy(new ByteArrayInputStream(thumb), arg0);
            }};
    }
}