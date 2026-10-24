package iped.parsers.security;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.X509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identity of a signer and technical indicators read from its certificate.
 *
 * The CPF and CNPJ come from the ICP-Brasil otherName fields of the subject
 * alternative name (DOC-ICP-04), following the parsing done by Patrick Dalla
 * Bernardina in PR #1981. When they are absent, the "NAME:digits" common name
 * convention of ICP-Brasil certificates is used.
 *
 * The certificate policies and QC statements only say what the certificate
 * declares. They are not a qualified or advanced signature classification,
 * which requires validating the certificate chain.
 */
public class SignerCertificateInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignerCertificateInfo.class);

    /** ICP-Brasil natural person data: birth date (8), CPF (11), PIS (11), RG (15), issuer */
    static final String OID_ICP_PESSOA_FISICA = "2.16.76.1.3.1";
    /** ICP-Brasil name of the person responsible for a company certificate */
    static final String OID_ICP_RESPONSAVEL_NOME = "2.16.76.1.3.2";
    /** ICP-Brasil CNPJ of a company certificate */
    static final String OID_ICP_CNPJ = "2.16.76.1.3.3";
    /** ICP-Brasil data of the person responsible for a company certificate, same layout as 2.16.76.1.3.1 */
    static final String OID_ICP_RESPONSAVEL_DADOS = "2.16.76.1.3.4";

    private static final int BIRTH_DATE_LEN = 8;
    private static final int CPF_LEN = 11;
    private static final int CNPJ_LEN = 14;

    private static final Pattern CN_WITH_ID = Pattern.compile("^(.*\\S)\\s*:\\s*(\\d{11}|\\d{14})$");

    private final String subjectDN;
    private final String issuerDN;
    private String name;
    private String responsibleName;
    private String organization;
    private final Set<String> emails = new LinkedHashSet<>();
    private final Set<String> cpfs = new LinkedHashSet<>();
    private final Set<String> cnpjs = new LinkedHashSet<>();
    private final List<String> certificatePolicies = new ArrayList<>();
    private final List<String> qcStatements = new ArrayList<>();

    public SignerCertificateInfo(X509Certificate cert) {
        // BouncyCastle style, as for the timestamp authority: it names attributes like
        // emailAddress (E=) that the RFC 2253 format of X500Principal writes as OID=#hex
        this.subjectDN = readableName(cert.getSubjectX500Principal());
        this.issuerDN = readableName(cert.getIssuerX500Principal());
        try {
            X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());
            readSubject(holder.getSubject());
            Extensions extensions = holder.getExtensions();
            if (extensions != null) {
                readAlternativeNames(extensions);
                readPolicies(extensions);
                readQcStatements(extensions);
            }
        } catch (CertificateEncodingException | java.io.IOException | RuntimeException e) {
            LOGGER.debug("Error reading signer certificate fields", e);
        }
    }

    private static String readableName(X500Principal principal) {
        try {
            return X500Name.getInstance(BCStyle.INSTANCE, principal.getEncoded()).toString();
        } catch (RuntimeException e) {
            // a name BouncyCastle cannot read must not stop reading the other signatures
            LOGGER.debug("Error formatting distinguished name", e);
            return principal.getName();
        }
    }

    private void readSubject(X500Name subject) {
        String cn = firstValue(subject, BCStyle.CN);
        if (cn != null) {
            Matcher m = CN_WITH_ID.matcher(cn);
            if (m.matches()) {
                name = m.group(1);
                String id = m.group(2);
                (id.length() == CPF_LEN ? cpfs : cnpjs).add(id);
            } else {
                name = cn;
            }
        }
        organization = firstValue(subject, BCStyle.O);
        String email = firstValue(subject, BCStyle.EmailAddress);
        if (email != null) {
            emails.add(email);
        }
    }

    private static String firstValue(X500Name name, ASN1ObjectIdentifier type) {
        RDN[] rdns = name.getRDNs(type);
        if (rdns.length == 0 || rdns[0].getFirst() == null) {
            return null;
        }
        // the raw attribute value: IETFUtils.valueToString would add RFC 4514 escapes
        // (e.g. "SILVA\, JOAO") that a search for the name would not match
        ASN1Encodable attr = rdns[0].getFirst().getValue();
        String value = (attr instanceof ASN1String ? ((ASN1String) attr).getString()
                : IETFUtils.valueToString(attr)).trim();
        return value.isEmpty() ? null : value;
    }

    private void readAlternativeNames(Extensions extensions) {
        GeneralNames names = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
        if (names == null) {
            return;
        }
        // identifiers in the alternative names take precedence over the common name
        Set<String> sanCpfs = new LinkedHashSet<>();
        Set<String> sanCnpjs = new LinkedHashSet<>();
        for (GeneralName gn : names.getNames()) {
            try {
                if (gn.getTagNo() == GeneralName.rfc822Name) {
                    emails.add(((ASN1String) gn.getName()).getString());
                } else if (gn.getTagNo() == GeneralName.otherName) {
                    ASN1Sequence otherName = ASN1Sequence.getInstance(gn.getName());
                    String oid = ASN1ObjectIdentifier.getInstance(otherName.getObjectAt(0)).getId();
                    String value = otherNameValue(ASN1TaggedObject.getInstance(otherName.getObjectAt(1)).getObject());
                    readIcpBrasilOtherName(oid, value, sanCpfs, sanCnpjs);
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Error reading subject alternative name", e);
            }
        }
        if (!sanCpfs.isEmpty()) {
            cpfs.clear();
            cpfs.addAll(sanCpfs);
        }
        if (!sanCnpjs.isEmpty()) {
            cnpjs.clear();
            cnpjs.addAll(sanCnpjs);
        }
    }

    private static String otherNameValue(ASN1Encodable value) {
        if (value instanceof ASN1String) {
            return ((ASN1String) value).getString();
        }
        if (value instanceof ASN1OctetString) {
            return new String(((ASN1OctetString) value).getOctets(), StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private void readIcpBrasilOtherName(String oid, String value, Set<String> sanCpfs, Set<String> sanCnpjs) {
        if (value == null) {
            return;
        }
        if (OID_ICP_PESSOA_FISICA.equals(oid) || OID_ICP_RESPONSAVEL_DADOS.equals(oid)) {
            if (value.length() >= BIRTH_DATE_LEN + CPF_LEN) {
                addIfDefined(sanCpfs, value.substring(BIRTH_DATE_LEN, BIRTH_DATE_LEN + CPF_LEN));
            }
        } else if (OID_ICP_CNPJ.equals(oid)) {
            if (value.length() >= CNPJ_LEN) {
                addIfDefined(sanCnpjs, value.substring(0, CNPJ_LEN));
            }
        } else if (OID_ICP_RESPONSAVEL_NOME.equals(oid)) {
            String responsible = value.trim();
            responsibleName = responsible.isEmpty() ? null : responsible;
        }
    }

    /** ICP-Brasil fills fields that are not defined with zeros */
    private static void addIfDefined(Set<String> target, String digits) {
        if (digits.chars().allMatch(Character::isDigit) && digits.chars().anyMatch(c -> c != '0')) {
            target.add(digits);
        }
    }

    private void readPolicies(Extensions extensions) {
        CertificatePolicies policies = CertificatePolicies.fromExtensions(extensions);
        if (policies == null) {
            return;
        }
        for (PolicyInformation info : policies.getPolicyInformation()) {
            certificatePolicies.add(info.getPolicyIdentifier().getId());
        }
    }

    private void readQcStatements(Extensions extensions) {
        Extension ext = extensions.getExtension(Extension.qCStatements);
        if (ext == null) {
            return;
        }
        ASN1Sequence statements = ASN1Sequence.getInstance(ext.getParsedValue());
        for (int i = 0; i < statements.size(); i++) {
            qcStatements.add(QCStatement.getInstance(statements.getObjectAt(i)).getStatementId().getId());
        }
    }

    public String getSubjectDN() {
        return subjectDN;
    }

    public String getIssuerDN() {
        return issuerDN;
    }

    /** common name without the ICP-Brasil ":CPF" or ":CNPJ" suffix */
    public String getName() {
        return name;
    }

    /** person responsible for an ICP-Brasil company certificate, or null */
    public String getResponsibleName() {
        return responsibleName;
    }

    public String getOrganization() {
        return organization;
    }

    public Set<String> getEmails() {
        return emails;
    }

    /** digits only; includes the CPF of the person responsible for a company certificate */
    public Set<String> getCpfs() {
        return cpfs;
    }

    /** digits only */
    public Set<String> getCnpjs() {
        return cnpjs;
    }

    public List<String> getCertificatePolicies() {
        return certificatePolicies;
    }

    public List<String> getQcStatements() {
        return qcStatements;
    }
}
