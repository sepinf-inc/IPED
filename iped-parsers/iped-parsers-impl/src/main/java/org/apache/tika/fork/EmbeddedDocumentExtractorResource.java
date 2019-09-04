package org.apache.tika.fork;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EmbeddedDocumentExtractorResource implements ForkResource {

    private EmbeddedDocumentExtractor extractor;
    private ContentHandler handler;

    public EmbeddedDocumentExtractorResource(EmbeddedDocumentExtractor extractor, ContentHandler handler) {
        this.extractor = extractor;
        this.handler = handler;
    }

    @Override
    public Throwable process(DataInputStream input, DataOutputStream output) throws IOException {

        // int type = input.readUnsignedByte();

        try {
            InputStream is = (InputStream) readObject(input, output);
            Metadata metadata = (Metadata) readObject(input, output);

            extractor.parseEmbedded(is, handler, metadata, true);
            output.write(ForkServer2.DONE);

        } catch (Exception e) {
            output.write(ForkServer2.ERROR);
            e.printStackTrace();
            return e;
        } finally {
            output.flush();
        }

        return null;
    }

    private Object readObject(DataInputStream input, DataOutputStream output)
            throws IOException, ClassNotFoundException {
        Object object = ForkObjectInputStream.readObject(input, this.getClass().getClassLoader());
        if (object instanceof ForkProxy) {
            ((ForkProxy) object).init(input, output);
        }

        // Tell the parent process that we successfully received this object
        output.writeByte(ForkServer2.DONE);
        output.flush();

        return object;
    }

}
