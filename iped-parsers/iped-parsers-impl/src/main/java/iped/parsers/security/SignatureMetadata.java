package iped.parsers.security;

import java.io.IOException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.metadata.Metadata;
import org.bouncycastle.cert.X509CertificateHolder;

import iped.parsers.util.MetadataUtil;
import iped.utils.DateUtil;

/**
 * Metadata fields describing digital signatures, shared by every item type that
 * carries signatures (PDF documents, and later standalone CMS files), so a
 * search for a signer finds all of them.
 *
 * On an item with several signatures the fields are multi-valued, but IPED
 * sorts and de-duplicates metadata values when normalizing them, so in the
 * index they are sets meant for searching (e.g. status:invalid finds documents
 * with at least one invalid signature), not lists aligned by signature.
 * {@link #COUNT} gives the number of signatures. Each signature, with its own
 * date, signer, status and coverage together, is a .p7s subitem when PDFs are
 * expanded.
 */
public class SignatureMetadata {

    public static final String PREFIX = "signature:";

    /** signatures and document timestamps, told apart by {@link #TYPE} */
    public static final String COUNT = PREFIX + "count";
    /** valid / invalid / unknown, see PdfSignatureExtractor.Status */
    public static final String STATUS = PREFIX + "status";
    /** signing time declared by the signer */
    public static final String DATE = PREFIX + "date";
    /** where {@link #DATE} came from: {@link #DATE_SOURCE_M} or {@link #DATE_SOURCE_CMS} */
    public static final String DATE_SOURCE = PREFIX + "dateSource";
    public static final String DATE_SOURCE_M = "/M";
    public static final String DATE_SOURCE_CMS = "signingTime";
    public static final String COVERS_WHOLE_DOCUMENT = PREFIX + "coversWholeDocument";
    /** {@link #TYPE_SIGNATURE} or {@link #TYPE_DOCUMENT_TIMESTAMP} */
    public static final String TYPE = PREFIX + "type";
    public static final String TYPE_SIGNATURE = "signature";
    /** RFC 3161 token over the document (PAdES document timestamp), signed by a timestamp authority */
    public static final String TYPE_DOCUMENT_TIMESTAMP = "documentTimestamp";
    /**
     * genTime of the RFC 3161 timestamp. On the PDF item only timestamps whose
     * message imprint matches are listed; the timestamp authority itself is not
     * validated.
     */
    public static final String TIMESTAMP_DATE = PREFIX + "timestampDate";
    public static final String TIMESTAMP_AUTHORITY = PREFIX + "timestampAuthority";

    public static final String SIGNER_SUBJECT_DN = PREFIX + "signerSubjectDN";
    public static final String SIGNER_NAME = PREFIX + "signerName";
    public static final String SIGNER_ORGANIZATION = PREFIX + "signerOrganization";
    public static final String SIGNER_EMAIL = PREFIX + "signerEmail";
    public static final String SIGNER_CPF = PREFIX + "signerCpf";
    public static final String SIGNER_CNPJ = PREFIX + "signerCnpj";
    public static final String ISSUER_DN = PREFIX + "issuerDN";
    public static final String CERTIFICATE_POLICIES = PREFIX + "certificatePolicies";
    public static final String QC_STATEMENTS = PREFIX + "qcStatements";
    /** SHA-256 of the signer certificate, hex: finds every item signed with the same certificate */
    public static final String SIGNER_CERT_SHA256 = PREFIX + "signerCertificateSha256";

    // details written only on the per-signature subitem

    /** position of the signature in document order, starting at 0 */
    public static final String INDEX = PREFIX + "index";
    public static final String FIELD_NAME = PREFIX + "fieldName";
    /** signer name declared in the signature dictionary (/Name), not taken from the certificate */
    public static final String DECLARED_NAME = PREFIX + "declaredName";
    public static final String REASON = PREFIX + "reason";
    public static final String LOCATION = PREFIX + "location";
    public static final String CONTACT_INFO = PREFIX + "contactInfo";
    public static final String FILTER = PREFIX + "filter";
    public static final String SUB_FILTER = PREFIX + "subFilter";
    /** short reason for an invalid or unknown status */
    public static final String STATUS_DETAIL = PREFIX + "statusDetail";
    public static final String DIGEST_ALGORITHM = PREFIX + "digestAlgorithm";
    public static final String SIGNATURE_ALGORITHM = PREFIX + "signatureAlgorithm";
    public static final String BYTE_RANGE = PREFIX + "byteRange";
    /** CMS signingTime attribute, also when /M exists */
    public static final String CMS_SIGNING_TIME = PREFIX + "cmsSigningTime";
    public static final String SIGNER_CERT_SERIAL = PREFIX + "signerCertificateSerial";
    public static final String SIGNER_CERT_NOT_BEFORE = PREFIX + "signerCertificateNotBefore";
    public static final String SIGNER_CERT_NOT_AFTER = PREFIX + "signerCertificateNotAfter";
    /** whether the timestamp message imprint matches the signature value or document bytes */
    public static final String TIMESTAMP_IMPRINT_MATCHES = PREFIX + "timestampImprintMatches";
    public static final String TIMESTAMP_DIGEST_ALGORITHM = PREFIX + "timestampDigestAlgorithm";
    /** message imprint of the timestamp token, in hex */
    public static final String TIMESTAMP_IMPRINT = PREFIX + "timestampImprint";

    static {
        init();
    }

    /**
     * Keeps the fields out of the "pdf:" prefix that MetadataUtil adds to PDF
     * metadata. Parsers writing these fields call it from their static block: the
     * field names are compile-time constants, so using them does not load this class.
     */
    public static void init() {
        MetadataUtil.addCustomMetadataPrefix(PREFIX);
    }

    /** Adds the identity and certificate indicators of one signer. */
    public static void addSigner(Metadata metadata, X509CertificateHolder cert) {
        SignerCertificateInfo info = new SignerCertificateInfo(cert);
        addIfNotEmpty(metadata, SIGNER_SUBJECT_DN, info.getSubjectDN());
        addIfNotEmpty(metadata, SIGNER_NAME, info.getName());
        // ICP-Brasil company certificates also name the person responsible for them
        addIfNotEmpty(metadata, SIGNER_NAME, info.getResponsibleName());
        addIfNotEmpty(metadata, SIGNER_ORGANIZATION, info.getOrganization());
        info.getEmails().forEach(v -> addIfNotEmpty(metadata, SIGNER_EMAIL, v));
        info.getCpfs().forEach(v -> addIfNotEmpty(metadata, SIGNER_CPF, v));
        info.getCnpjs().forEach(v -> addIfNotEmpty(metadata, SIGNER_CNPJ, v));
        addIfNotEmpty(metadata, ISSUER_DN, info.getIssuerDN());
        info.getCertificatePolicies().forEach(v -> addIfNotEmpty(metadata, CERTIFICATE_POLICIES, v));
        info.getQcStatements().forEach(v -> addIfNotEmpty(metadata, QC_STATEMENTS, v));
        addIfNotEmpty(metadata, SIGNER_CERT_SHA256, sha256(cert));
    }

    /** Adds serial number and validity of the signer certificate. */
    public static void addCertificateDetails(Metadata metadata, X509CertificateHolder cert) {
        addIfNotEmpty(metadata, SIGNER_CERT_SERIAL, cert.getSerialNumber().toString(16));
        addIfNotEmpty(metadata, SIGNER_CERT_NOT_BEFORE, DateUtil.dateToString(cert.getNotBefore()));
        addIfNotEmpty(metadata, SIGNER_CERT_NOT_AFTER, DateUtil.dateToString(cert.getNotAfter()));
    }

    private static String sha256(X509CertificateHolder cert) {
        try {
            return DigestUtils.sha256Hex(cert.getEncoded());
        } catch (IOException e) {
            return null;
        }
    }

    /** Adds a value unless it is empty or already present. */
    public static void addIfNotEmpty(Metadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (String existing : metadata.getValues(key)) {
            if (existing.equals(value)) {
                return;
            }
        }
        metadata.add(key, value);
    }
}
