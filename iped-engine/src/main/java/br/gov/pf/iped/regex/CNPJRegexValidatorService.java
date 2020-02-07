package br.gov.pf.iped.regex;

import java.util.Arrays;
import java.util.List;

public class CNPJRegexValidatorService extends AbstractDocRegexValidatorService {

    private static final String REGEX_NAME = "CNPJ";
    private static final int[] CNPJ_WEIGHTS = { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

    @Override
    public String format(String cnpj) {
        cnpj = NON_DIGIT.matcher(cnpj).replaceAll("");

        StringBuilder builder = new StringBuilder(18);

        builder.append(cnpj.substring(0, 2)).append(".");
        builder.append(cnpj.substring(2, 5)).append(".");
        builder.append(cnpj.substring(5, 8)).append("/");
        builder.append(cnpj.substring(8, 12)).append("-");
        builder.append(cnpj.substring(12, 14));

        return builder.toString();
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    @Override
    protected int getAcceptableLength() {
        return 14;
    }

    @Override
    protected int[] getWeights() {
        return CNPJ_WEIGHTS;
    }

    @Override
    protected int getNumVerifiers() {
        return 2;
    }

}
