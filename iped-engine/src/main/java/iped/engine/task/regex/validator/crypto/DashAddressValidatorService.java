package iped.engine.task.regex.validator.crypto;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import iped.engine.task.regex.BasicAbstractRegexValidatorService;

public class DashAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final AltcoinBase58CheckValidator validator;
    
    static {
        validator = new AltcoinBase58CheckValidator();
        validator.setVersionForPrefix("7", (byte) 16);
        validator.setVersionForPrefix("X", (byte) 76);
    }

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRYPTOCOIN_DASH");
    }

    @Override
    protected boolean validate(String hit) {
        return validator.validate(hit);
    }
}
