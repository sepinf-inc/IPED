package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class PlacaRegexValidatorService extends BasicAbstractRegexValidatorService {

    protected static final Pattern NON_WORD = Pattern.compile("\\W");

    private static final String[] REGEX_NAME = { "CAR", "PLACA" };

    @Override
    public boolean validate(String hit) {
        return true;
    }

    @Override
    public String format(String hit) {
        hit = NON_WORD.matcher(hit).replaceAll("");
        hit = hit.toUpperCase();
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
