package iped.parsers.util;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X962Parameters;
import org.bouncycastle.cert.X509CertificateHolder;

public class CertificateUtils {

    // A map for KeyPurposeId OIDs to human-readable names
    private static final Map<KeyPurposeId, String> keyPurposeIdMap = new HashMap<>();

    static {
        keyPurposeIdMap.put(KeyPurposeId.id_kp_serverAuth, "TLS Web Server Authentication");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_clientAuth, "TLS Web Client Authentication");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_codeSigning, "Code Signing");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_emailProtection, "Email Protection");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_ipsecEndSystem, "IPsec End System");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_ipsecTunnel, "IPsec Tunnel");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_ipsecUser, "IPsec User");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_timeStamping, "Time Stamping");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_OCSPSigning, "OCSP Signing");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_dvcs, "Data Validation and Certification Server (DVCS)");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_sbgpCertAAServerAuth, "SBGP Cert AA Server Authentication");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_scvp_responder, "SCVP Responder");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_eapOverPPP, "EAP over PPP");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_eapOverLAN, "EAP over LAN");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_scvpServer, "SCVP Server");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_scvpClient, "SCVP Client");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_ipsecIKE, "IPsec Internet Key Exchange (IKE)");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_capwapAC, "CAPWAP Access Controller");
        keyPurposeIdMap.put(KeyPurposeId.id_kp_capwapWTP, "CAPWAP Wireless Termination Point");
        keyPurposeIdMap.put(KeyPurposeId.anyExtendedKeyUsage, "Any Extended Key Usage");
    }

    public static int getRSAKeySize(X509Certificate cert) {
        PublicKey publicKey = cert.getPublicKey();
        if (publicKey instanceof RSAPublicKey) {
            RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
            return rsaKey.getModulus().bitLength();
        } else {
            throw new IllegalArgumentException("Certificate does not contain an RSA public key.");
        }
    }

    public static String getECCurveName(X509CertificateHolder holder) {
        SubjectPublicKeyInfo spki = holder.getSubjectPublicKeyInfo();

        try {
            ASN1Primitive params = spki.getAlgorithm().getParameters().toASN1Primitive();
            X962Parameters x962Params = X962Parameters.getInstance(params);

            if (x962Params.isNamedCurve()) {
                ASN1ObjectIdentifier curveOid = ASN1ObjectIdentifier.getInstance(x962Params.getParameters());

                // Try to resolve name
                String name = ECNamedCurveTable.getName(curveOid);
                return name != null ? name : curveOid.getId(); // fallback to OID string
            } else {
                return "explicit parameters";
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getExtendedKeyUsageStrings(X509CertificateHolder certHolder) {
        List<String> extendedKeyUsages = new ArrayList<>();

        Extension extendedKeyUsageExtension = certHolder.getExtension(Extension.extendedKeyUsage);
        if (extendedKeyUsageExtension != null) {
            ExtendedKeyUsage eku = ExtendedKeyUsage.getInstance(extendedKeyUsageExtension.getParsedValue());
            KeyPurposeId[] usages = eku.getUsages();
            for (KeyPurposeId keyPurposeId : usages) {
                String name = keyPurposeIdMap.get(keyPurposeId);
                if (name != null) {
                    extendedKeyUsages.add(name);
                } else {
                    // If not in our map, add the OID string itself
                    extendedKeyUsages.add(keyPurposeId.getId());
                }
            }
        }
        return extendedKeyUsages;
    }
}
