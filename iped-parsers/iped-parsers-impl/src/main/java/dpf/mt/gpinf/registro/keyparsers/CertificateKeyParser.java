package dpf.mt.gpinf.registro.keyparsers;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;

import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.mt.gpinf.registro.model.KeyValue;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;

public class CertificateKeyParser extends HtmlKeyParser {

    @Override
    public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent,
            ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
        ArrayList<KeyNode> kns = kn.getSubKeys();
        if (kns != null) {
            for (int i = 0; i < kns.size(); i++) {
                parseCertificateKey(kns.get(i), kns.get(i).getKeyName(), true, keyPath + "/" + kns.get(i).getKeyName(),
                        parent, handler, metadata, context);
            }
        }
    }

    public void parseCertificateKey(KeyNode kn, String title, boolean hasChildren, String keyPath,
            EmbeddedParent parent, ContentHandler handler, Metadata metadata, ParseContext context)
            throws TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                KeyValue[] kvs = kn.getValues();
                KeyValue blob = null;
                for (int i = 0; i < kvs.length; i++) {
                    if (kvs[i].getValueName().equals("Blob")) {
                        blob = kvs[i];
                        break;
                    }
                }

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                if (blob != null) {

                    // procura no blob a assinatura de inicio do certificado
                    byte[] data = blob.getValueData();
                    int index = 0;
                    for (; index < data.length - 6; index++) {
                        if (data[index] == 0x30) {
                            if (data[index + 1] == (byte) 0x82) {
                                if (data[index + 4] == 0x30) {
                                    if (data[index + 5] == (byte) 0x82)
                                        break;
                                }
                            }
                        }
                    }

                    data = Arrays.copyOfRange(data, index, data.length);
                    ByteArrayInputStream keyStream = new ByteArrayInputStream(data);

                    Metadata kmeta = new Metadata();
                    kmeta.set(TikaCoreProperties.MODIFIED, kn.getLastWrittenAsDate());
                    kmeta.set(HttpHeaders.CONTENT_TYPE, MediaType.application("pkix-cert").toString());
                    kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MediaType.application("pkix-cert").toString());
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(keyStream);
                    kmeta.add(TikaCoreProperties.TITLE, cert.getSubjectX500Principal().getName());

                    context.set(EmbeddedParent.class, parent);
                    extractor.parseEmbedded(new ByteArrayInputStream(data), handler, kmeta, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}