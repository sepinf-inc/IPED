package iped.parsers.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.security.capi.CapiBlob;

public class CryptoAPIBlobParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
	public static final MediaType CAPI_MIME = MediaType.application("crypto-api-file");
    private static Set<MediaType> SUPPORTED_TYPES = Collections.singleton(CAPI_MIME);
    public static final String ALIAS = "capi:alias"; //$NON-NLS-1$
    public static final Property HASPRIVATEKEY = Property.internalBoolean("capi:hasPrivateKey"); //$NON-NLS-1$
    public static final Property HASPUBLICKEY = Property.internalBoolean("capi:hasPublicKey"); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();

        try {
            CapiBlob cb = new CapiBlob(tis);

            metadata.set(ALIAS, cb.getName());
            metadata.set(HASPRIVATEKEY, cb.getExPrivKeyLen() > 0);
            metadata.set(HASPUBLICKEY, cb.getExPubKeyLen() > 0);

        } catch (Exception e) {

        }

    }

}
