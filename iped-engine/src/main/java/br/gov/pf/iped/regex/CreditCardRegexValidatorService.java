package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class CreditCardRegexValidatorService extends BasicAbstractRegexValidatorService {

  protected static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

  private static final String REGEX_NAME = "CREDIT_CARD";

  @Override
  public void init(File confDir) {
    // Nothing to do.
  }

  @Override
  public boolean validate(String cc) {
    cc = NON_DIGIT.matcher(cc).replaceAll("");
    if (isRepeated(cc)) {
      return false;
    }
    int sum = 0;
    boolean odd = false;
    for (int index = cc.length() - 1; index >= 0; index--) {
      int n = Integer.parseInt(cc.substring(index, index + 1));
      if (odd) {
        n *= 2;
        if (n > 9) {
          n = (n % 10) + 1;
        }
      }
      sum += n;
      odd = !odd;
    }
    return (sum % 10 == 0);
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
    hit = NON_DIGIT.matcher(hit).replaceAll("");
    return hit;
  }

  @Override
  public List<String> getRegexNames() {
    return Arrays.asList(REGEX_NAME);
  }

}
