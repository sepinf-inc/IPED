package iped.parsers.security;

import static iped.parsers.util.Messages.getOrDefault;
import static iped.parsers.util.Messages.getString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cms.AuthenticatedData;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.cert.X509AttributeCertificateHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.PKIXRecipientId;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.X509TrustedCertificateBlock;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS12PfxPdu;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.security.icpbrasil.PessoaFisicaDataParser.PessoaFisicaField;
import iped.parsers.security.icpbrasil.X509OtherName;
import iped.parsers.util.TableHTMLContentHandler;
import iped.utils.DateUtil;

public class CryptoObjectParser extends AbstractParser {

    private static final long serialVersionUID = 5607548651956823436L;

    private static final Logger logger = LoggerFactory.getLogger(CryptoObjectParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(//
            PemUtils.PEM_MIME, //
            ASN1SequenceUtils.ASN1_SEQUENCE_MIME, //
            CryptoObjectMimeTypes.X509_CERT_TYPE, //
            CryptoObjectMimeTypes.X509_CRL_TYPE, //
            CryptoObjectMimeTypes.PKCS10_TYPE, //
            CryptoObjectMimeTypes.PKCS7_DATA_TYPE, //
            CryptoObjectMimeTypes.PKCS7_SIGNED_DATA_TYPE, //
            CryptoObjectMimeTypes.PKCS8_UNENCRYPTED_TYPE, //
            CryptoObjectMimeTypes.PKCS8_ENCRYPTED_TYPE, //
            CryptoObjectMimeTypes.PKCS12_TYPE //
    );

    private static final String META_PREFIX = "crypto:";
    private static final String META_OBJECT_PREFIX = META_PREFIX + "object:";
    private static final String META_CERT_PREFIX = META_PREFIX + "certificate:";
    private static final String META_CRL_PREFIX = META_PREFIX + "crl:";
    private static final String META_CSR_PREFIX = META_PREFIX + "csr:";
    private static final String META_KEY_PREFIX = META_PREFIX + "key:";
    private static final String META_CMS_PREFIX = META_PREFIX + "cms:";
    private static final String META_PKCS12_PREFIX = META_PREFIX + "pkcs12:";

    private static final String META_OBJECT_CLASS = META_OBJECT_PREFIX + "class";

    public static final Map<ASN1ObjectIdentifier, String> OID_TO_NAME = new HashMap<>();
    public static final Map<Integer, String> RECEIPIENT_ID_TO_NAME = new HashMap<>();
    static {
        OID_TO_NAME.put(CMSObjectIdentifiers.data, "data");
        OID_TO_NAME.put(CMSObjectIdentifiers.signedData, "signedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.envelopedData, "envelopedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.signedAndEnvelopedData, "signedAndEnvelopedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.digestedData, "digestedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.encryptedData, "encryptedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.authenticatedData, "authenticatedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.compressedData, "compressedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.authEnvelopedData, "authEnvelopedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.timestampedData, "timestampedData");
        OID_TO_NAME.put(CMSObjectIdentifiers.zlibCompress, "zlibCompress");

        RECEIPIENT_ID_TO_NAME.put(RecipientId.kek, "kek");
        RECEIPIENT_ID_TO_NAME.put(RecipientId.kem, "kem");
        RECEIPIENT_ID_TO_NAME.put(RecipientId.keyAgree, "keyAgree");
        RECEIPIENT_ID_TO_NAME.put(RecipientId.keyTrans, "keyTrans");
        RECEIPIENT_ID_TO_NAME.put(RecipientId.password, "password");
    }

    private DateFormat createDateFormat() {
        return new SimpleDateFormat(getString("CryptoObjectParser.DateFormat"));
    }

    private EmbeddedDocumentExtractor createdEmbeddedDocumentExtractor(ParseContext context) {
        return context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        byte[] bytes = IOUtils.toByteArray(stream);
        ByteArrayInputStream bytesStream = new ByteArrayInputStream(bytes);

        Object result = null;
        if (PemUtils.isPossiblyPem(bytesStream, metadata)) {
            result = CryptoObjectDecoder.getInstance().parsePem(bytesStream);
        }

        if (result == null) {
            if (ASN1SequenceUtils.isPotentiallyValidASN1Sequence(bytesStream)) {
                result = CryptoObjectDecoder.getInstance().parseObject(bytes);
            }
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.element("style", //
                "table {border-collapse: collapse} " + //
                        "table, td, th {border: 1px solid black; padding: 5px} " + //
                        "pre { padding: 10px 15px; border: 2px solid black; background-color: #EEE} " + // ;
                        "ol, ul {margin: 2px; padding-left: 25px } " + //
                        "th {text-align:left; background-color: #CCC} " //
        );

        try {
            if (result != null) {
                if (result instanceof List) {
                    processObjectsList((List<?>) result, xhtml, metadata, context);
                } else {
                    processObject(result, xhtml, metadata, context);
                }
            } else {
                xhtml.element("p", "No result parsed");
            }
        } catch (GeneralSecurityException e) {
            logger.error("Error parsing object", e);
            xhtml.element("p", "Error parsing object: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error parsing object", e);
            xhtml.element("p", "Error parsing object: " + e.getMessage());
            throw e;
        } finally {
            xhtml.endDocument();
        }
    }

    protected void processObjectsList(List<?> list, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws TikaException, SAXException, IOException {

        if (list.isEmpty()) {
            xhtml.element("p", "No objects in the list");
        } else {
            EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);
            int index = 0;
            for (Object object : list) {
                if (object instanceof PemObject) {
                    addEmbbeddedObject((PemObject) object, index++, extractor);
                } else {
                    logger.warn("Unexpected object in the list: " + object.getClass());
                }
            }
        }
    }

    protected void processObject(Object cryptoObject, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws TikaException, SAXException, IOException, GeneralSecurityException {

        metadata.add("X-Parsed-Crypto-Object-Type", cryptoObject.getClass().getName());

        xhtml.element("h3", cryptoObject.getClass().getSimpleName());

        if (cryptoObject instanceof X509Certificate) {
            processCertificate((X509Certificate) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof X509CRL) {
            processCRL((X509CRL) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof ContentInfo) {
            processContentInfo((ContentInfo) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof X509AttributeCertificateHolder) {
            processAttributeCertificateHolder((X509AttributeCertificateHolder) cryptoObject, metadata, xhtml, context);

        } else if (cryptoObject instanceof PKCS10CertificationRequest) {
            processCSR((PKCS10CertificationRequest) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PrivateKey) {
            processPrivateKey((PrivateKey) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PublicKey) {
            processPublicKey((PublicKey) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PEMKeyPair) {
            processPEMKeyPair((PEMKeyPair) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof SubjectPublicKeyInfo) {
            processSubjectPublicKeyInfo((SubjectPublicKeyInfo) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PrivateKeyInfo) {
            processPrivateKeyInfo((PrivateKeyInfo) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof EncryptedPrivateKeyInfo) {
            processEncryptedPrivateKeyInfo((EncryptedPrivateKeyInfo) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PKCS8EncryptedPrivateKeyInfo) {
            processPKCS8EncryptedPrivateKeyInfo((PKCS8EncryptedPrivateKeyInfo) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof PEMEncryptedKeyPair) {
            processEncryptedKeyPair((PEMEncryptedKeyPair) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof X9ECParameters) {
            processECParameters((X9ECParameters) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof Pfx) {
            processPfx((Pfx) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof ASN1ObjectIdentifier) {
            processASN1ObjectIdentifier((ASN1ObjectIdentifier) cryptoObject, xhtml, metadata, context);

        } else if (cryptoObject instanceof X509TrustedCertificateBlock) {
            processTrustedCertificateBlock((X509TrustedCertificateBlock) cryptoObject, xhtml, metadata, context);

        } else {
            metadata.set(META_OBJECT_CLASS, "Unknown");

            xhtml.element("p", "Unknown or not specifically handled object type: " + cryptoObject.getClass().getName());
        }
    }

    private void addEmbbeddedObject(PemObject pemObject, int index, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        String name = String.format("pem-object-%d-[%s].pem", index, pemObject.getType());

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (PemWriter writer = new PemWriter(new OutputStreamWriter(byteStream, StandardCharsets.UTF_8))) {
            writer.writeObject(pemObject);
        }

        addEmbbeddedObject(byteStream.toByteArray(), name, extractor);
    }

    private void addEmbbeddedObject(byte[] data, String name, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        addEmbbeddedObject(new ByteArrayInputStream(data), name, extractor);
    }

    private void addEmbbeddedObject(InputStream stream, String name, EmbeddedDocumentExtractor extractor) throws SAXException, IOException {

        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);

        extractor.parseEmbedded(stream, new DefaultHandler(), metadata, true);
    }

    private void processContentInfo(ContentInfo ci, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException {

        metadata.set(META_OBJECT_CLASS, ContentInfo.class.getSimpleName());

        String typeString = OID_TO_NAME.getOrDefault(ci.getContentType(), ci.getContentType().getId());

        metadata.set(META_CMS_PREFIX + "contentType", typeString);

        TableHTMLContentHandler table = new TableHTMLContentHandler(xhtml, metadata);

        table.startTable();
        table.addRow(true, getString("CryptoObjectParser.CMS.ContentType"), typeString + " (" + ci.getContentType().getId() + ")");

        try {

            if (ci.getContentType().equals(CMSObjectIdentifiers.signedData)) {

                processCmsSignedData(ci, xhtml, metadata, context);

            } else if (ci.getContentType().equals(CMSObjectIdentifiers.envelopedData)) {

                processCmsEnvelopedData(ci, xhtml, metadata, context);

            } else if (ci.getContentType().equals(CMSObjectIdentifiers.signedAndEnvelopedData)) {

                processCmsSignedData(ci, xhtml, metadata, context);
                processCmsEnvelopedData(ci, xhtml, metadata, context);

            } else if (ci.getContentType().equals(CMSObjectIdentifiers.authenticatedData)) {

                processCmsAuthenticatedData(ci, xhtml, metadata, context);

            } else if (ci.getContentType().equals(CMSObjectIdentifiers.authEnvelopedData)) {
                // not implemented
            } else if (ci.getContentType().equals(CMSObjectIdentifiers.digestedData)) {
                // not implemented
            } else if (ci.getContentType().equals(CMSObjectIdentifiers.compressedData)) {
                // not implemented
            }
        } catch (CMSException e) {
            logger.warn("Error processing CMS encoded data", e);
        }
        table.endTable();

        dumpObjectAsString(ci, xhtml);
    }

    @SuppressWarnings("unchecked")
    private void processCmsEnvelopedData(ContentInfo ci, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws CMSException, SAXException, IOException {
        CMSEnvelopedData cms = new CMSEnvelopedData(ci);

        // process RecipientInfos
        xhtml.startElement("tr");
        xhtml.element("th", getString("CryptoObjectParser.CMS.Recipients"));
        xhtml.startElement("td");
        xhtml.startElement("ul");
        for (RecipientInformation ri : cms.getRecipientInfos()) {
            xhtml.startElement("li");
            RecipientId riId = ri.getRID();
            String type = StringUtils.firstNonBlank(RECEIPIENT_ID_TO_NAME.get(riId.getType()), "type-" + riId.getType());
            xhtml.element("b", type);
            if (riId instanceof PKIXRecipientId) {
                PKIXRecipientId pkixriId = (PKIXRecipientId) riId;
                String issuerAndSerialNumber = String.format("Issuer=[%s], SerialNumber=[%s]", pkixriId.getIssuer(), pkixriId.getSerialNumber());
                metadata.add(META_CMS_PREFIX + type + ":issuerAndSerialNumber", issuerAndSerialNumber);
                xhtml.characters(": " + issuerAndSerialNumber);
            }
            xhtml.endElement("li");
        }
        xhtml.endElement("ul");
        xhtml.endElement("td");
        xhtml.endElement("tr");

        // process OriginatorInfo
        if (cms.getOriginatorInfo() != null) {
            xhtml.startElement("tr");
            xhtml.element("th", getString("CryptoObjectParser.CMS.Originator"));
            xhtml.startElement("td");
            xhtml.startElement("ul");
            EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);

            Collection<X509CertificateHolder> certs = cms.getOriginatorInfo().getCertificates().getMatches(null);
            int index = 0;
            for (X509CertificateHolder cert : certs) {
                xhtml.element("li", cert.getSubject().toString());
                String name = String.format("originator-%d-[%s].crt", index++, cert.getSubject().toString());
                addEmbbeddedObject(cert.getEncoded(), name, extractor);
            }
            xhtml.endElement("ul");
            xhtml.endElement("td");
            xhtml.endElement("tr");
        }

    }

    @SuppressWarnings("unchecked")
    private void processCmsSignedData(ContentInfo ci, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException, CMSException {

        CMSSignedData cms = new CMSSignedData(ci);

        EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);

        // extract CMS certificates
        Collection<X509CertificateHolder> certs = cms.getCertificates().getMatches(null);
        int index = 0;
        for (X509CertificateHolder cert : certs) {
            String name = String.format("certificate-%d-[%s].crt", index++, cert.getSubject().toString());
            addEmbbeddedObject(cert.getEncoded(), name, extractor);
        }

        // extract SignedContent
        CMSTypedData signedContent = cms.getSignedContent();
        if (signedContent != null) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                signedContent.write(bytes);
                String name = ci.getContentType().equals(CMSObjectIdentifiers.signedAndEnvelopedData) ? "signedContent.p7m" : "signedContent.dat";
                addEmbbeddedObject(bytes.toByteArray(), name, extractor);
            } catch (IOException | CMSException e) {
                logger.warn("Error extracting CMSSignedData content", e);
            }
        }

        // identify the signers
        xhtml.element("th", getString("CryptoObjectParser.CMS.Signers"));
        xhtml.startElement("td");
        if (cms.getSignerInfos().size() > 0) {

            xhtml.startElement("ul");
            for (SignerInformation signerInfo : cms.getSignerInfos()) {
                Collection<X509CertificateHolder> signersCerts = cms.getCertificates().getMatches(signerInfo.getSID());
                for (X509CertificateHolder cert : signersCerts) {
                    xhtml.element("li", cert.getSubject().toString());
                    metadata.add(META_CMS_PREFIX + "signers", cert.getSubject().toString());
                }
            }
            xhtml.endElement("ul");

        } else {
            xhtml.characters(getString("CryptoObjectParser.CMS.NoSignersFound"));
        }
        xhtml.endElement("td");
    }

    private void processCmsAuthenticatedData(ContentInfo ci, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException {

        // extracts EncapsulatedContentInfo
        EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);
        AuthenticatedData authData = AuthenticatedData.getInstance(ci.getContent());
        byte[] encapsulatedContentInfo = ASN1OctetString.getInstance(authData.getEncapsulatedContentInfo().getContent()).getOctets();
        addEmbbeddedObject(encapsulatedContentInfo, "encapsulatedContentInfo.dat", extractor);

    }

    private void processCertificate(X509Certificate cert, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException, TikaException, CertificateEncodingException {

        metadata.set(META_OBJECT_CLASS, X509Certificate.class.getSimpleName());

        X509CertificateHolder certHolder = new X509CertificateHolder(cert.getEncoded());
        DateFormat df = createDateFormat();

        metadata.set(META_CERT_PREFIX + "subject", certHolder.getSubject().toString());
        metadata.set(META_CERT_PREFIX + "issuer", certHolder.getIssuer().toString());
        metadata.set(META_CERT_PREFIX + "serialNumber", certHolder.getSerialNumber().toString());
        metadata.set(META_CERT_PREFIX + "notBefore", DateUtil.dateToString(certHolder.getNotBefore()));
        metadata.set(META_CERT_PREFIX + "notAfter", DateUtil.dateToString(certHolder.getNotAfter()));
        metadata.set(META_CERT_PREFIX + "publicKeyAlgorithm", cert.getPublicKey().getAlgorithm());
        metadata.set(META_CERT_PREFIX + "signatureAlgorithm", cert.getSigAlgName());

        TableHTMLContentHandler table = new TableHTMLContentHandler(xhtml, metadata);

        table.startTable();
        table.addRow(true, getString("CryptoObjectParser.Cert.Subject"), certHolder.getSubject().toString());
        table.addRow(true, getString("CryptoObjectParser.Cert.Issuer"), certHolder.getIssuer().toString());
        table.addRow(true, getString("CryptoObjectParser.Cert.SerialNumber"), certHolder.getSerialNumber().toString());
        table.addRow(true, getString("CryptoObjectParser.Cert.ValidFrom"), df.format(certHolder.getNotBefore()));
        table.addRow(true, getString("CryptoObjectParser.Cert.ValidTo"), df.format(certHolder.getNotAfter()));
        table.addRow(true, getString("CryptoObjectParser.Cert.PublicKeyAlgorithm"), cert.getPublicKey().getAlgorithm());
        table.addRow(true, getString("CryptoObjectParser.Cert.SignatureAlgorithm"), cert.getSigAlgName());

        // Process Alternative Names
        table.startElement("tr");
        table.element("th", getString("CryptoObjectParser.Cert.AlternativeNames"));
        table.startElement("td");
        processAlternativeNames(cert, xhtml, metadata);
        table.endElement("td");
        table.endElement("tr");
        table.endTable();

        xhtml.endElement("br");

        dumpObjectAsString(cert, xhtml);
    }

    private void processAlternativeNames(X509Certificate cert, XHTMLContentHandler xhtml, Metadata metadata) throws SAXException {
        try {
            Collection<?> alternativeNames = JcaX509ExtensionUtils.getSubjectAlternativeNames(cert);

            if (alternativeNames.isEmpty()) {
                xhtml.element("em", getString("CryptoObjectParser.Cert.NoAlternativeNamesFound"));
            } else {
                xhtml.startElement("ol");
                for (Object object : alternativeNames) {
                    xhtml.startElement("li");
                    String altName = processAlternativeNameValue(((List<?>) object));
                    xhtml.characters(altName != null ? altName : "---");
                    xhtml.endElement("li");
                    metadata.add(META_CERT_PREFIX + "alternativeNames", altName);
                }
                xhtml.endElement("ol");
            }
        } catch (CertificateParsingException e) {
            xhtml.characters("Error getting alternative name: " + e.getMessage());
        }
    }

    private String processAlternativeNameValue(List<?> alternativeName) {

        if (alternativeName.size() != 2) {
            return null;
        }

        Object value = alternativeName.get(1);
        if (value instanceof byte[]) {
            try {
                X509OtherName otherName = X509OtherName.fromByteArray((byte[]) value);
                if (otherName.isICPBrasil()) {
                    String fieldValue;
                    if (otherName.hasPessoaFisicaData()) {
                        Map<PessoaFisicaField, String> data = otherName.getPessoaFisicaData();
                        fieldValue = data.entrySet().stream().map(e -> {
                            String entryValue = StringUtils.firstNonBlank(e.getValue(), getString("CryptoObjectParser.NotDefined"));
                            return "[" + getString("ICPBrasil.OtherName.PessoaFisica." + e.getKey()) + ": " + entryValue + "]";
                        }).collect(Collectors.joining(", "));

                    } else {
                        fieldValue = StringUtils.firstNonBlank(otherName.getDecodedValue(), getString("CryptoObjectParser.NotDefined"));
                    }
                    String fieldName = getOrDefault("ICPBrasil.OtherName." + otherName.getTypeID(), "");
                    return String.format("(%s) %s: %s", otherName.getTypeID(), fieldName, fieldValue);
                } else {
                    return ASN1Dump.dumpAsString(otherName, true);
                }
            } catch (Exception e) {
                return Hex.toHexString((byte[]) value);
            }
        } else if (value != null) {
            return value.toString();
        }

        return null;
    }

    private void processCRL(X509CRL crl, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, CRLException, IOException {

        metadata.set(META_OBJECT_CLASS, X509CRL.class.getSimpleName());

        X509CRLHolder crlHolder = new X509CRLHolder(crl.getEncoded());
        DateFormat df = createDateFormat();

        metadata.set(META_CRL_PREFIX + "issuer", crlHolder.getIssuer().toString());
        metadata.set(META_CRL_PREFIX + "thisUpdate", DateUtil.dateToString(crlHolder.getThisUpdate()));
        metadata.set(META_CRL_PREFIX + "nextUpdate", DateUtil.dateToString(crlHolder.getNextUpdate()));

        TableHTMLContentHandler table = new TableHTMLContentHandler(xhtml, metadata);

        table.startTable();
        table.addRow(true, getString("CryptoObjectParser.CRL.Issuer"), crlHolder.getIssuer().toString());
        table.addRow(true, getString("CryptoObjectParser.CRL.ThisUpdate"), df.format(crlHolder.getThisUpdate()));
        table.addRow(true, getString("CryptoObjectParser.CRL.NextUpdate"), df.format(crlHolder.getNextUpdate()));
        table.endTable();

        xhtml.endElement("br");

        dumpObjectAsString(crl, xhtml);
    }

    private void processTrustedCertificateBlock(X509TrustedCertificateBlock block, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException {

        metadata.set(META_OBJECT_CLASS, X509TrustedCertificateBlock.class.getSimpleName());

        EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);
        addEmbbeddedObject(block.getCertificateHolder().getEncoded(), "certificate.crt", extractor);
    }

    private void processCSR(PKCS10CertificationRequest csr, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws SAXException {

        metadata.set(META_OBJECT_CLASS, PKCS10CertificationRequest.class.getSimpleName());

        metadata.set(META_CSR_PREFIX + "subject", csr.getSubject().toString());
        metadata.set(META_CSR_PREFIX + "signatureAlgorithm", csr.getSignatureAlgorithm().getAlgorithm().getId());
        metadata.set(META_CSR_PREFIX + "publicKeyAlgorithm", csr.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm().getId());

        TableHTMLContentHandler table = new TableHTMLContentHandler(xhtml, metadata);

        table.startTable();
        table.addRow(true, getString("CryptoObjectParser.CSR.Subject"), csr.getSubject().toString());
        table.addRow(true, getString("CryptoObjectParser.CSR.SignatureAlgorithm"), csr.getSignatureAlgorithm().getAlgorithm().getId());
        table.addRow(true, getString("CryptoObjectParser.CSR.PublicKeyAlgorithm"),
                csr.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm().getId());
        table.endTable();

        dumpObjectAsString(csr.toASN1Structure(), xhtml);
    }

    private void processPrivateKey(PrivateKey key, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws SAXException {

        metadata.set(META_OBJECT_CLASS, PrivateKey.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "algorithm", key.getAlgorithm());
        metadata.set(META_KEY_PREFIX + "format", key.getFormat());

        xhtml.element("h4", key.getAlgorithm() + " (" + key.getFormat() + ")");

        dumpObjectAsString(key, xhtml);
    }

    private void processPublicKey(PublicKey key, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws SAXException {

        metadata.set(META_OBJECT_CLASS, PublicKey.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "algorithm", key.getAlgorithm());
        metadata.set(META_KEY_PREFIX + "format", key.getFormat());

        xhtml.element("h4", key.getAlgorithm() + " (" + key.getFormat() + ")");

        dumpObjectAsString(key, xhtml);
    }

    private void processPEMKeyPair(PEMKeyPair kp, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException, IOException {

        metadata.set(META_OBJECT_CLASS, PEMKeyPair.class.getSimpleName());

        EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);

        if (kp.getPublicKeyInfo() != null) {
            addEmbbeddedObject(kp.getPublicKeyInfo().getEncoded(), "publicKey.key", extractor);
        }

        if (kp.getPrivateKeyInfo() != null) {
            addEmbbeddedObject(kp.getPrivateKeyInfo().getEncoded(), "privateKey.key", extractor);
        }
    }

    private void processSubjectPublicKeyInfo(SubjectPublicKeyInfo publicKey, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException {

        if (publicKey == null) {
            return;
        }

        metadata.set(META_OBJECT_CLASS, SubjectPublicKeyInfo.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "publicKeyAlgorithm", publicKey.getAlgorithm().getAlgorithm().getId());

        dumpObjectAsString(publicKey, xhtml);
    }

    private void processPrivateKeyInfo(PrivateKeyInfo privateKey, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException {

        metadata.set(META_OBJECT_CLASS, PrivateKeyInfo.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "algorithm", privateKey.getPrivateKeyAlgorithm().getAlgorithm().getId());
        metadata.set(META_KEY_PREFIX + "keyLength", Integer.toString(privateKey.getPrivateKeyLength()));

        dumpObjectAsString(privateKey, xhtml);
    }

    private void processEncryptedPrivateKeyInfo(EncryptedPrivateKeyInfo epki, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException {

        metadata.set(META_OBJECT_CLASS, EncryptedPrivateKeyInfo.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "algorithm", epki.getEncryptionAlgorithm().getAlgorithm().getId());

        xhtml.element("p", "The private key is encrypted and requires a password/mechanism for decryption.");

        dumpObjectAsString(epki, xhtml);
    }

    private void processPKCS8EncryptedPrivateKeyInfo(PKCS8EncryptedPrivateKeyInfo ekpi, XHTMLContentHandler xhtml, Metadata metadata,
            ParseContext context) throws SAXException {

        processEncryptedPrivateKeyInfo(ekpi.toASN1Structure(), xhtml, metadata, context);

        metadata.set(META_OBJECT_CLASS, PKCS8EncryptedPrivateKeyInfo.class.getSimpleName());
    }

    private void processPfx(Pfx pfx, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws SAXException, IOException {

        metadata.set(META_OBJECT_CLASS, Pfx.class.getSimpleName());

        metadata.set(META_PKCS12_PREFIX + "authSafe:oid", pfx.getAuthSafe().getContentType().getId());
        if (pfx.getMacData() != null) {
            metadata.set(META_PKCS12_PREFIX + "mac:algorithm", pfx.getMacData().getMac().getAlgorithmId().toString());
        }

        PKCS12PfxPdu pdu = new PKCS12PfxPdu(pfx);
        int index = 0;
        EmbeddedDocumentExtractor extractor = createdEmbeddedDocumentExtractor(context);
        for (org.bouncycastle.asn1.pkcs.ContentInfo ci : pdu.getContentInfos()) {
            String name = String.format("contentInfo-%d.der", index++);
            addEmbbeddedObject(ci.getEncoded(), name, extractor);
        }

        dumpObjectAsString(pfx, xhtml);
    }

    private void processECParameters(X9ECParameters obj, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws SAXException {

        metadata.set(META_OBJECT_CLASS, X9ECParameters.class.getSimpleName());

        dumpObjectAsString(obj, xhtml);
    }

    private void processAttributeCertificateHolder(X509AttributeCertificateHolder obj, Metadata metadata, XHTMLContentHandler xhtml,
            ParseContext context) throws SAXException {

        metadata.set(META_OBJECT_CLASS, X509AttributeCertificateHolder.class.getSimpleName());

        dumpObjectAsString(obj.toASN1Structure(), xhtml);
    }

    private void processASN1ObjectIdentifier(ASN1ObjectIdentifier obj, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException {

        metadata.set(META_OBJECT_CLASS, ASN1ObjectIdentifier.class.getSimpleName());

        metadata.set(META_PREFIX + "oid", obj.getId());

        dumpObjectAsString(obj, xhtml);
    }

    private void processEncryptedKeyPair(PEMEncryptedKeyPair obj, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws SAXException {

        metadata.set(META_OBJECT_CLASS, PEMEncryptedKeyPair.class.getSimpleName());

        metadata.set(META_KEY_PREFIX + "decryptionAlgorithm", obj.getDekAlgName());

        TableHTMLContentHandler table = new TableHTMLContentHandler(xhtml, metadata);

        table.startTable();
        table.addRow(true, getString("CryptoObjectParser.EncryptedKeyPair.DecryptionAlgorithm"), obj.getDekAlgName());
        table.endTable();
    }

    private void dumpObjectAsString(ASN1Encodable obj, XHTMLContentHandler xhtml) throws SAXException {

        String objectString;
        try {
            objectString = ASN1Dump.dumpAsString(obj, true);
        } catch (Exception e) {
            logger.warn("Error encoding ASN1 object to String: " + e);
            objectString = getString("CryptoObjectParser.ErrorEncodingString");
        }
        xhtml.element("pre", objectString);
    }

    private void dumpObjectAsString(Object obj, XHTMLContentHandler xhtml) throws SAXException {

        String objectString;
        try {
            objectString = obj.toString();
        } catch (Exception e) {
            logger.warn("Error encoding object to String: " + e);
            objectString = getString("CryptoObjectParser.ErrorEncodingString");
        }
        xhtml.element("pre", objectString);
    }

}
