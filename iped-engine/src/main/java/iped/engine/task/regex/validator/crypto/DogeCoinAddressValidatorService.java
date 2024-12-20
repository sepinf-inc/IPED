package iped.engine.task.regex.validator.crypto;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import iped.engine.task.regex.BasicAbstractRegexValidatorService;

public class DogeCoinAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final AltcoinBase58CheckValidator validator;
    
    static {
        validator = new AltcoinBase58CheckValidator();
        validator.setVersionForPrefix("D", (byte) 30);
        validator.setVersionForPrefix("A", (byte) 22);
        validator.setVersionForPrefix("9", (byte) 22);
    }

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRYPTOCOIN_DOGECOIN");
    }

    @Override
    protected boolean validate(String hit) {
        return validator.validate(hit);
    }
}
