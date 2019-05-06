package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class TituloEleitorRegexValidatorService extends BasicAbstractRegexValidatorService {

  private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

  private static final String REGEX_NAME = "TITULO_ELEITOR";

  @Override
  public void init(File confDir) {
    // Nothing to do.
  }

  @Override
  public boolean validate(String titulo) {

    if (isRepeated(titulo.substring(0, titulo.length() - 2))) {
      return false;
    }

    int sum = 0;
    int digit1;
    int digit2;

    // Calcula primeiro digito verificador
    for (int index = 0, weight = 2, digit; index <= 7; index++, weight++) {
      digit = Integer.parseInt(titulo.substring(index, index + 1));
      sum += digit * weight;
    }

    sum = sum % 11;

    if (sum > 9) {
      digit1 = 0;
    } else {
      digit1 = sum;
    }

    // Calcula segundo digito verificador
    sum = 0;
    for (int index = 8, weight = 7, digit; index <= 10; index++, weight++) {
      digit = Integer.parseInt(titulo.substring(index, index + 1));
      sum += digit * weight;
    }

    sum = sum % 11;

    if (sum > 9) {
      digit2 = 0;
    } else {
      digit2 = sum;
    }

    return titulo.equals(titulo.substring(0, 10) + digit1 + digit2);
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
