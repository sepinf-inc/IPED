package iped.carvers.custom;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;

import iped.carvers.api.CarverType;
import iped.carvers.api.Hit;
import iped.carvers.api.InvalidCarvedObjectException;
import iped.carvers.standard.AbstractCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class DERCarver extends AbstractCarver {

    public DERCarver() throws DecoderException {
        carverTypes = new CarverType[1];

        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("\\30\\82??\\30\\82");
        carverTypes[0].addHeader("\\30\\83???\\30\\83");
        carverTypes[0].setMimeType(MediaType.parse("application/pkix-cert"));
        carverTypes[0].setMaxLength(100000);
        carverTypes[0].setMinLength(1000);
        carverTypes[0].setName("DER");
        carverTypes[0].setCarverClass(this.getClass().getName());
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(header.getOffset() + 1);
            int b = is.read();
            if (b == -1) {
                throw new IOException("End of stream reached");
            }
            int lenlen = b & 0x7F;
            byte[] lenb = is.readNBytes(lenlen);
            if (lenb.length < lenlen) {
                throw new IOException("End of stream reached");
            }
            long len = 0;
            long valor = 0;
            for (int j = 0; j < lenlen; j++) {
                valor = ((long) lenb[j] & 0xff);
                valor = (long) (valor << (8 * (lenlen - j - 1)));
                len |= valor;
            }

            return len + 4;// soma o tamanho do cabeçalho
        }
    }

    @Override
    public void validateCarvedObject(IItem parentEvidence, Hit header, long length)
            throws InvalidCarvedObjectException {

        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(header.getOffset());
            byte[] buf = is.readNBytes((int) length);
            if (buf.length < length) {
                throw new IOException("End of stream reached");
            }
            parse(buf); // tenta interpretar o certificado com o tamanho do cabecalho incluso

        } catch (Exception e) {
            throw new InvalidCarvedObjectException(e);
        }

    }

    public void parse(byte[] buff) throws IOException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream certStream = new ByteArrayInputStream(buff);
            cf.generateCertificate(certStream);

        } catch (CertificateException e) {
            try {
                // se não for um certificado válido tenta verificar se é um keystore
                KeyStore p12 = KeyStore.getInstance("PKCS12");
                ByteArrayInputStream bais = new ByteArrayInputStream(buff);
                p12.load(bais, "123".toCharArray());

            } catch (Exception e1) {
                if (!e1.toString().contains("password")) {
                    // o erro não foi de senha invalida, então a stream foi aparentemente
                    // recuperada.
                    throw new IOException(e1);
                }
            }
        }
    }

}
