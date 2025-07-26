package iped.parsers.util;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

public class HashUtils {

    private static final HashSet<String> zeroLengthHashes = new HashSet<>(Arrays.asList( //
            // Hashes of empty input (byte[0]), see issue #2157.
            "d41d8cd98f00b204e9800998ecf8427e", // md5
            "da39a3ee5e6b4b0d3255bfef95601890afd80709", // sha-1
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // sha-256
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e", // sha-512
            "31d6cfe0d16ae931b73c59d7e0c089c0" // edonkey
    ));

    private static boolean isZeroLengthHash(String hash) {
        if (zeroLengthHashes.contains(hash.toLowerCase())) {
            return true;
        }
        try {
            byte[] hashBytes = Base64.getDecoder().decode(hash);
            return zeroLengthHashes.contains(Hex.encodeHexString(hashBytes).toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValidHash(String hash) {
        return StringUtils.isNotBlank(hash) && !isZeroLengthHash(hash);
    }
}