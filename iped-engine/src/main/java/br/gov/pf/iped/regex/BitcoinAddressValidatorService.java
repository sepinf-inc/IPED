package br.gov.pf.iped.regex;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class BitcoinAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final Base58CheckedValidator base58validator = new Base58CheckedValidator(
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray());

    private static int[] BECH32_CHARSET_REV = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, 15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13, 25, 9, 8, 23,
            -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13,
            25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1 };

    private static int[] BECH32_GENERATOR = { 0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3 };

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRIPTOCOIN_BITCOIN_ADDRESS");
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
        byte[] tmp = base58validator.decodeChecked(address);
        return tmp[0] & 0xFF;
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
            data[i + 5] = BECH32_CHARSET_REV[(int) value[i + 3]];
        }

        return data;
    }

    private static boolean bech32VerifyChecksum(String value) {
        return bech32Polymod(bech32ExpandData(value)) == 1;
    }

}
