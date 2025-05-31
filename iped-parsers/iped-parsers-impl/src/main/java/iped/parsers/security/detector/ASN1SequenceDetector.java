package iped.parsers.security.detector;

import static iped.parsers.security.ASN1SequenceUtils.ASN1_SEQUENCE_MIME;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.parsers.security.ASN1SequenceUtils;
import iped.parsers.security.CryptoObjectDecoder;
import iped.parsers.security.CryptoObjectMimeTypes;

public class ASN1SequenceDetector implements Detector {

    private static final long serialVersionUID = 6047410789833041492L;

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {

        if (ASN1SequenceUtils.isPotentiallyValidASN1Sequence(input)) {

            TikaInputStream tis = TikaInputStream.cast(input);
            if (tis == null) {
                return ASN1_SEQUENCE_MIME;
            }

            if (tis.hasLength() && tis.getLength() < Integer.MAX_VALUE) {
                tis.mark((int) tis.getLength() + 1);
            }

            try {
                Object result = CryptoObjectDecoder.getInstance().parseObject(IOUtils.toByteArray(tis));

                if (result != null) {

                    MediaType mime = CryptoObjectMimeTypes.getMimetypeFromObject(result);
                    return mime;

                } else {
                    return MediaType.OCTET_STREAM;
                }
            } finally {
                tis.reset();
            }
        }

        return MediaType.OCTET_STREAM;
    }

}
