package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class CNHValidatorService extends BasicAbstractRegexValidatorService {

  private static final String REGEX_NAME = "CNH";

  protected static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

  @Override
  public void init(File confDir) {
    // Nothing to do.
  }

  @Override
  public boolean validate(String cnh) {
    cnh = NON_DIGIT.matcher(cnh).replaceAll("");
    if (isRepeated(cnh.substring(0, cnh.length() - 2))) {
      return false;
    }

    int sum = 0;
    int digit1;
    int digit2;
    int incDigit2 = 0;

    for (int index = 0, weight = 9, digit; index < 9; index++, weight--) {
      digit = Integer.parseInt(cnh.substring(index, index + 1));
      sum += digit * weight;
    }

    sum = sum % 11;

    if (sum > 9) {
      digit1 = 0;
      incDigit2 = 2;
    } else
      digit1 = sum;

    sum = 0;
    for (int index = 0, weight = 1, digit; index < 9; index++, weight++) {
      digit = Integer.parseInt(cnh.substring(index, index + 1));
      sum += digit * weight;
    }

    sum = sum % 11;

    if (sum > 9)
      digit2 = 0;
    else
      digit2 = sum - incDigit2;

    return cnh.equals(cnh.substring(0, 9) + digit1 + digit2);
  }

  private static final boolean isRepeated(final String str) {
    for (char c : str.toCharArray()) {
      if (c != str.charAt(0)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String format(String hit) {
    return NON_DIGIT.matcher(hit).replaceAll("");
  }

  @Override
  public List<String> getRegexNames() {
    return Arrays.asList(REGEX_NAME);
  }

}
