package iped.parsers.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.NullReader;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.spec.OpenSSHPrivateKeySpec;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class CryptoObjectDecoder {

    private static volatile CryptoObjectDecoder instance;
    private final CertificateFactory certificateFactory;

    private final List<String> keyAlgorithms = Arrays.asList("RSA", "DSA", "EC", "ECDSA", "EdDSA", "XDH");

    private final Map<String, KeyFactory> keyFactories = new HashMap<>();

    private CryptoObjectDecoder() {

        // accepts wrong OID when parsing CMS
        System.setProperty("org.bouncycastle.asn1.allow_wrong_oid_enc", "true");

        // uses BouncyCastle as provider
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            provider = new BouncyCastleProvider();
        }

        // initialize JCA factories
        try {
            certificateFactory = CertificateFactory.getInstance("X.509", provider);
            for (String algorithm : keyAlgorithms) {
                keyFactories.put(algorithm, KeyFactory.getInstance(algorithm, provider));
            }
        } catch (CertificateException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CryptoObjectDecoder getInstance() {
        if (instance == null) {
            synchronized (CryptoObjectDecoder.class) {
                if (instance == null) {
                    instance = new CryptoObjectDecoder();
                }
            }
        }
        return instance;
    }

    /**
     * @return a List<PemObject> in case of multiple objects in PEM or a single parsed Object in case of single object in
     *         PEM
     */
    public Object parsePem(InputStream input) throws IOException {

        PemReader reader = new PemReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        PemObject pemObject = reader.readPemObject();
        if (pemObject == null) {
            return null;
        }

        PemObject nextPemObject = reader.readPemObject();

        if (nextPemObject != null) {

            // multiple objects in PEM
            List<PemObject> objects = new ArrayList<>();
            objects.add(pemObject);
            objects.add(nextPemObject);
            while ((nextPemObject = reader.readPemObject()) != null) {
                objects.add(nextPemObject);
            }

            return objects;

        } else {

            // single object in PEM
            return parseObject(pemObject);
        }
    }

    public Object parseObject(PemObject pemObject) throws IOException {

        Object object = parseObject(pemObject.getContent());

        if (object == null) {
            PEMParser pemParser = new PEMParser(new NullReader()) {
                public PemObject readPemObject() throws IOException {
                    return pemObject;
                };
            };
            object = pemParser.readObject();
        }

        return object;
    }

    public Object parseObject(byte[] contentBytes) {

        ASN1Primitive asn1 = null;
        try {
            asn1 = ASN1Primitive.fromByteArray(contentBytes);
        } catch (IOException e) {
        }

        // attempt using JCA factories and BouncyCastle objects
        Object obj = null;
        if ((obj = tryParseAsContentInfo(asn1)) != null //
                || (obj = tryParseCertificate(contentBytes)) != null //
                || (obj = tryParseCRL(contentBytes)) != null //
                || (obj = tryParseKey(contentBytes)) != null //
                || (obj = tryParsePKCS10CSR(asn1)) != null //
                || (obj = tryParsePrivateKeyInfo(asn1)) != null //
                || (obj = tryParseEncryptedPrivateKeyInfo(asn1)) != null //
                || (obj = tryParsePKCS12Pfx(asn1)) != null //
        ) {
            return obj;
        }

        return null;

    }

    public List<? extends Certificate> tryParseCertificatePath(byte[] data) {
        try {
            return certificateFactory.generateCertPath(new ByteArrayInputStream(data), "PKCS7").getCertificates();
        } catch (Exception e) {
            return null;
        }
    }

    private Certificate tryParseCertificate(byte[] data) {
        try {
            return certificateFactory.generateCertificate(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    private CRL tryParseCRL(byte[] data) {
        try {
            return certificateFactory.generateCRL(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    private Key tryParseKey(byte[] data) {
        for (String algorithm : keyAlgorithms) {
            try {
                KeyFactory kf = keyFactories.get(algorithm);
                try {
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(data);
                    return kf.generatePrivate(keySpec);
                } catch (Exception ignore) {
                }
                try {
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(data);
                    return kf.generatePublic(keySpec);
                } catch (Exception ignore) {
                }
                try {
                    OpenSSHPrivateKeySpec keySpec = new OpenSSHPrivateKeySpec(data);
                    return kf.generatePrivate(keySpec);
                } catch (Exception ignore) {
                }
                try {
                    OpenSSHPublicKeySpec keySpec = new OpenSSHPublicKeySpec(data);
                    return kf.generatePublic(keySpec);
                } catch (Exception ignore) {
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private ContentInfo tryParseAsContentInfo(ASN1Primitive asn1) {
        try {
            return ContentInfo.getInstance(asn1);
        } catch (Exception e) {
            return null;
        }
    }

    private PKCS10CertificationRequest tryParsePKCS10CSR(ASN1Primitive asn1) {
        try {
            return new PKCS10CertificationRequest(CertificationRequest.getInstance(asn1));
        } catch (Exception e) {
            return null;
        }
    }

    private Pfx tryParsePKCS12Pfx(ASN1Primitive asn1) {
        try {
            return Pfx.getInstance(asn1);
        } catch (Exception e) {
            return null;
        }
    }

    private PrivateKeyInfo tryParsePrivateKeyInfo(ASN1Primitive asn1) {
        try {
            return PrivateKeyInfo.getInstance(asn1);
        } catch (Exception e) {
            return null;
        }
    }

    private EncryptedPrivateKeyInfo tryParseEncryptedPrivateKeyInfo(ASN1Primitive asn1) {
        try {
            return EncryptedPrivateKeyInfo.getInstance(asn1);
        } catch (Exception e) {
            return null;
        }
    }
}
