package iped.engine.task.regex.validator;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import iped.engine.task.regex.BasicAbstractRegexValidatorService;

public class RENAVAMRegexValidatorService extends BasicAbstractRegexValidatorService {

    private static final String REGEX_NAME = "BR_RENAVAM";
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");
    private static final int[] WEIGHTS = { 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };
    private static final int NORMALIZED_LENGTH = 11;

    @Override
    public void init(File confDir) {
    }

    @Override
    public boolean validate(String renavam) {
        if (renavam == null) {
            return false;
        }
        renavam = NON_DIGIT.matcher(renavam).replaceAll("");

        if (renavam.length() < 9 || renavam.length() > NORMALIZED_LENGTH) {
            return false;
        }

        while (renavam.length() < NORMALIZED_LENGTH) {
            renavam = "0" + renavam;
        }

        String base = renavam.substring(0, 10);

        if (isAllSameDigit(base)) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(base.charAt(i)) * WEIGHTS[i];
        }

        int remainder = sum % 11;
        int expectedCheck = (remainder <= 1) ? 0 : (11 - remainder);
        int actualCheck = Character.getNumericValue(renavam.charAt(10));

        return expectedCheck == actualCheck;
    }

    @Override
    public String format(String hit) {
        String digits = NON_DIGIT.matcher(hit).replaceAll("");
        while (digits.length() < NORMALIZED_LENGTH) {
            digits = "0" + digits;
        }
        return digits;
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    private static boolean isAllSameDigit(String s) {
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

}
