package iped.parsers.security;

import java.security.Key;
import java.security.cert.CRL;
import java.security.cert.Certificate;

import org.apache.tika.mime.MediaType;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

public class CryptoObjectMimeTypes {

    // MediaType constants
    public static final MediaType X509_CERT_TYPE = MediaType.application("pkix-cert");
    public static final MediaType X509_CRL_TYPE = MediaType.application("pkix-crl");
    public static final MediaType PKCS10_TYPE = MediaType.application("pkcs10");
    public static final MediaType PKCS7_DATA_TYPE = MediaType.application("pkcs7-mime");
    public static final MediaType PKCS7_SIGNED_DATA_TYPE = MediaType.application("pkcs7-signature");
    public static final MediaType PKCS8_UNENCRYPTED_TYPE = MediaType.application("pkcs8");
    public static final MediaType PKCS8_ENCRYPTED_TYPE = MediaType.application("pkcs8-encrypted");

    public static MediaType getMimetypeFromObject(Object object) {

        if (object instanceof Certificate) {
            return X509_CERT_TYPE;
        } else if (object instanceof CRL) {
            return X509_CERT_TYPE;
        } else if (object instanceof Key) {
            return PKCS8_UNENCRYPTED_TYPE;
        } else if (object instanceof X509CertificateHolder) {
            return X509_CERT_TYPE;
        } else if (object instanceof X509CRLHolder) {
            return X509_CRL_TYPE;
        } else if (object instanceof PKCS10CertificationRequest) {
            return PKCS10_TYPE;
        } else if (object instanceof ContentInfo) {
            ContentInfo ci = (ContentInfo) object;
            if (CMSObjectIdentifiers.signedData.equals(ci.getContentType())
                    || CMSObjectIdentifiers.signedAndEnvelopedData.equals(ci.getContentType())) {
                return PKCS7_SIGNED_DATA_TYPE;
            } else {
                return PKCS7_DATA_TYPE;
            }
        } else if (object instanceof PrivateKeyInfo //
                || object instanceof PEMKeyPair) {
            return PKCS8_UNENCRYPTED_TYPE;
        } else if (object instanceof EncryptedPrivateKeyInfo //
                || object instanceof PKCS8EncryptedPrivateKeyInfo //
                || object instanceof PEMEncryptedKeyPair) {
            return PKCS8_ENCRYPTED_TYPE;
        } else if (object instanceof Pfx) {
            return KeystoreParser.PKCS12_MIME;
        } else {
            return MediaType.OCTET_STREAM;
        }
    }
}
