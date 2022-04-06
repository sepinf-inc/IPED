package br.gov.pf.iped.regex;

import java.util.Arrays;
import java.util.List;

public class CPFRegexValidatorService extends AbstractDocRegexValidatorService {

    private static final String REGEX_NAME = "BR_CPF";

    static final int[] CPF_WEIGHTS = { 11, 10, 9, 8, 7, 6, 5, 4, 3, 2 };

    @Override
    public String format(String cpf) {
        cpf = NON_DIGIT.matcher(cpf).replaceAll("");

        StringBuilder builder = new StringBuilder(15);

        builder.append(cpf.substring(0, 3)).append(".");
        builder.append(cpf.substring(3, 6)).append(".");
        builder.append(cpf.substring(6, 9)).append("-");
        builder.append(cpf.substring(9, 11));

        return builder.toString();
    }

    @Override
    protected int getAcceptableLength() {
        return 11;
    }

    @Override
    protected int[] getWeights() {
        return CPF_WEIGHTS;
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    @Override
    protected int getNumVerifiers() {
        return 2;
    }

}