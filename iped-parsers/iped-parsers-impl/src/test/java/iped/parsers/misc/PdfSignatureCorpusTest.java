package iped.parsers.misc;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import iped.parsers.misc.PdfSignatureExtractor.SignatureInfo;
import iped.parsers.misc.PdfSignatureExtractor.Status;
import iped.utils.DateUtil;
import junit.framework.TestCase;

/**
 * Checks PdfSignatureExtractor against EXPECTED.json of the CC0 corpus in
 * test-files/pdf-signatures (see its README.md).
 *
 * Every field the corpus records is either compared or listed in
 * {@link #NOT_COMPARED} with the reason, so a corpus update with new fields
 * fails here instead of being silently ignored. A field is compared when both
 * validators agree; where they disagree, the value of the PDFBox +
 * BouncyCastle validator (v2) is used, since IPED runs on the same libraries.
 */
public class PdfSignatureCorpusTest extends TestCase {

    private static final String DIR = "test-files/pdf-signatures/";

    /** corpus fields this test does not compare, and why */
    private static final Set<String> NOT_COMPARED = new HashSet<>(Arrays.asList(
            // produced by PDFBox when loading the AcroForm, not by IPED
            "document.signature_field_count",
            // diagnostics of the corpus validators, not expected values
            "signature._v2_only", "signature._v3_openssl"));

    private static final Set<String> COMPARED_DOCUMENT_FIELDS = new HashSet<>(
            Arrays.asList("file_size", "loads", "signature_count"));

    private static final Set<String> COMPARED_SIGNATURE_FIELDS = new HashSet<>(Arrays.asList("field_name", "name",
            "reason", "location", "contact_info", "filter", "subfilter", "m", "cms_signing_time", "byte_range",
            "covers_whole_file", "gap_is_contents_only", "digest_algorithm", "signature_algorithm", "cms_parsed",
            "digest_match", "signature_valid", "crypto_ok", "extraction_status", "cert_subject_cn",
            "cert_issuer_cn", "cert_subject_der_sha256", "cert_issuer_der_sha256", "cert_serial_hex",
            "cert_not_before", "cert_not_after", "cert_sha256_fingerprint", "cert_valid_at_m", "ts_present",
            "ts_gen_time", "ts_imprint_alg", "ts_imprint_hex", "ts_imprint_matches_signature"));

    private static JsonNode expected() throws Exception {
        return new ObjectMapper().readTree(resource("EXPECTED.json"));
    }

    private static File resource(String name) throws Exception {
        return new File(Thread.currentThread().getContextClassLoader().getResource(DIR + name).toURI());
    }

    /** the agreed value, or the PDFBox + BouncyCastle value (v2) on a disagreement */
    private static JsonNode value(JsonNode field) {
        return "agreed".equals(field.path("status").asText()) ? field.get("value") : field.get("v2");
    }

    @Test
    public void testEveryCorpusFieldIsAccountedFor() throws Exception {
        Set<String> missing = new TreeSet<>();
        for (Iterator<JsonNode> it = expected().get("cases").elements(); it.hasNext();) {
            JsonNode c = it.next();
            c.get("document").fieldNames().forEachRemaining(k -> {
                if (!COMPARED_DOCUMENT_FIELDS.contains(k) && !NOT_COMPARED.contains("document." + k)) {
                    missing.add("document." + k);
                }
            });
            c.get("signatures").elements().forEachRemaining(s -> s.fieldNames().forEachRemaining(k -> {
                if (!COMPARED_SIGNATURE_FIELDS.contains(k) && !NOT_COMPARED.contains("signature." + k)) {
                    missing.add("signature." + k);
                }
            }));
        }
        assertTrue("corpus fields neither compared nor excluded: " + missing, missing.isEmpty());
    }

    @Test
    public void testAllCasesMatchExpectedValues() throws Exception {
        JsonNode cases = expected().get("cases");
        List<String> mismatches = new ArrayList<>();
        int compared = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = cases.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            compared += checkCase(e.getKey(), e.getValue(), mismatches);
        }
        assertTrue(mismatches.size() + " mismatches:\n" + String.join("\n", mismatches), mismatches.isEmpty());
        assertEquals("cases", 17, cases.size());
        System.out.println("PdfSignatureCorpusTest: " + compared + " values compared");
        // guards against a test that compares nothing
        assertTrue("compared values: " + compared, compared > 400);
    }

    /** positive control: a changed expected value must be reported */
    @Test
    public void testDetectsChangedExpectedValues() throws Exception {
        JsonNode cases = expected().get("cases");
        ObjectNode a01 = (ObjectNode) cases.get("A01").get("signatures").get("Signature1");
        ((ObjectNode) a01.get("crypto_ok")).put("value", false);
        ((ObjectNode) a01.get("cert_serial_hex")).put("value", "1002");
        ObjectNode b01 = (ObjectNode) cases.get("B01").get("signatures").get("Signature1");
        ((ObjectNode) b01.get("ts_gen_time")).put("value", "2026-01-15T12:00:00Z");

        List<String> mismatches = new ArrayList<>();
        checkCase("A01", cases.get("A01"), mismatches);
        checkCase("B01", cases.get("B01"), mismatches);
        String all = String.join("\n", mismatches);
        assertTrue(all, all.contains("A01 Signature1 status (crypto_ok=false)"));
        assertTrue(all, all.contains("A01 Signature1 cert_serial_hex"));
        assertTrue(all, all.contains("B01 Signature1 ts_gen_time"));
    }

    private int checkCase(String id, JsonNode c, List<String> mismatches) throws Exception {
        File file = resource(c.get("file").asText());
        JsonNode doc = c.get("document");
        Checker check = new Checker(id, mismatches);

        check.eq("file_size", value(doc.get("file_size")).asLong(), file.length());
        boolean expectedLoads = value(doc.get("loads")).asBoolean();
        List<SignatureInfo> sigs;
        try (PDDocument document = PDDocument.load(file)) {
            sigs = new PdfSignatureExtractor().extract(document, file);
        } catch (IOException e) {
            check.eq("loads", expectedLoads, false);
            return check.count;
        }
        check.eq("loads", expectedLoads, true);
        check.eq("signature_count", value(doc.get("signature_count")).asInt(), sigs.size());

        for (Iterator<Map.Entry<String, JsonNode>> it = c.get("signatures").fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            SignatureInfo info = sigs.stream().filter(s -> e.getKey().equals(s.fieldName)).findFirst().orElse(null);
            if (info == null) {
                mismatches.add(id + " " + e.getKey() + ": signature not extracted");
                continue;
            }
            check.count += checkSignature(new Checker(id + " " + e.getKey(), mismatches), e.getValue(), info,
                    file.length());
        }
        return check.count;
    }

    /** returns the number of compared values */
    private static int checkSignature(Checker check, JsonNode s, SignatureInfo info, long fileLength)
            throws Exception {
        check.str(s, "field_name", info.fieldName);
        check.str(s, "name", info.name);
        check.str(s, "reason", info.reason);
        check.str(s, "location", info.location);
        check.str(s, "contact_info", info.contactInfo);
        check.str(s, "filter", info.filter);
        check.str(s, "subfilter", info.subFilter);
        check.str(s, "m", info.signDate != null ? DateUtil.dateToString(info.signDate.getTime()) : null);
        check.str(s, "cms_signing_time", info.cmsSigningTime != null ? DateUtil.dateToString(info.cmsSigningTime) : null);

        int[] br = info.byteRange;
        List<Integer> expectedRange = new ArrayList<>();
        value(s.get("byte_range")).forEach(v -> expectedRange.add(v.asInt()));
        check.eq("byte_range", expectedRange, br == null ? null : Arrays.asList(br[0], br[1], br[2], br[3]));
        boolean reachesEnd = br != null && br[0] == 0 && (long) br[2] + br[3] == fileLength;
        check.eq("covers_whole_file", value(s.get("covers_whole_file")).asBoolean(), reachesEnd);
        check.eq("gap_is_contents_only", value(s.get("gap_is_contents_only")).asBoolean(), info.gapIsContentsOnly);
        // IPED reports whole document coverage only when both hold
        check.eq("coversWholeDocument", reachesEnd && info.gapIsContentsOnly, info.coversWholeDocument);

        check.str(s, "digest_algorithm", info.digestAlgorithmOid);
        check.str(s, "signature_algorithm", info.signatureAlgorithmOid);

        // cryptographic verdicts, mapped to the status IPED reports
        boolean cmsParsed = value(s.get("cms_parsed")).asBoolean();
        check.eq("cms_parsed / extraction_status", value(s.get("extraction_status")).asText(), cmsParsed ? "OK" : "FAILED");
        if (!cmsParsed) {
            check.eq("status (cms_parsed=false)", Status.UNKNOWN, info.status);
        } else {
            boolean cryptoOk = value(s.get("crypto_ok")).asBoolean();
            check.eq("status (crypto_ok=" + cryptoOk + ")", cryptoOk ? Status.VALID : Status.INVALID, info.status);
            boolean digestMatch = value(s.get("digest_match")).asBoolean();
            boolean signatureValid = value(s.get("signature_valid")).asBoolean();
            check.eq("crypto_ok = digest_match && signature_valid", cryptoOk, digestMatch && signatureValid);
            if (!digestMatch) {
                check.eq("digest_match", "message digest does not match /ByteRange content", info.statusDetail);
            } else if (!signatureValid) {
                check.eq("signature_valid", "signature value does not match", info.statusDetail);
            }
        }

        X509Certificate cert = info.signerCertificate;
        if (cert == null) {
            for (String k : new String[] { "cert_subject_cn", "cert_issuer_cn", "cert_serial_hex", "cert_not_before",
                    "cert_not_after", "cert_sha256_fingerprint", "cert_subject_der_sha256", "cert_issuer_der_sha256",
                    "cert_valid_at_m" }) {
                check.isNull(s, k);
            }
        } else {
            check.str(s, "cert_subject_cn", cn(cert.getSubjectX500Principal().getEncoded()));
            check.str(s, "cert_issuer_cn", cn(cert.getIssuerX500Principal().getEncoded()));
            check.str(s, "cert_serial_hex", cert.getSerialNumber().toString(16));
            check.str(s, "cert_not_before", DateUtil.dateToString(cert.getNotBefore()));
            check.str(s, "cert_not_after", DateUtil.dateToString(cert.getNotAfter()));
            check.str(s, "cert_sha256_fingerprint", sha256(cert.getEncoded()));
            check.str(s, "cert_subject_der_sha256", sha256(cert.getSubjectX500Principal().getEncoded()));
            check.str(s, "cert_issuer_der_sha256", sha256(cert.getIssuerX500Principal().getEncoded()));
            if (info.signDate != null) {
                Date m = info.signDate.getTime();
                check.eq("cert_valid_at_m", value(s.get("cert_valid_at_m")).asBoolean(),
                        !m.before(cert.getNotBefore()) && !m.after(cert.getNotAfter()));
            }
        }

        check.eq("ts_present", value(s.get("ts_present")).asBoolean(), info.timestampImprintMatches != null);
        check.str(s, "ts_gen_time", info.timestampGenTime != null ? DateUtil.dateToString(info.timestampGenTime) : null);
        check.str(s, "ts_imprint_alg", info.timestampDigestAlgorithmOid);
        check.str(s, "ts_imprint_hex", info.timestampImprint != null ? Hex.toHexString(info.timestampImprint) : null);
        JsonNode tsMatches = value(s.get("ts_imprint_matches_signature"));
        check.eq("ts_imprint_matches_signature", tsMatches.isNull() ? null : tsMatches.asBoolean(),
                info.timestampImprintMatches);
        return check.count;
    }

    private static String cn(byte[] encodedName) {
        X500Name name = X500Name.getInstance(encodedName);
        if (name.getRDNs(BCStyle.CN).length == 0) {
            return null;
        }
        // raw value, without RFC 4514 escapes
        org.bouncycastle.asn1.ASN1Encodable value = name.getRDNs(BCStyle.CN)[0].getFirst().getValue();
        return value instanceof org.bouncycastle.asn1.ASN1String ? ((org.bouncycastle.asn1.ASN1String) value).getString()
                : IETFUtils.valueToString(value);
    }

    private static String sha256(byte[] data) throws Exception {
        return Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static class Checker {
        final String prefix;
        final List<String> mismatches;
        int count;

        Checker(String prefix, List<String> mismatches) {
            this.prefix = prefix;
            this.mismatches = mismatches;
        }

        void eq(String field, Object expected, Object actual) {
            count++;
            if (expected == null ? actual != null : !expected.equals(actual)) {
                mismatches.add(prefix + " " + field + ": expected <" + expected + "> but was <" + actual + ">");
            }
        }

        void str(JsonNode signature, String field, String actual) {
            JsonNode v = value(signature.get(field));
            eq(field, v == null || v.isNull() ? null : v.asText(), actual);
        }

        /** the corpus records no value where IPED found no certificate */
        void isNull(JsonNode signature, String field) {
            JsonNode v = value(signature.get(field));
            eq(field + " (no signer certificate found)", v == null || v.isNull() ? null : v.asText(), null);
        }
    }
}
