package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class BitcoinAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final AltcoinBase58CheckValidator validator;

    static {
        validator = new AltcoinBase58CheckValidator();
        validator.setVersionForPrefix("1", (byte) 0);
        validator.setVersionForPrefix("3", (byte) 5);
        validator.setVersionForPrefix("5", (byte) 128);
        validator.setVersionForPrefix("K", (byte) 128);
        validator.setVersionForPrefix("L", (byte) 128);
        validator.setVersionForPrefix("xpub", (byte) 4, (byte) 136, (byte) 178, (byte) 30);
        validator.setVersionForPrefix("xpub", (byte) 4, (byte) 136, (byte) 173, (byte) 228);
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
        return Arrays.asList("CRIPTOCOIN_BITCOIN_ADDRESS", "CRIPTOCOIN_BITCOIN_BIP38_ENC_PRIV_K",
                "CRIPTOCOIN_BITCOIN_WIF_PRIV_K_UNC_PUB_K", "CRIPTOCOIN_BITCOIN_WIF_PRIV_K_COMP_PUB_K",
                "CRIPTOCOIN_BITCOIN_BIP32_HD_XPRV_KEY", "CRIPTOCOIN_BITCOIN_BIP32_HD_XPUB_KEY");
    }

    @Override
    protected boolean validate(String hit) {
        return validateBitcoinAddress(hit);
    }

    public boolean validateBitcoinAddress(String addr) {
        if (addr.startsWith("bc1")) {
            return bech32validator.bech32VerifyChecksum(addr);
        }

        return validator.validate(addr);
    }

}
