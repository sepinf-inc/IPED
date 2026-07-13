package iped.engine.task.regex.validator.crypto;

import iped.engine.task.regex.BasicAbstractRegexValidatorService;

public class TronAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final AltcoinBase58CheckValidator validator = new AltcoinBase58CheckValidator();

    static {
        // Tron mainnet addresses start with 'T' and version byte 0x41
        validator.setVersionForPrefix("T", (byte) 0x41);
    }

    @Override
    public void init(java.io.File confDir) {
        // Nothing to do.
    }

    @Override
    public java.util.List<String> getRegexNames() {
        return java.util.Arrays.asList("CRYPTOCOIN_TRON");
    }

    @Override
    protected boolean validate(String hit) {
        return validateTronAddress(hit);
    }

    public boolean validateTronAddress(String addr) {
        return validator.validate(addr);
    }

}
