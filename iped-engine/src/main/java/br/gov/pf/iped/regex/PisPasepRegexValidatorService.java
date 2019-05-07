package br.gov.pf.iped.regex;

import java.util.Arrays;
import java.util.List;

public class PisPasepRegexValidatorService extends AbstractDocRegexValidatorService {

    private static final String REGEX_NAME = "PISPASEP";
    private static final int[] PIS_PASEP_WEIGHTS = { 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

    @Override
    public String format(String pisPasep) {
        pisPasep = NON_DIGIT.matcher(pisPasep).replaceAll("");

        StringBuilder builder = new StringBuilder(15);

        builder.append(pisPasep.substring(0, 3)).append(".");
        builder.append(pisPasep.substring(3, 8)).append(".");
        builder.append(pisPasep.substring(8, 10)).append("-");
        builder.append(pisPasep.substring(10, 11));

        return builder.toString();
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    @Override
    protected int getNumVerifiers() {
        return 1;
    }

    @Override
    protected int getAcceptableLength() {
        return 11;
    }

    @Override
    protected int[] getWeights() {
        return PIS_PASEP_WEIGHTS;
    }

}
