package iped.parsers.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.apache.tika.metadata.Metadata;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import junit.framework.TestCase;

/**
 * Synthetic certificates only. The CPF and CNPJ values are fictitious numbers
 * used as examples, not real registrations.
 */
public class SignerCertificateInfoTest extends TestCase {

    private static final String CPF = "12345678909";
    private static final String CPF_RESPONSIBLE = "98765432100";
    private static final String CNPJ = "11222333000181";

    private static KeyPair keyPair;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            keyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static GeneralName otherName(String oid, ASN1Encodable value) {
        return new GeneralName(GeneralName.otherName,
                new DERSequence(new ASN1Encodable[] { new ASN1ObjectIdentifier(oid), new DERTaggedObject(true, 0, value) }));
    }

    private static DEROctetString octets(String value) {
        return new DEROctetString(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** birth date + CPF + PIS + RG + RG issuer, as in DOC-ICP-04 */
    private static String pessoaFisica(String cpf) {
        return "01011980" + cpf + "00000000000" + "000000000000000" + "SSPDF";
    }

    private static X509CertificateHolder cert(String subject, GeneralName[] altNames, Extension... extensions)
            throws Exception {
        X500Name name = new X500Name(subject);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(new X500Name("CN=Synthetic Test CA, O=IPED Test"),
                BigInteger.valueOf(7), new Date(0), new Date(4102444800000L), name, keyPair.getPublic());
        if (altNames != null) {
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(altNames));
        }
        for (Extension ext : extensions) {
            builder.addExtension(ext);
        }
        return builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate()));
    }

    private static Extension policies(String... oids) throws Exception {
        PolicyInformation[] infos = Arrays.stream(oids).map(o -> new PolicyInformation(new ASN1ObjectIdentifier(o)))
                .toArray(PolicyInformation[]::new);
        return new Extension(Extension.certificatePolicies, false, new CertificatePolicies(infos).getEncoded());
    }

    @Test
    public void testIcpBrasilPessoaFisica() throws Exception {
        X509CertificateHolder cert = cert("CN=FULANO DE TESTE:" + CPF + ", OU=Synthetic, O=ICP-Brasil, C=BR",
                new GeneralName[] { otherName(SignerCertificateInfo.OID_ICP_PESSOA_FISICA, octets(pessoaFisica(CPF))),
                        new GeneralName(GeneralName.rfc822Name, "fulano@example.invalid") },
                policies("2.16.76.1.2.3.99"));
        SignerCertificateInfo info = new SignerCertificateInfo(cert);
        assertEquals("FULANO DE TESTE", info.getName());
        assertEquals(Collections.singleton(CPF), info.getCpfs());
        assertTrue(info.getCnpjs().isEmpty());
        assertEquals(Collections.singleton("fulano@example.invalid"), info.getEmails());
        assertEquals(Collections.singletonList("2.16.76.1.2.3.99"), info.getCertificatePolicies());
        assertEquals("ICP-Brasil", info.getOrganization());
    }

    @Test
    public void testIcpBrasilPessoaJuridica() throws Exception {
        X509CertificateHolder cert = cert("CN=EMPRESA TESTE LTDA:" + CNPJ + ", O=ICP-Brasil, C=BR",
                new GeneralName[] { otherName(SignerCertificateInfo.OID_ICP_CNPJ, new DERPrintableString(CNPJ)),
                        otherName(SignerCertificateInfo.OID_ICP_RESPONSAVEL_NOME, new DERPrintableString("RESPONSAVEL TESTE")),
                        otherName(SignerCertificateInfo.OID_ICP_RESPONSAVEL_DADOS, octets(pessoaFisica(CPF_RESPONSIBLE))) });
        SignerCertificateInfo info = new SignerCertificateInfo(cert);
        assertEquals("EMPRESA TESTE LTDA", info.getName());
        assertEquals("RESPONSAVEL TESTE", info.getResponsibleName());
        assertEquals(Collections.singleton(CNPJ), info.getCnpjs());
        assertEquals(Collections.singleton(CPF_RESPONSIBLE), info.getCpfs());
    }

    @Test
    public void testNamesWithoutDistinguishedNameEscapes() throws Exception {
        X500Name subject = new org.bouncycastle.asn1.x500.X500NameBuilder()
                .addRDN(org.bouncycastle.asn1.x500.style.BCStyle.C, "BR")
                .addRDN(org.bouncycastle.asn1.x500.style.BCStyle.O, "ACME + CIA LTDA")
                .addRDN(org.bouncycastle.asn1.x500.style.BCStyle.CN, "SILVA, JOAO:" + CPF).build();
        SignerCertificateInfo info = new SignerCertificateInfo(cert(subject.toString(), null));
        assertEquals("SILVA, JOAO", info.getName());
        assertEquals("ACME + CIA LTDA", info.getOrganization());
        assertEquals(Collections.singleton(CPF), info.getCpfs());
    }

    @Test
    public void testIdFromCommonNameWhenAltNameIsMissing() throws Exception {
        SignerCertificateInfo info = new SignerCertificateInfo(cert("CN=FULANO DE TESTE:" + CPF + ", C=BR", null));
        assertEquals("FULANO DE TESTE", info.getName());
        assertEquals(Collections.singleton(CPF), info.getCpfs());
    }

    @Test
    public void testUndefinedFieldsFilledWithZerosAreIgnored() throws Exception {
        X509CertificateHolder cert = cert("CN=FULANO DE TESTE, C=BR", new GeneralName[] {
                otherName(SignerCertificateInfo.OID_ICP_PESSOA_FISICA, octets(pessoaFisica("00000000000"))) });
        SignerCertificateInfo info = new SignerCertificateInfo(cert);
        assertTrue(info.getCpfs().isEmpty());
        assertEquals("FULANO DE TESTE", info.getName());
    }

    @Test
    public void testForeignQualifiedCertificate() throws Exception {
        DERSequence statements = new DERSequence(new ASN1Encodable[] {
                new QCStatement(new ASN1ObjectIdentifier("0.4.0.1862.1.1")),
                new QCStatement(new ASN1ObjectIdentifier("0.4.0.1862.1.4")) });
        X509CertificateHolder cert = cert("CN=Erika Mustermann (TEST), O=Example GmbH, E=erika@example.invalid, C=DE", null,
                new Extension(Extension.qCStatements, false, statements.getEncoded()));
        SignerCertificateInfo info = new SignerCertificateInfo(cert);
        assertEquals("Erika Mustermann (TEST)", info.getName());
        assertEquals("Example GmbH", info.getOrganization());
        assertEquals(Collections.singleton("erika@example.invalid"), info.getEmails());
        // readable DN: the e-mail attribute is not written as an OID with hex value
        assertTrue(info.getSubjectDN(), info.getSubjectDN().contains("E=erika@example.invalid"));
        assertFalse(info.getSubjectDN(), info.getSubjectDN().contains("#"));
        assertEquals(Arrays.asList("0.4.0.1862.1.1", "0.4.0.1862.1.4"), info.getQcStatements());
        assertTrue(info.getCpfs().isEmpty());
    }

    @Test
    public void testSignerMetadata() throws Exception {
        X509CertificateHolder cert = cert("CN=FULANO DE TESTE:" + CPF + ", C=BR", new GeneralName[] {
                otherName(SignerCertificateInfo.OID_ICP_PESSOA_FISICA, octets(pessoaFisica(CPF))) });
        Metadata metadata = new Metadata();
        SignatureMetadata.addSigner(metadata, cert);
        // the same signer twice (two signatures) is written once
        SignatureMetadata.addSigner(metadata, cert);
        assertEquals(Collections.singletonList(CPF), Arrays.asList(metadata.getValues(SignatureMetadata.SIGNER_CPF)));
        assertEquals("FULANO DE TESTE", metadata.get(SignatureMetadata.SIGNER_NAME));
        assertTrue(metadata.get(SignatureMetadata.ISSUER_DN).contains("CN=Synthetic Test CA"));
    }
}
