package br.gov.pf.labld.iped.regex;

import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class FacebookContactValidatorService implements RegexValidatorService {

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
  public String getRegexName() {
    return REGEX_NAME;
  }

}
