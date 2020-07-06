package dpf.sp.gpinf.carving.carvers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.carving.AbstractCarver;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carver.api.InvalidCarvedObjectException;
import iped3.IItem;
import iped3.io.SeekableInputStream;

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
        try (SeekableInputStream is = parentEvidence.getStream()) {
            is.seek(header.getOffset() + 1);
            byte lenlenb[] = new byte[1];
            is.read(lenlenb);
            int lenlen = lenlenb[0] & 0x7F;
            byte lenb[] = new byte[lenlen];
            is.read(lenb);

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
    public Object validateCarvedObject(IItem parentEvidence, Hit header, long length)
            throws InvalidCarvedObjectException {
        Certificate cert = null;

        try (SeekableInputStream is = parentEvidence.getStream()) {
            byte[] buf = new byte[(int) length];
            is.seek(header.getOffset());
            is.read(buf);
            cert = parse(buf); // tenta interpretar o certificado com o tamanho do cabecalho incluso
        } catch (Exception e) {
            throw new InvalidCarvedObjectException(e);
        }

        return cert;
    }

    public Certificate parse(byte[] buff) throws IOException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            X509Certificate cert = null;

            InputStream certStream = new ByteArrayInputStream(buff);
            cert = (X509Certificate) cf.generateCertificate(certStream);

            return cert;
        } catch (CertificateException e) {
            throw new IOException(e);
        }
    }

}
