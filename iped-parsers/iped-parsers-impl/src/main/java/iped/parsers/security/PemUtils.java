package iped.parsers.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

import com.google.common.primitives.Bytes;

public class PemUtils {

    public static final MediaType PEM_MIME = MediaType.application("x-pem-file");

    private static final int BYTES_TO_READ_IN_PEM_EXTENSION = 8 * 1024;

    private static final byte[] BEGIN_TAG = "-----BEGIN".getBytes(StandardCharsets.US_ASCII);

    private static final Set<String> pemExtensions = Set.of("pem", "crt", "cer", "cert", "key", "csr", "pkcs8", "pub");

    public static boolean isPossiblyPem(InputStream input, Metadata metadata) throws IOException {

        // check if input starts with "-----BEGIN"
        input.mark(BEGIN_TAG.length);
        byte[] initialBytes = new byte[BEGIN_TAG.length];
        int n = input.read(initialBytes);
        input.reset();
        if (n < BEGIN_TAG.length) {
            return false;
        }
        if (Arrays.equals(initialBytes, BEGIN_TAG)) {
            return true;
        }

        // if ext is a PEM extension, check if it contains "-----BEGIN" in the first 8KB
        String ext = StringUtils.substringAfterLast(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY), ".");
        if (pemExtensions.contains(ext)) {

            input.mark(BYTES_TO_READ_IN_PEM_EXTENSION);
            byte[] moreBytes = new byte[BYTES_TO_READ_IN_PEM_EXTENSION];
            n = input.read(moreBytes);
            input.reset();
            if (n < BEGIN_TAG.length) {
                return false;
            }
            if (Bytes.indexOf(moreBytes, BEGIN_TAG) >= 0) {
                return true;
            }
        }
        return false;
    }

}
