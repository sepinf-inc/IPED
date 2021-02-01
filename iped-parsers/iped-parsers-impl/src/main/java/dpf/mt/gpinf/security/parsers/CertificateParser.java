package dpf.mt.gpinf.security.parsers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class CertificateParser extends AbstractParser {
    public static final MediaType PEM_MIME = MediaType.application("x-pem-file");
    public static final MediaType DER_MIME = MediaType.application("pkix-cert");
    private static final MediaType PKCS7_MIME = MediaType.application("pkcs7-mime");
    private static final MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");
    private static Set<MediaType> SUPPORTED_TYPES = null;

    public static final Property NOTBEFORE = Property.internalDate("certificate:notbefore"); //$NON-NLS-1$
    public static final Property NOTAFTER = Property.internalDate("certificate:notafter"); //$NON-NLS-1$
    public static final String ISSUER = "certificate:issuer"; //$NON-NLS-1$
    public static final String SUBJECT = "certificate:subject"; //$NON-NLS-1$
    public static final Property ISSUBJECTAUTHORITY = Property.internalBoolean("certificate:subjectIsCertAuthority"); //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        if (SUPPORTED_TYPES == null) {
            SUPPORTED_TYPES = new HashSet<MediaType>();
            SUPPORTED_TYPES.add(PEM_MIME);
            SUPPORTED_TYPES.add(DER_MIME);
            SUPPORTED_TYPES.add(PKCS7_MIME);
            SUPPORTED_TYPES.add(PKCS7_SIGNATURE);
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
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            X509Certificate cert = null;
            String mimeType = metadata.get("Indexer-Content-Type");

            if (mimeType.equals(PKCS7_SIGNATURE.toString())) {
                try (InputStream certStream = new FileInputStream(file)) {
                    CertPath p = cf.generateCertPath(certStream, "PKCS7");
                    List certs = p.getCertificates();
                    XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                    xhtml.startDocument();
                    for (Iterator iterator = certs.iterator(); iterator.hasNext();) {
                        cert = (X509Certificate) iterator.next();
                        generateCertificateHtml(cert, xhtml);
                    }
                    xhtml.endDocument();
                }
            } else {
                InputStream certStream = null;
                try {
                    certStream = new FileInputStream(file);
                    cert = (X509Certificate) cf.generateCertificate(certStream);
                } finally {
                    if (certStream != null) {
                        certStream.close();
                    }
                }
                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                generateCertificateHtml(cert, xhtml);
                xhtml.endDocument();
            }

            metadata.set(NOTBEFORE, cert.getNotBefore());
            metadata.set(NOTAFTER, cert.getNotAfter());
            metadata.set(ISSUER, cert.getIssuerX500Principal().getName());
            metadata.set(SUBJECT, cert.getSubjectX500Principal().getName());
            if (cert.getBasicConstraints() <= -1) {
                metadata.set(ISSUBJECTAUTHORITY, (new Boolean(false)).toString());
            } else {
                metadata.set(ISSUBJECTAUTHORITY, (new Boolean(true)).toString());
            }
            metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain");
            metadata.set(TikaCoreProperties.TITLE, "Certificado:" + cert.getSubjectX500Principal().getName());

        } catch (Exception e) {
            throw new TikaException("Invalid or unkown certificate format.", e);
        } finally {
            tis.close();
        }
    }

    private void generateCertificateHtml(X509Certificate cert, XHTMLContentHandler xhtml)
            throws UnsupportedEncodingException, SAXException {

        xhtml.startElement("table");

        xhtml.startElement("tr");
        xhtml.startElement("th");
        xhtml.characters("Propriedade");
        xhtml.endElement("th");
        xhtml.startElement("th");
        xhtml.characters("Valor");
        xhtml.endElement("th");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Subject");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(cert.getSubjectX500Principal().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Version");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(Integer.toString(cert.getVersion()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Serial Number");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(cert.getSerialNumber().toString());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Signature Algorithm");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(cert.getSigAlgName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Issuer");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(cert.getIssuerX500Principal().getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");

        DateFormat df = DateFormat.getDateInstance();
        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Valid from");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(df.format(cert.getNotBefore()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Valid to");
        xhtml.endElement("td");
        xhtml.startElement("td");
        xhtml.characters(df.format(cert.getNotAfter()));
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.startElement("tr");
        xhtml.startElement("td");
        xhtml.characters("Alternative Names:");
        xhtml.endElement("td");
        xhtml.startElement("td");
        List<String> altNamesStrs = getAltNames(cert);
        for (String altNameStr : altNamesStrs) {
            xhtml.characters(altNameStr);
            xhtml.startElement("br");
            xhtml.endElement("br");// linebreak
        }
        xhtml.endElement("td");
        xhtml.endElement("tr");

        xhtml.endElement("table");

    }

    private List<String> getAltNames(X509Certificate cert) {
        List<String> altNamesStrs = new ArrayList<String>();
        try {
            Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            for (List<?> sanItem : altNames) {
                final Integer itemType = (Integer) sanItem.get(0);
                if (itemType == 0) {
                    String altNameStr = null;
                    final byte[] altNameBytes = (byte[]) sanItem.get(1);
                    ASN1Sequence altNameSeq = getAltnameSequence(altNameBytes);
                    final ASN1TaggedObject obj = (ASN1TaggedObject) altNameSeq.getObjectAt(1);
                    if (obj != null) {
                        ASN1Primitive prim = obj.getObject();
                        // can be tagged one more time
                        if (prim instanceof ASN1TaggedObject) {
                            prim = ASN1TaggedObject.getInstance(((ASN1TaggedObject) prim)).getObject();
                        }

                        if (prim instanceof ASN1OctetString) {
                            altNameStr = new String(((ASN1OctetString) prim).getOctets());
                        } else if (prim instanceof ASN1String) {
                            altNameStr = ((ASN1String) prim).getString();
                        }
                    }
                    if (altNameStr != null) {
                        altNamesStrs.add(altNameStr);
                    }
                }
            }
        } catch (IOException | CertificateParsingException e) {
            // ignore error.
        }
        return altNamesStrs;
    }

    private ASN1Sequence getAltnameSequence(final byte[] sanValue) throws IOException {
        ASN1Primitive obj = null;
        try (final ByteArrayInputStream baInput = new ByteArrayInputStream(sanValue)) {
            try (final ASN1InputStream asnInput = new ASN1InputStream(baInput)) {
                obj = asnInput.readObject();
            }
            if (obj != null) {
                return ASN1Sequence.getInstance(obj);
            } else {
                return null;
            }
        }
    }

    private Date toDate(String timestamp) {
        return DatatypeConverter.parseDateTime(timestamp).getTime();
    }
}
