package iped.parsers.security.detector;

import static iped.parsers.security.PemUtils.PEM_MIME;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.parsers.security.CryptoObjectDecoder;
import iped.parsers.security.CryptoObjectMimeTypes;
import iped.parsers.security.PemUtils;

public class PemDetector implements Detector {

    private static final long serialVersionUID = -7193549296685279420L;

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {

        if (PemUtils.isPossiblyPem(input, metadata)) {

            TikaInputStream tis = TikaInputStream.cast(input);
            if (tis == null) {
                return PEM_MIME;
            }

            if (tis.hasLength() && tis.getLength() < Integer.MAX_VALUE) {
                tis.mark((int) tis.getLength() + 1);
            } else {
                return PEM_MIME;
            }

            try {
                Object result = CryptoObjectDecoder.getInstance().parsePem(tis);

                if (result != null) {

                    if (result instanceof List) {
                        return PEM_MIME;
                    } else {
                        MediaType mime = CryptoObjectMimeTypes.getMimetypeFromObject(result);
                        if (mime.equals(MediaType.OCTET_STREAM)) {
                            return PEM_MIME;
                        }
                        return mime;
                    }
                } else {
                    return PEM_MIME;
                }
            } catch (IOException ignore) {

            } finally {
                tis.reset();
            }
        }

        return MediaType.OCTET_STREAM;
    }

}
