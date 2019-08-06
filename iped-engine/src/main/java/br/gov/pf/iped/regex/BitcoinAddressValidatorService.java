package br.gov.pf.iped.regex;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

/**
 * Copied and adapted from https://github.com/timqi/btc_address_validator
 *
 */
public class BitcoinAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private static final int[] INDEXES = new int[128];

    private static final MessageDigest digest;

    private static int[] BECH32_CHARSET_REV = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, 15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13, 25, 9, 8, 23,
            -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13,
            25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1 };

    private static int[] BECH32_GENERATOR = { 0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3 };

    static {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < INDEXES.length; i++) {
            INDEXES[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEXES[ALPHABET[i]] = i;
        }
    }

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("BITCOIN_ADDRESS");
    }

    @Override
    protected boolean validate(String hit) {
        return validateBitcoinAddress(hit);
    }

    public boolean validateBitcoinAddress(String addr) {
        if (addr.startsWith("bc1")) {
            return bech32VerifyChecksum(addr);
        } else {
            try {
                int addressHeader = getAddressHeader(addr);
                return (addressHeader == 0 || addressHeader == 5);
            } catch (Exception x) {
            }
        }
        return false;
    }

    private static int getAddressHeader(String address) throws IOException {
        byte[] tmp = decodeChecked(address);
        return tmp[0] & 0xFF;
    }

    private static byte[] decodeChecked(String input) throws IOException {
        byte[] tmp = decodeBase58To25Bytes(input);
        if (tmp.length < 4)
            throw new IOException("BTC AddressFormatException Input too short");
        byte[] bytes = copyOfRange(tmp, 0, tmp.length - 4);
        byte[] checksum = copyOfRange(tmp, tmp.length - 4, tmp.length);

        tmp = doubleDigest(bytes);
        byte[] hash = copyOfRange(tmp, 0, 4);
        if (!Arrays.equals(checksum, hash))
            throw new IOException("BTC AddressFormatException Checksum does not validate");

        return bytes;
    }

    private static byte[] decodeBase58To25Bytes(String input) {
        if (input.length() == 0) {
            return new byte[0];
        }
        byte[] input58 = new byte[input.length()];
        // Transform the String to a base58 byte sequence
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            int digit58 = -1;
            if (c >= 0 && c < 128) {
                digit58 = INDEXES[c];
            }
            if (digit58 < 0) {
                return null;
            }

            input58[i] = (byte) digit58;
        }

        // Count leading zeroes
        int zeroCount = 0;
        while (zeroCount < input58.length && input58[zeroCount] == 0) {
            ++zeroCount;
        }
        // The encoding
        byte[] temp = new byte[input.length()];
        int j = temp.length;

        int startAt = zeroCount;
        while (startAt < input58.length) {
            byte mod = divmod256(input58, startAt);
            if (input58[startAt] == 0) {
                ++startAt;
            }

            temp[--j] = mod;
        }
        // Do no add extra leading zeroes, move j to first non null byte.
        while (j < temp.length && temp[j] == 0) {
            ++j;
        }

        return copyOfRange(temp, j - zeroCount, temp.length);
    }

    private static byte[] doubleDigest(byte[] input) {
        return doubleDigest(input, 0, input.length);
    }

    private static byte[] doubleDigest(byte[] input, int offset, int length) {
        synchronized (digest) {
            digest.reset();
            digest.update(input, offset, length);
            byte[] first = digest.digest();
            return digest.digest(first);
        }
    }

    private static byte divmod256(byte[] number58, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number58.length; i++) {
            int digit58 = (int) number58[i] & 0xFF;
            int temp = remainder * 58 + digit58;

            number58[i] = (byte) (temp / 256);

            remainder = temp % 256;
        }
        return (byte) remainder;
    }

    private static byte[] copyOfRange(byte[] source, int from, int to) {
        byte[] range = new byte[to - from];
        System.arraycopy(source, from, range, 0, range.length);
        return range;
    }

    /*
     * Validacao de enderecos bitcoin que começam com "bc1"
     */

    private static int bech32Polymod(int[] values) {
        int chk = 1;
        int top = 0;

        for (int value : values) {
            top = chk >>> 25;
            chk = (chk & 0x1ffffff) << 5 ^ value;
            for (int i = 0; i < 5; i++) {
                if (((top >>> i) & 1) != 0) {
                    chk ^= BECH32_GENERATOR[i];
                }
            }
        }

        return chk;
    }

    private static int[] bech32ExpandData(String valueString) {
        char[] value = valueString.toCharArray();
        int[] data = new int[value.length + 2];
        
        data[0] = 3;
        data[1] = 3;
        data[2] = 0;
        data[3] = 2;
        data[4] = 3;
        
        for (int i = 0; i < value.length - 3; i++) {
            data[i + 5] = BECH32_CHARSET_REV[(int)value[i+3]];
        }
        
        return data;
    }

    private static boolean bech32VerifyChecksum(String value) {
        return bech32Polymod(bech32ExpandData(value)) == 1;
    }

}
