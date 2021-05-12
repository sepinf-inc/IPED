package br.gov.pf.iped.regex.cripto;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class LiteCoinAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final AltcoinBase58CheckValidator validator;
    
    static {
        validator = new AltcoinBase58CheckValidator();
        validator.setVersionForPrefix("M", (byte) 50);
        validator.setVersionForPrefix("L", (byte) 48);
        validator.setVersionForPrefix("9", (byte) 22);
    }
    
    private static int[] BECH32_CHARSET_REV = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, 15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13, 25, 9, 8, 23,
            -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1, -1, 29, -1, 24, 13,
            25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1 };

    private static int[] BECH32_GENERATOR = { 0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3 };

    private static Bech32AddressValidator bech32validator = new Bech32AddressValidator(BECH32_CHARSET_REV,
            BECH32_GENERATOR);

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRIPTOCOIN_DOGECOIN");
    }

    @Override
    protected boolean validate(String hit) {
        if (hit.startsWith("ltc1")) {
            return bech32validator.bech32VerifyChecksum(hit);
        }
        return validator.validate(hit);
    }
}
