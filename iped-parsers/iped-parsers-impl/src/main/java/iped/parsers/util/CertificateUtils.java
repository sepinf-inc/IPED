package iped.parsers.util;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X962Parameters;
import org.bouncycastle.cert.X509CertificateHolder;

public class CertificateUtils {

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
}
