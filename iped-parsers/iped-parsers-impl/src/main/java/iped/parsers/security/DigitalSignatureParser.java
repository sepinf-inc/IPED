/*
 * Copyright 2012-2024, IPED Contributors
 *
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.security;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;

/**
 * Parser forense para assinaturas digitais em documentos PDF.
 * Extrai metadados técnicos de assinaturas PAdES/CMS sem realizar validação jurídica.
 * Opera offline por padrão — não acessa OCSP/CRL/TSA remotamente.
 *
 * @author ojaneri
 */
public class DigitalSignatureParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalSignatureParser.class);

    public static final MediaType PDF_TYPE = MediaType.application("pdf");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDF_TYPE);

    // Metadata property names following IPED convention (ExtraProperties.PDF_META_PREFIX = "pdf:")
    public static final String SIG_COUNT = ExtraProperties.PDF_META_PREFIX + "signatureCount";
    public static final String SIG_PREFIX = ExtraProperties.PDF_META_PREFIX + "signature";

    // Per-signature properties (indexed as pdf:signature[0].field)
    public static final String SIGNER_NAME = ".signerName";
    public static final String CONTACT_INFO = ".contactInfo";
    public static final String REASON = ".reason";
    public static final String LOCATION = ".location";
    public static final String SIGNING_TIME = ".signingTime";
    public static final String SIG_ALGORITHM = ".signatureAlgorithm";
    public static final String DIGEST_ALGORITHM = ".digestAlgorithm";
    public static final String CERT_SUBJECT = ".certificateSubject";
    public static final String CERT_ISSUER = ".certificateIssuer";
    public static final String CERT_SERIAL = ".certificateSerial";
    public static final String CERT_VALID_FROM = ".certificateValidFrom";
    public static final String CERT_VALID_TO = ".certificateValidTo";
    public static final String CERT_FINGERPRINT_SHA256 = ".certificateFingerprintSHA256";
    public static final String COVERS_WHOLE_DOCUMENT = ".coversWholeDocument";
    public static final String BYTE_RANGE_START = ".byteRangeStart";
    public static final String BYTE_RANGE_GAP_SIZE = ".byteRangeGapSize";
    public static final String REVISION_NUMBER = ".revisionNumber";
    public static final String MODIFIED_AFTER_SIGNING = ".modifiedAfterSigning";
    public static final String TIMESTAMP_TIME = ".timestampTime";
    public static final String TIMESTAMP_AUTHORITY = ".timestampAuthority";
    public static final String INTEGRITY_STATUS = ".integrityStatus";

    private static final ThreadLocal<SimpleDateFormat> ISO_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            try (PDDocument document = PDDocument.load(tis.getFile())) {
                List<PDSignature> signatures = document.getSignatureDictionaries();
                if (signatures == null || signatures.isEmpty()) {
                    metadata.set(SIG_COUNT, "0");
                    return;
                }

                int sigCount = signatures.size();
                metadata.set(SIG_COUNT, Integer.toString(sigCount));

                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.startElement("div", "class", "digital-signatures");

                long docLength = tis.getFile().length();

                // Sort signatures by byte range start position to determine chronological revision order
                List<PDSignature> sortedSignatures = new ArrayList<>(signatures);
                sortedSignatures.sort(Comparator.comparingInt(sig -> {
                    int[] br = sig.getByteRange();
                    return (br != null && br.length >= 2) ? br[0] : Integer.MAX_VALUE;
                }));

                for (int i = 0; i < sigCount; i++) {
                    PDSignature sig = sortedSignatures.get(i);
                    String prefix = SIG_PREFIX + "[" + i + "]";
                    extractSignatureMetadata(sig, tis.getFile(), docLength, i, prefix, metadata, xhtml);
                }

                xhtml.endElement("div");
                xhtml.endDocument();
            } catch (SAXException e) {
                throw e;
            } catch (Exception e) {
                // Catch all exceptions including IOException from corrupted/malformed PDFs gracefully.
                // A corrupted document should not crash the entire IPED processing pipeline.
                LOGGER.warn("Failed to parse digital signatures from PDF: {}", e.getMessage());
                LOGGER.debug("Digital signature parsing error", e);
                metadata.set(SIG_COUNT, "0");
            }
        } finally {
            tmp.close();
        }
    }

    private void extractSignatureMetadata(PDSignature sig, java.io.File file, long docLength,
            int index, String prefix, Metadata metadata, XHTMLContentHandler xhtml) throws SAXException {

        setIfNotNull(metadata, prefix + SIGNER_NAME, sig.getName());
        setIfNotNull(metadata, prefix + CONTACT_INFO, sig.getContactInfo());
        setIfNotNull(metadata, prefix + REASON, sig.getReason());
        setIfNotNull(metadata, prefix + LOCATION, sig.getLocation());

        Calendar signDate = sig.getSignDate();
        if (signDate != null) {
            metadata.set(prefix + SIGNING_TIME, ISO_FORMAT.get().format(signDate.getTime()));
        }

        int[] byteRange = sig.getByteRange();
        // Require full 4-element byte range for meaningful forensic analysis
        if (byteRange != null && byteRange.length >= 4) {
            metadata.set(prefix + BYTE_RANGE_START, Integer.toString(byteRange[0]));
            // Gap size = offset2 - (offset1 + length1), represents the signature content hole
            long gapSize = (long) byteRange[2] - ((long) byteRange[0] + byteRange[1]);
            metadata.set(prefix + BYTE_RANGE_GAP_SIZE, Long.toString(gapSize));

            boolean coversWhole = coversWholeDocument(byteRange, docLength);
            metadata.set(prefix + COVERS_WHOLE_DOCUMENT, Boolean.toString(coversWhole));

            boolean modifiedAfter = isModifiedAfterSigning(byteRange, docLength);
            metadata.set(prefix + MODIFIED_AFTER_SIGNING, Boolean.toString(modifiedAfter));
        } else {
            // Explicitly set coverage flags to false when byte range is incomplete
            metadata.set(prefix + COVERS_WHOLE_DOCUMENT, "false");
            metadata.set(prefix + MODIFIED_AFTER_SIGNING, "false");
        }

        String subFilter = sig.getSubFilter();
        setIfNotNull(metadata, prefix + SIG_ALGORITHM, subFilter);

        try (InputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] contents = sig.getContents(fileStream);
            if (contents != null && contents.length > 0) {
                extractCMSMetadata(contents, prefix, metadata, xhtml);
            } else {
                metadata.set(prefix + INTEGRITY_STATUS, "UNKNOWN");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to extract signature contents for signature {}: {}", index, e.getMessage());
            metadata.set(prefix + INTEGRITY_STATUS, "UNKNOWN");
        }

        // Revision number based on sorted position (index after sorting by byte range start)
        // ponytail: PDFBox 2.0.27 does not expose revision number directly;
        // we use sorted position as best available approximation.
        // Path out: upgrade to PDFBox 3.x which has PDDocument.getRevisionAndIncrementalUpdateInfo().
        metadata.set(prefix + REVISION_NUMBER, Integer.toString(index + 1));
    }

    private void extractCMSMetadata(byte[] contents, String prefix, Metadata metadata,
            XHTMLContentHandler xhtml) throws SAXException {
        try {
            CMSSignedData signedData = new CMSSignedData(contents);
            SignerInformationStore signerStore = signedData.getSignerInfos();

            // Handle multiple SignerInformation objects (countersignatures) by indexing them
            int signerIndex = 0;
            for (SignerInformation signerInfo : signerStore.getSigners()) {
                String signerPrefix = (signerStore.size() > 1)
                        ? prefix + ".signer[" + signerIndex + "]"
                        : prefix;

                String digestAlgOID = signerInfo.getDigestAlgOID();
                setIfNotNull(metadata, signerPrefix + DIGEST_ALGORITHM, digestAlgOID);

                String encryptionAlgOID = signerInfo.getEncryptionAlgOID();
                setIfNotNull(metadata, signerPrefix + SIG_ALGORITHM, encryptionAlgOID);

                Store<X509CertificateHolder> certStore = signedData.getCertificates();
                Collection<X509CertificateHolder> certCollection = certStore.getMatches(signerInfo.getSID());

                // Use the first matching certificate for both metadata and verification
                X509Certificate matchedCert = null;
                for (X509CertificateHolder certHolder : certCollection) {
                    X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);

                    // Only populate top-level cert metadata from the first matching cert
                    if (matchedCert == null) {
                        matchedCert = cert;
                        metadata.set(signerPrefix + CERT_SUBJECT, cert.getSubjectX500Principal().getName());
                        metadata.set(signerPrefix + CERT_ISSUER, cert.getIssuerX500Principal().getName());
                        metadata.set(signerPrefix + CERT_SERIAL, cert.getSerialNumber().toString());
                        metadata.set(signerPrefix + CERT_VALID_FROM, ISO_FORMAT.get().format(cert.getNotBefore()));
                        metadata.set(signerPrefix + CERT_VALID_TO, ISO_FORMAT.get().format(cert.getNotAfter()));

                        try {
                            MessageDigest md = MessageDigest.getInstance("SHA-256");
                            byte[] fingerprint = md.digest(cert.getEncoded());
                            metadata.set(signerPrefix + CERT_FINGERPRINT_SHA256, bytesToHex(fingerprint));
                        } catch (Exception e) {
                            LOGGER.debug("Failed to compute certificate fingerprint", e);
                        }
                    }

                    renderCertificateInfo(xhtml, cert);
                }

                // Verify signature against the first matched certificate only
                // ponytail: Full chain validation requires trust store configuration;
                // we only verify the signature against the embedded certificate.
                // Path out: add configurable trust store and CRL/OCSP checking in future PR.
                try {
                    if (matchedCert != null) {
                        boolean valid = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().build(matchedCert));
                        metadata.set(signerPrefix + INTEGRITY_STATUS, valid ? "VALID" : "INVALID");
                    } else {
                        metadata.set(signerPrefix + INTEGRITY_STATUS, "UNKNOWN");
                    }
                } catch (Exception e) {
                    LOGGER.debug("Signature verification failed for {}", signerPrefix, e);
                    metadata.set(signerPrefix + INTEGRITY_STATUS, "UNKNOWN");
                }

                signerIndex++;
            }

            // ponytail: RFC 3161 timestamp token parsing from CMS unsigned attributes
            // is complex; deferring full implementation to next iteration.
            // Path out: parse SignerInfo.getUnsignedAttributes() for id-smime-aa-timeStampToken.

        } catch (Exception e) {
            LOGGER.warn("Failed to parse CMS signed data: {}", e.getMessage());
            LOGGER.debug("CMS parsing error", e);
            metadata.set(prefix + INTEGRITY_STATUS, "UNKNOWN");
        }
    }

    private boolean coversWholeDocument(int[] byteRange, long docLength) {
        if (byteRange == null || byteRange.length < 4) {
            return false;
        }
        return byteRange[0] == 0 && ((long) byteRange[2] + byteRange[3]) >= docLength;
    }

    private boolean isModifiedAfterSigning(int[] byteRange, long docLength) {
        if (byteRange == null || byteRange.length < 4) {
            return false;
        }
        long endOfSignedContent = (long) byteRange[2] + byteRange[3];
        return endOfSignedContent < docLength;
    }

    private void setIfNotNull(Metadata metadata, String key, String value) {
        if (value != null && !value.isEmpty()) {
            metadata.set(key, value);
        }
    }

    private void renderCertificateInfo(XHTMLContentHandler xhtml, X509Certificate cert) throws SAXException {
        xhtml.startElement("div", "class", "signature-certificate");
        xhtml.startElement("p");
        xhtml.characters("Certificate Subject: " + cert.getSubjectX500Principal().getName());
        xhtml.endElement("p");
        xhtml.startElement("p");
        xhtml.characters("Certificate Issuer: " + cert.getIssuerX500Principal().getName());
        xhtml.endElement("p");
        xhtml.startElement("p");
        xhtml.characters("Serial: " + cert.getSerialNumber().toString());
        xhtml.endElement("p");
        xhtml.startElement("p");
        xhtml.characters("Valid From: " + cert.getNotBefore().toString());
        xhtml.endElement("p");
        xhtml.startElement("p");
        xhtml.characters("Valid To: " + cert.getNotAfter().toString());
        xhtml.endElement("p");
        xhtml.endElement("div");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}