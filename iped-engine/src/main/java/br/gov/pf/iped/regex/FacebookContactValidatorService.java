package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class FacebookContactValidatorService extends BasicAbstractRegexValidatorService {

    protected static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    private static final String REGEX_NAME = "FACEBOOK";

    @Override
    public boolean validate(String hit) {
        return true;
    }

    @Override
    public String format(String hit) {
        hit = NON_DIGIT.matcher(hit).replaceAll("");
        return hit;
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    @Override
    public void init(File confDir) {
        // TODO Auto-generated method stub

    }

}
