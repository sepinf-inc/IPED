package org.apache.tika.fork;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EmbeddedDocumentExtractorProxy implements EmbeddedDocumentExtractor, ForkProxy {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final int STREAM = 1;

    private static final int HANDLER = 2;

    private static final int METADATA = 3;

    private final int resource;

    private transient DataInputStream input;

    private transient DataOutputStream output;

    public EmbeddedDocumentExtractorProxy(int resource) {
        this.resource = resource;
    }

    @Override
    public void init(DataInputStream input, DataOutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        return true;
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException {

        // System.err.println(count++);

        try {
            output.writeByte(ForkServer2.RESOURCE);
            output.writeByte(resource);

            List<ForkResource> r = new ArrayList<>();

            sendObject(stream, r);
            sendObject(metadata, r);

            waitForResponse(r);

        } catch (Exception e) {
            // do not throw exception with embedded doc to not interrupt parent doc parsing
            // throw new IOException(e);
            e.printStackTrace();
        }

    }

    private void sendObject(Object object, List<ForkResource> resources) throws IOException, TikaException {
        int n = resources.size();
        if (object instanceof InputStream) {
            resources.add(new InputStreamResource2((InputStream) object));
            object = new InputStreamProxy2(n, null);

        } else if (object instanceof ContentHandler) {
            resources.add(new ContentHandlerResource2((ContentHandler) object));
            object = new ContentHandlerProxy2(n);
        }

        try {
            ForkObjectInputStream.sendObject(object, output);

        } catch (NotSerializableException nse) {
            // Build a more friendly error message for this
            throw new TikaException(
                    "Unable to serialize " + object.getClass().getSimpleName() + " to pass to the Forked Parser", nse);
        }

        waitForResponse(resources);
    }

    private Throwable waitForResponse(List<ForkResource> resources) throws IOException {
        output.flush();
        while (true) {
            int type = input.read();
            if (type == -1) {
                throw new IOException("Lost connection to a forked client process");
            } else if (type == ForkServer2.RESOURCE) {
                ForkResource resource = resources.get(input.readUnsignedByte());
                resource.process(input, output);
            } else if ((byte) type == ForkServer2.ERROR) {
                throw new IOException("Error waiting response from fork client");
            } else {
                return null;
            }
        }
    }

}
