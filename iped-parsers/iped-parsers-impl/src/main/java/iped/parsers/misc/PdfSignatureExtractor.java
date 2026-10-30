package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenInfo;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.parsers.security.SignatureMetadata;
import iped.parsers.standard.StandardParser;
import iped.utils.DateUtil;

/**
 * Extracts technical information about the digital signatures embedded in a
 * PDF: signature dictionary fields, signer certificate, /ByteRange coverage and
 * a cryptographic status.
 *
 * The status only checks the signature against the signer certificate found in
 * the CMS (message digest over the /ByteRange bytes and signature over the
 * signed attributes). Certificate chain, revocation and timestamp authority are
 * not validated, and nothing is fetched from the network. A valid status does
 * not imply legal validity.
 *
 * RFC 3161 timestamps are read without validating the timestamp authority: for
 * a signature timestamp, only its message imprint is compared with the
 * signature value. A document timestamp (SubFilter ETSI.RFC3161) gets the same
 * kind of status as a signature, with the token as the CMS and the authority
 * certificate in the token as the signer certificate.
 */
public class PdfSignatureExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfSignatureExtractor.class);

    public static final MediaType PKCS7_SIGNATURE = MediaType.application("pkcs7-signature");

    public enum Status {
        VALID, INVALID, UNKNOWN;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public static class SignatureInfo {

        /** position in document order, starting at 0 */
        public int index;
        public String fieldName;
        public String name;
        public String reason;
        public String location;
        public String contactInfo;
        public String filter;
        public String subFilter;
        /** signing time declared in the signature dictionary (/M) */
        public Calendar signDate;
        /** signing time declared in the CMS signingTime attribute, if present */
        public Date cmsSigningTime;
        public int[] byteRange;
        /** /ByteRange is well formed and lies inside the file */
        public boolean byteRangeValid;
        /** the unsigned gap of /ByteRange holds only the /Contents hex string */
        public boolean gapIsContentsOnly;
        /** signed bytes reach the end of the file and the gap holds only /Contents */
        public boolean coversWholeDocument;
        public Status status = Status.UNKNOWN;
        /** short reason for an invalid or unknown status */
        public String statusDetail;
        public X509CertificateHolder signerCertificate;
        public String digestAlgorithmOid;
        public String signatureAlgorithmOid;
        /** raw CMS bytes from /Contents, including any zero padding */
        public byte[] contents;

        /**
         * a document timestamp (SubFilter ETSI.RFC3161): the value is an RFC 3161
         * token over the /ByteRange bytes, signed by a timestamp authority, not by a
         * person
         */
        public boolean documentTimestamp;
        /** genTime of the RFC 3161 token: the signature timestamp, or the document timestamp itself */
        public Date timestampGenTime;
        /**
         * whether the token's message imprint matches what it should cover: the
         * signature value for a signature timestamp, the /ByteRange bytes for a
         * document timestamp. Null without a token.
         */
        public Boolean timestampImprintMatches;
        public String timestampAuthority;
        public String timestampDigestAlgorithmOid;
        /** message imprint declared in the token */
        public byte[] timestampImprint;
    }

    private static final String SUBFILTER_DOC_TIMESTAMP = "ETSI.RFC3161";
    private static final String DETAIL_DIGEST_MISMATCH = "message digest does not match /ByteRange content";

    /**
     * Returns the signatures of the document, ordered by how much of the file each
     * one covers (the first signed revision first). Signature fields without a
     * signature value are ignored. Errors in one signature are recorded in its
     * status and do not prevent the others from being extracted.
     */
    public List<SignatureInfo> extract(PDDocument document, File pdfFile) throws IOException {
        List<SignatureInfo> result = new ArrayList<>();
        long fileLength = pdfFile.length();

        // getAcroForm(null): read the form as stored, without PDFBox's default fixup
        // that may rebuild fields and appearances (Tika does the same)
        PDAcroForm form = document.getDocumentCatalog().getAcroForm(null);
        List<PDSignatureField> fields = new ArrayList<>();
        if (form != null) {
            // a malformed field elsewhere in the form must not hide the signatures read so far
            try {
                for (PDField f : form.getFieldTree()) {
                    if (f instanceof PDSignatureField) {
                        fields.add((PDSignatureField) f);
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Error reading the form fields of a signed PDF, {} signature fields read: {}",
                        fields.size(), e.toString());
            }
        }
        // two fields may share one signature dictionary: it is a single signature
        Set<COSDictionary> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PDSignatureField field : fields) {
            PDSignature signature;
            try {
                signature = field.getSignature();
            } catch (RuntimeException e) {
                LOGGER.debug("Error reading signature dictionary", e);
                continue;
            }
            if (signature == null || !seen.add(signature.getCOSObject())) {
                continue;
            }
            SignatureInfo info = new SignatureInfo();
            try {
                info.fieldName = field.getFullyQualifiedName();
            } catch (RuntimeException e) {
                LOGGER.debug("Error reading signature field name", e);
            }
            try {
                extractSignature(signature, pdfFile, fileLength, info);
                // a placeholder reserved for a signature that was never filled holds
                // only zero padding: there is no signature value
                if (info.contents == null || cmsBytes(info).length == 0) {
                    continue;
                }
            } catch (Exception | StackOverflowError e) {
                // StackOverflowError: BouncyCastle parses deeply nested ASN.1 recursively
                info.status = Status.UNKNOWN;
                info.statusDetail = "extraction error: " + e;
                LOGGER.debug("Error extracting PDF signature", e);
            }
            result.add(info);
        }

        result.sort(Comparator.comparingLong(PdfSignatureExtractor::signedEnd));
        for (int i = 0; i < result.size(); i++) {
            result.get(i).index = i;
        }
        return result;
    }

    /**
     * Writes the signature fields of {@link SignatureMetadata} for the given
     * signatures.
     */
    public static void addMetadata(List<SignatureInfo> signatures, Metadata metadata) {
        metadata.set(SignatureMetadata.COUNT, Integer.toString(signatures.size()));
        for (SignatureInfo info : signatures) {
            addSignatureFields(info, metadata);
        }
    }

    private static void addSignatureFields(SignatureInfo info, Metadata metadata) {
        metadata.add(SignatureMetadata.TYPE,
                info.documentTimestamp ? SignatureMetadata.TYPE_DOCUMENT_TIMESTAMP : SignatureMetadata.TYPE_SIGNATURE);
        metadata.add(SignatureMetadata.STATUS, info.status.toString());
        if (info.signDate != null) {
            metadata.add(SignatureMetadata.DATE, DateUtil.dateToString(info.signDate.getTime()));
            metadata.add(SignatureMetadata.DATE_SOURCE, SignatureMetadata.DATE_SOURCE_M);
        } else if (info.cmsSigningTime != null) {
            metadata.add(SignatureMetadata.DATE, DateUtil.dateToString(info.cmsSigningTime));
            metadata.add(SignatureMetadata.DATE_SOURCE, SignatureMetadata.DATE_SOURCE_CMS);
        }
        metadata.add(SignatureMetadata.COVERS_WHOLE_DOCUMENT, Boolean.toString(info.coversWholeDocument));
        if (Boolean.TRUE.equals(info.timestampImprintMatches)) {
            metadata.add(SignatureMetadata.TIMESTAMP_DATE, DateUtil.dateToString(info.timestampGenTime));
            SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.TIMESTAMP_AUTHORITY, info.timestampAuthority);
        }
        // the certificate of a document timestamp belongs to the timestamp authority,
        // not to a signer of the document
        if (info.signerCertificate != null && !info.documentTimestamp) {
            SignatureMetadata.addSigner(metadata, info.signerCertificate);
        }
    }

    /**
     * Metadata of the subitem holding one signature: the same fields as the PDF
     * item, for this signature only, plus the details of its signature
     * dictionary and CMS.
     */
    public static Metadata subitemMetadata(SignatureInfo info) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, subitemName(info));
        metadata.set(Metadata.CONTENT_TYPE, PKCS7_SIGNATURE.toString());
        metadata.set(StandardParser.INDEXER_CONTENT_TYPE, PKCS7_SIGNATURE.toString());
        metadata.set(SignatureMetadata.COUNT, "1");
        addSignatureFields(info, metadata);

        metadata.set(SignatureMetadata.INDEX, Integer.toString(info.index));
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.FIELD_NAME, info.fieldName);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.DECLARED_NAME, info.name);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.REASON, info.reason);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.LOCATION, info.location);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.CONTACT_INFO, info.contactInfo);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.FILTER, info.filter);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.SUB_FILTER, info.subFilter);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.STATUS_DETAIL, info.statusDetail);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.DIGEST_ALGORITHM, info.digestAlgorithmOid);
        SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.SIGNATURE_ALGORITHM, info.signatureAlgorithmOid);
        if (info.byteRange != null) {
            metadata.set(SignatureMetadata.BYTE_RANGE, Arrays.toString(info.byteRange));
        }
        if (info.cmsSigningTime != null) {
            metadata.set(SignatureMetadata.CMS_SIGNING_TIME, DateUtil.dateToString(info.cmsSigningTime));
        }
        // for a document timestamp the certificate is the TSA's, named in timestampAuthority
        if (info.signerCertificate != null && !info.documentTimestamp) {
            SignatureMetadata.addCertificateDetails(metadata, info.signerCertificate);
        }
        if (info.timestampImprintMatches != null) {
            metadata.set(SignatureMetadata.TIMESTAMP_IMPRINT_MATCHES, info.timestampImprintMatches.toString());
            SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.TIMESTAMP_DIGEST_ALGORITHM,
                    info.timestampDigestAlgorithmOid);
            if (info.timestampImprint != null) {
                metadata.set(SignatureMetadata.TIMESTAMP_IMPRINT, Hex.toHexString(info.timestampImprint));
            }
            SignatureMetadata.addIfNotEmpty(metadata, SignatureMetadata.TIMESTAMP_AUTHORITY, info.timestampAuthority);
            // on the subitem the token time is kept even when the imprint does not
            // match, next to the flag that says so
            if (!info.timestampImprintMatches && info.timestampGenTime != null) {
                metadata.set(SignatureMetadata.TIMESTAMP_DATE, DateUtil.dateToString(info.timestampGenTime));
            }
        }
        return metadata;
    }

    static String subitemName(SignatureInfo info) {
        String base = info.fieldName != null && !info.fieldName.trim().isEmpty() ? info.fieldName.trim()
                : "Signature" + (info.index + 1);
        return base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") + ".p7s";
    }

    /**
     * The CMS bytes of a signature without the zero padding of the reserved
     * /Contents space, so the file can be checked with external tools.
     */
    public static byte[] cmsBytes(SignatureInfo info) {
        byte[] contents = info.contents;
        int length = encodedLength(contents);
        if (length <= 0) {
            // not a parsable ASN.1 object: only strip the trailing zeros
            length = contents.length;
            while (length > 0 && contents[length - 1] == 0) {
                length--;
            }
        }
        return Arrays.copyOf(contents, length);
    }

    /**
     * Length of the ASN.1 object at the start of the array, for definite (DER)
     * and indefinite (BER) length encodings, or -1 when it cannot be parsed.
     * BER objects end with 00 00 markers, so the length cannot be found by
     * stripping zeros.
     */
    static int encodedLength(byte[] data) {
        if (data == null || data.length < 2) {
            return -1;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        try (ASN1InputStream asn1 = new ASN1InputStream(in, data.length)) {
            if (asn1.readObject() == null) {
                return -1;
            }
            return data.length - in.available();
        } catch (IOException | RuntimeException | StackOverflowError e) {
            return -1;
        }
    }

    private static final byte[] BYTE_RANGE_KEY = "/ByteRange".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * Cheap check used when Tika did not flag the document: a signature value
     * always has a /ByteRange entry. Compressed object streams can hide it, so a
     * false result is not proof of absence.
     */
    public static boolean mayContainSignature(File pdfFile) throws IOException {
        try (InputStream in = new FileInputStream(pdfFile)) {
            return mayContainSignature(in);
        }
    }

    /** Same as {@link #mayContainSignature(File)}, reading the stream to its end at most. */
    public static boolean mayContainSignature(InputStream in) throws IOException {
        byte[] buf = new byte[65536];
        // the key starts with its only '/', so a mismatch restarts the match at
        // this byte, and a partial match carries over to the next chunk
        int matched = 0;
        int read;
        while ((read = in.read(buf)) > 0) {
            for (int i = 0; i < read; i++) {
                if (buf[i] == BYTE_RANGE_KEY[matched]) {
                    if (++matched == BYTE_RANGE_KEY.length) {
                        return true;
                    }
                } else {
                    matched = buf[i] == BYTE_RANGE_KEY[0] ? 1 : 0;
                }
            }
        }
        return false;
    }

    private static long signedEnd(SignatureInfo info) {
        return info.byteRangeValid ? (long) info.byteRange[2] + info.byteRange[3] : Long.MAX_VALUE;
    }

    private void extractSignature(PDSignature signature, File pdfFile, long fileLength, SignatureInfo info)
            throws IOException {
        info.name = signature.getName();
        info.reason = signature.getReason();
        info.location = signature.getLocation();
        info.contactInfo = signature.getContactInfo();
        info.filter = signature.getFilter();
        info.subFilter = signature.getSubFilter();
        info.documentTimestamp = SUBFILTER_DOC_TIMESTAMP.equals(info.subFilter);
        info.signDate = signature.getSignDate();
        info.byteRange = signature.getByteRange();
        info.byteRangeValid = isByteRangeValid(info.byteRange, fileLength);

        if (info.byteRangeValid) {
            info.gapIsContentsOnly = isGapContentsOnly(pdfFile, info.byteRange);
            long signedEnd = (long) info.byteRange[2] + info.byteRange[3];
            info.coversWholeDocument = info.gapIsContentsOnly && info.byteRange[0] == 0 && signedEnd == fileLength;
        }

        // read /Contents from the dictionary, not from the /ByteRange gap: PDFBox fails
        // to parse the gap when it is wider than the /Contents string
        info.contents = signature.getContents();
        if (info.contents == null || info.contents.length == 0) {
            info.statusDetail = "empty /Contents";
            return;
        }
        if (!info.byteRangeValid) {
            info.statusDetail = "invalid /ByteRange";
            return;
        }
        verify(pdfFile, info);
    }

    /**
     * /ByteRange must be [offset1 length1 offset2 length2], non negative, with the
     * second range after the first one and inside the file.
     */
    static boolean isByteRangeValid(int[] br, long fileLength) {
        if (br == null || br.length != 4) {
            return false;
        }
        for (int value : br) {
            if (value < 0) {
                return false;
            }
        }
        long end1 = (long) br[0] + br[1];
        long end2 = (long) br[2] + br[3];
        return end1 <= br[2] && end2 <= fileLength;
    }

    private static boolean isPdfWhitespace(int b) {
        return b == 0 || b == '\t' || b == '\n' || b == '\f' || b == '\r' || b == ' ';
    }

    /**
     * Checks that the bytes between the two signed ranges are only the /Contents
     * hex string: '<', hex digits, '>'. Anything else there is unsigned data.
     */
    static boolean isGapContentsOnly(File pdfFile, int[] br) throws IOException {
        long gapStart = (long) br[0] + br[1];
        long gapLength = br[2] - gapStart;
        if (gapLength < 2) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(pdfFile, "r")) {
            raf.seek(gapStart);
            byte[] buf = new byte[8192];
            long remaining = gapLength;
            long pos = 0;
            while (remaining > 0) {
                int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read < 0) {
                    return false;
                }
                for (int i = 0; i < read; i++, pos++) {
                    int b = buf[i];
                    if (pos == 0) {
                        if (b != '<') {
                            return false;
                        }
                    } else if (pos == gapLength - 1) {
                        if (b != '>') {
                            return false;
                        }
                    } else if (Character.digit(b, 16) < 0 && !isPdfWhitespace(b)) {
                        // whitespace inside a hex string is ignored (ISO 32000-1, 7.3.4.3)
                        return false;
                    }
                }
                remaining -= read;
            }
        }
        return true;
    }

    private void verify(File pdfFile, SignatureInfo info) {
        if (info.documentTimestamp) {
            verifyDocumentTimestamp(pdfFile, info);
            return;
        }
        ByteRangeContent signedContent = new ByteRangeContent(pdfFile, info.byteRange);
        // Usually the CMS is detached and signs the /ByteRange bytes. Some signers
        // (adbe.pkcs7.sha1, and older tools whatever the SubFilter) encapsulate a digest
        // of the /ByteRange bytes and sign that value instead: the CMS content decides.
        byte[] encapsulated;
        CMSSignedData cms;
        try {
            cms = new CMSSignedData(info.contents);
            if (cms.getSignedContent() != null && !(cms.getSignedContent().getContent() instanceof byte[])) {
                // not an OCTET STRING: neither a digest nor the /ByteRange bytes
                info.statusDetail = "unsupported encapsulated content";
                return;
            }
            encapsulated = encapsulatedContent(cms);
            if (encapsulated == null) {
                cms = new CMSSignedData(signedContent, info.contents);
            }
        } catch (CMSException | RuntimeException e) {
            info.statusDetail = "unparsable CMS";
            LOGGER.debug("Error parsing CMS of PDF signature", e);
            return;
        }

        Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
        if (signers.isEmpty()) {
            info.statusDetail = "no SignerInfo";
            return;
        }

        // a PDF signature normally has a single SignerInfo; if there are more, the
        // status is valid only when all of them verify
        Status combined = Status.VALID;
        String combinedDetail = null;
        for (SignerInformation signer : signers) {
            Status status = verifySigner(cms, signer, encapsulated != null, info);
            if (status == Status.INVALID || (status == Status.UNKNOWN && combined == Status.VALID)) {
                combined = status;
                combinedDetail = info.statusDetail;
            }
        }
        // the reason of the worst signer, not of the last one verified
        info.statusDetail = combinedDetail;
        if (encapsulated != null && combined != Status.INVALID) {
            Status digestStatus = checkEncapsulatedDigest(encapsulated, signedContent, info);
            if (digestStatus == Status.INVALID || (digestStatus == Status.UNKNOWN && combined == Status.VALID)) {
                combined = digestStatus;
            } else {
                // keep the reason of the signer, which is not better known
                info.statusDetail = combinedDetail;
            }
        }
        info.status = combined;
        if (combined == Status.VALID) {
            info.statusDetail = null;
        }
    }

    /**
     * Digest algorithms by digest length, for CMS that encapsulate a digest of the
     * /ByteRange bytes. The CMS does not say which algorithm made that digest, so
     * every known algorithm of the same length is tried.
     */
    private static final Map<Integer, List<String>> DIGESTS_BY_LENGTH = new HashMap<>();
    static {
        DIGESTS_BY_LENGTH.put(20, Arrays.asList(OIWObjectIdentifiers.idSHA1.getId(),
                TeleTrusTObjectIdentifiers.ripemd160.getId()));
        DIGESTS_BY_LENGTH.put(28, Arrays.asList(NISTObjectIdentifiers.id_sha224.getId(),
                NISTObjectIdentifiers.id_sha3_224.getId(), NISTObjectIdentifiers.id_sha512_224.getId()));
        DIGESTS_BY_LENGTH.put(32, Arrays.asList(NISTObjectIdentifiers.id_sha256.getId(),
                NISTObjectIdentifiers.id_sha3_256.getId(), NISTObjectIdentifiers.id_sha512_256.getId()));
        DIGESTS_BY_LENGTH.put(48, Arrays.asList(NISTObjectIdentifiers.id_sha384.getId(),
                NISTObjectIdentifiers.id_sha3_384.getId()));
        DIGESTS_BY_LENGTH.put(64, Arrays.asList(NISTObjectIdentifiers.id_sha512.getId(),
                NISTObjectIdentifiers.id_sha3_512.getId()));
    }

    /**
     * Valid if the encapsulated content is the digest of the /ByteRange bytes by
     * some algorithm of its length. Invalid only if every such algorithm could be
     * computed and none matches; unknown otherwise, never a false invalid.
     */
    private static Status checkEncapsulatedDigest(byte[] encapsulated, ByteRangeContent signedContent,
            SignatureInfo info) {
        List<String> algorithms = DIGESTS_BY_LENGTH.get(encapsulated.length);
        if (algorithms == null) {
            info.statusDetail = "encapsulated content is not a known digest";
            return Status.UNKNOWN;
        }
        boolean allComputed = true;
        for (String algorithm : algorithms) {
            try {
                if (Arrays.equals(encapsulated, digest(algorithm, signedContent::write))) {
                    return Status.VALID;
                }
            } catch (Exception e) {
                allComputed = false;
                LOGGER.debug("Error computing /ByteRange digest with " + algorithm, e);
            }
        }
        if (allComputed) {
            info.statusDetail = DETAIL_DIGEST_MISMATCH;
            return Status.INVALID;
        }
        info.statusDetail = "encapsulated digest algorithm not available";
        return Status.UNKNOWN;
    }

    /** the encapsulated content of the CMS, or null for a detached signature */
    private static byte[] encapsulatedContent(CMSSignedData cms) {
        Object content = cms.getSignedContent() != null ? cms.getSignedContent().getContent() : null;
        return content instanceof byte[] && ((byte[]) content).length > 0 ? (byte[]) content : null;
    }

    @SuppressWarnings("unchecked")
    private Status verifySigner(CMSSignedData cms, SignerInformation signer, boolean encapsulated,
            SignatureInfo info) {
        // the first signer is the one reported: algorithms, signing time, timestamp and
        // certificate all come from it
        boolean reported = info.digestAlgorithmOid == null;
        if (reported) {
            info.digestAlgorithmOid = signer.getDigestAlgOID();
            info.signatureAlgorithmOid = signer.getEncryptionAlgOID();
            info.cmsSigningTime = getSigningTime(signer);
            readSignatureTimestamp(signer, info);
        }

        Collection<X509CertificateHolder> holders = cms.getCertificates().getMatches(signer.getSID());
        if (holders.isEmpty()) {
            info.statusDetail = "signer certificate not found in CMS";
            return Status.UNKNOWN;
        }

        X509CertificateHolder firstCert = null;
        Status result = Status.UNKNOWN;
        for (X509CertificateHolder cert : holders) {
            PublicKey key;
            try {
                key = publicKey(cert);
            } catch (Exception e) {
                LOGGER.debug("Error reading signer certificate public key", e);
                continue;
            }
            if (firstCert == null) {
                firstCert = cert;
            }
            try {
                // verifier built from the public key only: building it from the certificate
                // makes BouncyCastle also reject certificates not valid at the CMS signing
                // time, which is a different question from the signature check
                if (signer.verify(verifier(key))) {
                    if (reported) {
                        info.signerCertificate = cert;
                    }
                    return Status.VALID;
                }
                info.statusDetail = "signature value does not match";
                result = Status.INVALID;
            } catch (CMSSignerDigestMismatchException e) {
                // the signed content changed: no certificate can make this valid
                if (reported) {
                    info.signerCertificate = cert;
                }
                // with encapsulated content, the CMS is inconsistent in itself; whether the
                // document bytes changed is a separate check
                info.statusDetail = encapsulated ? "message digest does not match encapsulated content"
                        : DETAIL_DIGEST_MISMATCH;
                return Status.INVALID;
            } catch (Exception e) {
                LOGGER.debug("Error verifying PDF signature", e);
                if (result != Status.INVALID) {
                    info.statusDetail = "verification error: " + e.getClass().getSimpleName();
                }
            }
        }
        if (firstCert == null) {
            info.statusDetail = "signer certificate unreadable";
            return Status.UNKNOWN;
        }
        if (reported && info.signerCertificate == null) {
            info.signerCertificate = firstCert;
        }
        return result;
    }

    /**
     * Reads the RFC 3161 signature timestamp of a signer, if any. The timestamp
     * authority's own signature and certificate are not validated: only the
     * message imprint is compared with the digest of the signature value.
     */
    private static void readSignatureTimestamp(SignerInformation signer, SignatureInfo info) {
        // a malformed unsigned attribute only loses the timestamp, not the other fields
        try {
            AttributeTable unsigned = signer.getUnsignedAttributes();
            if (unsigned == null) {
                return;
            }
            Attribute attribute = unsigned.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            if (attribute == null || attribute.getAttrValues().size() == 0) {
                return;
            }
            TimeStampToken token = new TimeStampToken(ContentInfo.getInstance(attribute.getAttrValues().getObjectAt(0)));
            readTokenInfo(token, info);
            byte[] digest = digest(info.timestampDigestAlgorithmOid, out -> out.write(signer.getSignature()));
            info.timestampImprintMatches = Arrays.equals(digest, token.getTimeStampInfo().getMessageImprintDigest());
        } catch (Exception e) {
            LOGGER.debug("Error reading signature timestamp", e);
        }
    }

    /**
     * A document timestamp is valid when its message imprint matches the
     * /ByteRange bytes and the token signature verifies against the timestamp
     * authority certificate found in the token. Chain and revocation of that
     * certificate are not checked.
     */
    @SuppressWarnings("unchecked")
    private void verifyDocumentTimestamp(File pdfFile, SignatureInfo info) {
        TimeStampToken token;
        try {
            token = new TimeStampToken(new CMSSignedData(info.contents));
        } catch (Exception e) {
            info.statusDetail = "unparsable timestamp token";
            LOGGER.debug("Error parsing document timestamp", e);
            return;
        }
        readTokenInfo(token, info);
        SignerInformation signer = token.toCMSSignedData().getSignerInfos().getSigners().iterator().next();
        // digest of the token's own SignerInfo; the imprint algorithm is in timestampDigestAlgorithmOid
        info.digestAlgorithmOid = signer.getDigestAlgOID();
        info.signatureAlgorithmOid = signer.getEncryptionAlgOID();

        Collection<X509CertificateHolder> holders = token.getCertificates().getMatches(token.getSID());
        try {
            // the document bytes first: a changed document is invalid whatever the certificate
            ByteRangeContent signedContent = new ByteRangeContent(pdfFile, info.byteRange);
            byte[] digest = digest(info.timestampDigestAlgorithmOid, signedContent::write);
            info.timestampImprintMatches = Arrays.equals(digest, token.getTimeStampInfo().getMessageImprintDigest());
            if (!info.timestampImprintMatches) {
                info.status = Status.INVALID;
                info.statusDetail = "timestamp imprint does not match /ByteRange content";
                return;
            }
            if (holders.isEmpty()) {
                info.statusDetail = "timestamp authority certificate not found in token";
                return;
            }
            X509CertificateHolder tsaCert = holders.iterator().next();
            PublicKey key;
            try {
                key = publicKey(tsaCert);
            } catch (IOException e) {
                info.statusDetail = "timestamp authority certificate unreadable";
                LOGGER.debug("Error reading timestamp authority public key", e);
                return;
            }
            info.signerCertificate = tsaCert;
            if (signer.verify(verifier(key))) {
                info.status = Status.VALID;
            } else {
                info.status = Status.INVALID;
                info.statusDetail = "signature value does not match";
            }
        } catch (CMSSignerDigestMismatchException e) {
            info.status = Status.INVALID;
            info.statusDetail = "timestamp token content was changed";
        } catch (Exception e) {
            info.statusDetail = "verification error: " + e.getClass().getSimpleName();
            LOGGER.debug("Error verifying document timestamp", e);
        }
    }

    private static void readTokenInfo(TimeStampToken token, SignatureInfo info) {
        TimeStampTokenInfo tstInfo = token.getTimeStampInfo();
        info.timestampGenTime = tstInfo.getGenTime();
        info.timestampDigestAlgorithmOid = tstInfo.getMessageImprintAlgOID().getId();
        info.timestampImprint = tstInfo.getMessageImprintDigest();
        GeneralName tsa = tstInfo.getTsa();
        if (tsa != null) {
            info.timestampAuthority = tsa.getTagNo() == GeneralName.directoryName
                    ? X500Name.getInstance(tsa.getName()).toString()
                    : tsa.getName().toString();
        } else {
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> holders = token.getCertificates().getMatches(token.getSID());
            if (!holders.isEmpty()) {
                info.timestampAuthority = holders.iterator().next().getSubject().toString();
            }
        }
    }

    private interface ContentWriter {
        void write(OutputStream out) throws IOException;
    }

    private static final Provider BC_PROVIDER = new BouncyCastleProvider();

    /** with the BouncyCastle provider, whether or not the JVM registered it */
    private static SignerInformationVerifier verifier(PublicKey key) throws OperatorCreationException {
        return new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC_PROVIDER).build(key);
    }

    private static PublicKey publicKey(X509CertificateHolder cert) throws IOException {
        PublicKey key = BouncyCastleProvider.getPublicKey(cert.getSubjectPublicKeyInfo());
        if (key == null) {
            throw new IOException("unsupported public key algorithm");
        }
        return key;
    }

    private static byte[] digest(String algorithmOid, ContentWriter content) throws Exception {
        // BouncyCastle provider: the JDK lacks some digests a PDF signer may use (RIPEMD-160)
        DigestCalculator calculator = new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build()
                .get(new AlgorithmIdentifier(new ASN1ObjectIdentifier(algorithmOid)));
        try (OutputStream out = calculator.getOutputStream()) {
            content.write(out);
        }
        return calculator.getDigest();
    }

    private static Date getSigningTime(SignerInformation signer) {
        try {
            AttributeTable attributes = signer.getSignedAttributes();
            if (attributes == null) {
                return null;
            }
            Attribute attribute = attributes.get(CMSAttributes.signingTime);
            if (attribute == null || attribute.getAttrValues().size() == 0) {
                return null;
            }
            return Time.getInstance(attribute.getAttrValues().getObjectAt(0)).getDate();
        } catch (Exception e) {
            LOGGER.debug("Error reading CMS signing time", e);
            return null;
        }
    }

    /**
     * The signed bytes of a PDF signature, streamed from the file so the whole
     * document is not loaded in memory.
     */
    private static class ByteRangeContent implements CMSTypedData {

        private final File file;
        private final int[] byteRange;

        ByteRangeContent(File file, int[] byteRange) {
            this.file = file;
            this.byteRange = byteRange;
        }

        @Override
        public ASN1ObjectIdentifier getContentType() {
            return CMSObjectIdentifiers.data;
        }

        @Override
        public void write(OutputStream out) throws IOException {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                byte[] buf = new byte[65536];
                for (int i = 0; i < byteRange.length; i += 2) {
                    raf.seek(byteRange[i]);
                    long remaining = byteRange[i + 1];
                    while (remaining > 0) {
                        int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (read < 0) {
                            throw new IOException("Unexpected end of file in /ByteRange");
                        }
                        out.write(buf, 0, read);
                        remaining -= read;
                    }
                }
            }
        }

        @Override
        public Object getContent() {
            return file;
        }
    }
}
