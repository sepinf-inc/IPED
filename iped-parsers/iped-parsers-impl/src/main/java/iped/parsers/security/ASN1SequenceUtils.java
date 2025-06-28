package iped.parsers.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ASN1SequenceUtils {

    private static Logger logger = LoggerFactory.getLogger(ASN1SequenceUtils.class);

    public static final MediaType ASN1_SEQUENCE_MIME = MediaType.application("x-asn1-sequence");

    private static final int ASN1_SEQUENCE_TAG = 0x30;

    // enough bytes to decode TAG + LEN
    private static final int TAG_LEN_HEADER_SIZE = 8;

    public static boolean isPotentiallyValidASN1Sequence(InputStream input) throws IOException {

        input.mark(TAG_LEN_HEADER_SIZE);
        byte[] initialBytes = new byte[TAG_LEN_HEADER_SIZE];
        input.read(initialBytes);
        input.reset();

        long inputLength = -1;
        TikaInputStream tis = TikaInputStream.cast(input);
        if (tis != null) {
            inputLength = tis.getLength();
        }

        if (isPotentiallyValidASN1Sequence(initialBytes, inputLength)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the initial bytes of a file and its total size are consistent with a top-level ASN.1 SEQUENCE structure.
     *
     * @param initialBytes  The first bytes of the file.
     * @param totalFileSize The known total size of the file. If -1, don't compare with calculated size.
     * @return true if the structure appears to be a valid ASN.1 SEQUENCE consistent with the total file size, false
     *         otherwise.
     */
    private static boolean isPotentiallyValidASN1Sequence(byte[] initialBytes, long totalFileSize) {
        if (initialBytes == null || initialBytes.length < 2) {
            return false;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(initialBytes);

        try {
            int tag = bais.read(); // Reads the Tag byte

            if (tag != ASN1_SEQUENCE_TAG) {
                return false;
            }

            int tagSize = 1; // SEQUENCE tag 0x30 is a single byte.

            int lengthOctet1 = bais.read(); // Reads the first byte of the Length

            long decodedValueLength;
            int lengthFieldSize = 1; // Size of the "Length" field itself

            if (lengthOctet1 == 0x80) { // Indefinite-length
                return true;

            } else if ((lengthOctet1 & 0x80) == 0) { // Short form: bit 8 is 0
                decodedValueLength = lengthOctet1;

            } else { // Long form: bit 8 is 1
                int numLengthOctets = lengthOctet1 & 0x7F;
                if (numLengthOctets > 4) {
                    logger.warn("Invalid number of length octets in long form or indefinite length for ASN.1 SEQUENCE: " + numLengthOctets);
                    return false;
                }

                if (bais.available() < numLengthOctets) {
                    logger.warn("Insufficient initial bytes to read the entire long-form Length field for ASN.1 SEQUENCE. Needed: " + numLengthOctets
                            + ", Available: " + bais.available());
                    return false;
                }

                byte[] lengthBytes = new byte[numLengthOctets];
                int bytesReadFromLengthField = bais.read(lengthBytes);

                if (bytesReadFromLengthField < numLengthOctets) {
                    logger.warn("Could not read all octets of the long-form Length field for ASN.1 SEQUENCE.");
                    return false;
                }

                if (lengthBytes[0] == 0x00) {
                    logger.warn("DER violation: first octet of long-form Length field is 0x00 for DER SEQUENCE.");
                    return false;
                }

                decodedValueLength = 0;
                for (byte b : lengthBytes) {
                    decodedValueLength = (decodedValueLength << 8) | (b & 0xFF);

                    if ((decodedValueLength >>> 23) != 0) {
                        logger.warn("Long form definite-length more than 31 bits");
                        return false;
                    }
                }
                lengthFieldSize += numLengthOctets;
            }

            // don't compare the size since we don't have it
            if (totalFileSize == -1) {
                return true;
            }

            long expectedObjectSize = tagSize + lengthFieldSize + decodedValueLength;

            // true if sizes match
            return expectedObjectSize == totalFileSize;

        } catch (IOException e) {
            logger.warn("Unexpected IO error", e);
            return false;
        }
    }

}
