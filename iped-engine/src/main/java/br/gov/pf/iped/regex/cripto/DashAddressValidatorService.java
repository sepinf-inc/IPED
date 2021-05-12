package br.gov.pf.iped.regex.cripto;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

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
        return Arrays.asList("CRIPTOCOIN_DASH");
    }

    @Override
    protected boolean validate(String hit) {
        return validator.validate(hit);
    }
}
