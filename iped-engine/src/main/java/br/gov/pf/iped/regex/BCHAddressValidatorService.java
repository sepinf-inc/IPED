package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

/**
 * Validate BCH CashAddr format. Note that the legacy format follow the exact
 * same rules as of bitcoin address, so it's not possible to tell the difference
 * (it will be validated as BitcoinAddress)
 * 
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 *
 */
public class BCHAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static long[] BECH32_BITCOIN_CASH_GENERATOR = { 0x98f2bc8e61L, 0x79b76d99e2L, 0xf33e5fb3c4L, 0xae2eabe2a8L,
            0x1e4f43e470L };

    private static int[] BECH32_CHARSET_REV = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, 15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13, 25, 9, 8, 23,
            -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13,
            25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1 };
    
    private static final String BCH_PREFIX = "bitcoincash:";

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRIPTOCOIN_BITCOIN_CASH_CASHADDR");
    }

    @Override
    protected boolean validate(String hit) {
        return cashAddrVerifyChecksum(hit);
    }

    public boolean validateBitcoinCashAddress(String addr) {
        return cashAddrVerifyChecksum(addr);
    }

    private static long bech32Polymod(long[] data) {
        long chk = 1;
        long top = 0;

        for (long value : data) {
            top = chk >>> 35;
            chk = (chk & 0x07ffffffffL) << 5 ^ value;
            for (int i = 0; i < 5; i++) {
                if (((top >>> i) & 1) != 0) {
                    chk ^= BECH32_BITCOIN_CASH_GENERATOR[i];
                }
            }
        }

        return chk ^ 1;
    }

    private static long[] expandData(String value) {
        int colPos = value.indexOf(':');
        long[] data = new long[value.length()];

        for (int i = 0; i < colPos; i++) {
            data[i] = (long) (value.charAt(i) & 0x1f);
        }

        data[colPos] = 0L;

        for (int i = colPos + 1; i < value.length(); i++) {
            data[i] = BECH32_CHARSET_REV[(int) value.charAt(i)];
        }

        return data;
    }

    private static boolean cashAddrVerifyChecksum(String value) {
        if(!value.startsWith(BCH_PREFIX)) {
            value = BCH_PREFIX + value;
        }
        return bech32Polymod(expandData(value)) == 0;
    }

}
