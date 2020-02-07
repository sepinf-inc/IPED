package br.gov.pf.iped.regex;

import java.io.File;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public abstract class AbstractDocRegexValidatorService extends BasicAbstractRegexValidatorService {

    protected static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public boolean validate(String doc) {
        if (doc == null) {
            return false;
        }

        doc = NON_DIGIT.matcher(doc).replaceAll("");

        int acceptableLength = getAcceptableLength();
        boolean isLengthValid = doc.length() == acceptableLength;

        if (!isLengthValid) {
            return false;
        }

        boolean repeated = isRepeated(doc.substring(0, doc.length() - 2));
        if (repeated) {
            return false;
        }
        int numVerifiers = getNumVerifiers();
        int numberLength = acceptableLength - numVerifiers;
        String number = doc.substring(0, numberLength);
        String verifiers = doc.substring(numberLength);

        int[] weights = getWeights();

        for (int verifier = 0; verifier < numVerifiers; verifier++) {
            int docDigit = Integer.parseInt(verifiers.substring(verifier, verifier + 1));
            int digit = calcDigit(number, weights);
            number += digit;

            if (docDigit != digit) {
                return false;
            }
        }
        return true;
    }

    private static final boolean isRepeated(final String str) {
        for (char c : str.toCharArray()) {
            if (c != str.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    protected static int calcDigit(String number, int[] weight) {
        int sum = 0;
        for (int index = number.length() - 1; index >= 0; index--) {
            int digit = Integer.parseInt(number.substring(index, index + 1));
            sum += digit * weight[weight.length - number.length() + index];
        }
        sum = 11 - sum % 11;
        return sum > 9 ? 0 : sum;
    }

    protected abstract int getNumVerifiers();

    protected abstract int getAcceptableLength();

    protected abstract int[] getWeights();

}
