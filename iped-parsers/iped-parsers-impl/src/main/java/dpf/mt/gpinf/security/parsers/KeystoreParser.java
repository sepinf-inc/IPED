package dpf.mt.gpinf.security.parsers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class KeystoreParser extends AbstractParser {
    private static final MediaType PKCS12_MIME = MediaType.application("x-pkcs12");
    private static final MediaType JAVA_KEYSTORE = MediaType.application("x-java-keystore");
    private static Set<MediaType> SUPPORTED_TYPES = null;
    public static final Property PRIVATEKEY = Property.internalText("certificate:privatekey"); //$NON-NLS-1$

    static String DEFAULT_JKS_KEYSTORE_PASSWORD = "changeit";
    static List<String> passwordList = null;
    public static final Property PASSWORD = Property.internalText("keystore:password"); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        if (SUPPORTED_TYPES == null) {
            SUPPORTED_TYPES = new HashSet<MediaType>();
            SUPPORTED_TYPES.add(PKCS12_MIME);
            SUPPORTED_TYPES.add(JAVA_KEYSTORE);
        }

        if (passwordList == null) {
            passwordList = new ArrayList();
            passwordList.add(DEFAULT_JKS_KEYSTORE_PASSWORD);
            passwordList.add("1234");
            passwordList.add("mo");
        }

        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File file = tis.getFile();

        try {
            String mimeType = metadata.get("Indexer-Content-Type");
            KeyStore p12 = null;

            if (mimeType.equals(JAVA_KEYSTORE.toString())) {
                p12 = KeyStore.getInstance("JKS");
            } else {
                p12 = KeyStore.getInstance("PKCS12");
            }

            String password = null;
            boolean loaded = false;
            for (Iterator<String> iterator = passwordList.iterator(); iterator.hasNext();) {
                password = iterator.next();

                try (InputStream keyStoreStream = new FileInputStream(file)) {
                    p12.load(keyStoreStream, password.toCharArray());
                    loaded = true;
                    metadata.set(PASSWORD, password);
                    break;
                } catch (IOException e) {
                    // se for erro de senha ignora-o para tentar a próxima senha
                    // se não for erro de senha, redispara a exceção encapsulada em um objeto
                    // TikaException.
                    // if(!(e.getMessage().contains("keystore password was
                    // incorrect")||e.getMessage().contains("wrong
                    // password")||e.getMessage().contains("password was incorrect"))){
                    if (!(e.getCause() instanceof UnrecoverableKeyException)) {
                        throw new TikaException(e.getMessage(), e);
                    }
                }
            }
            if (!loaded) {
                // nenhuma das senhas tentadas era a correta.
                throw new EncryptedDocumentException("Documento encriptado com senha diferente das senhas tentadas.");
            }

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));
            if (extractor.shouldParseEmbedded(metadata)) {
                Enumeration<String> e = p12.aliases();
                while (e.hasMoreElements()) {
                    String alias = (String) e.nextElement();
                    X509Certificate cert = (X509Certificate) p12.getCertificate(alias);

                    Metadata kmeta = new Metadata();
                    try {
                        Key key = p12.getKey(alias, password.toCharArray());
                        if (key instanceof PrivateKey) {
                            // se tem uma privatekey a passa.
                            kmeta.add(PRIVATEKEY, Base64.getEncoder().encodeToString(key.getEncoded()));
                        } else {
                            // se tem uma privatekey a passa.
                        }
                    } catch (UnrecoverableKeyException ue) {
                        kmeta.add(PRIVATEKEY, "Protegida por senha desconhecida.");
                        // tem uma privatekey mas não foi possivel extraí-la por causa da senha inválida
                    }
                    parseCertificate(alias, cert, handler, kmeta, context);
                }
            }

        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new TikaException("Erro de formatação da base de certificados.", e);
        }
    }

    public void parseCertificate(String alias, X509Certificate cert, ContentHandler handler, Metadata metadata,
            ParseContext context) throws IOException, SAXException, TikaException {
        try {
            InputStream certStream = new ByteArrayInputStream(cert.getEncoded());
            parseCertificate(alias, certStream, handler, metadata, context);
        } catch (CertificateEncodingException e) {
            throw new IOException(e);
        }
    }

    public void parseCertificate(String alias, InputStream certStream, ContentHandler handler, Metadata kmeta,
            ParseContext context) throws IOException, SAXException, TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        try {
            kmeta.set(HttpHeaders.CONTENT_TYPE, MediaType.application("pkix-cert").toString());
            kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MediaType.application("pkix-cert").toString());
            kmeta.add(TikaCoreProperties.TITLE, alias);

            extractor.parseEmbedded(certStream, handler, kmeta, false);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void parsePKCS7(String alias, InputStream certStream, ContentHandler handler, Metadata metadata,
            ParseContext context) throws IOException, SAXException, TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                Metadata kmeta = new Metadata();
                kmeta.set(HttpHeaders.CONTENT_TYPE, MediaType.application("pkcs7-signature").toString());
                kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                        MediaType.application("pkcs7-signature").toString());
                kmeta.add(TikaCoreProperties.TITLE, alias);

                extractor.parseEmbedded(certStream, handler, kmeta, false);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

}
