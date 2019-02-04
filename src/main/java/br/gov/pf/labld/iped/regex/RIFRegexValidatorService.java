package br.gov.pf.labld.iped.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class RIFRegexValidatorService implements RegexValidatorService {

  private static final String REPLACEMENT = " ";

  private static final String REGEX_NAME = "RIF";

  private final Pattern NOT_ALLOWED_CHARS_PATTERN = Pattern.compile("[^RIF0-9]+");

  @Override
  public boolean validate(String hit) {
    return true;
  }

  @Override
  public String format(String hit) {
    Matcher matcher = NOT_ALLOWED_CHARS_PATTERN.matcher(hit);
    return matcher.replaceAll(REPLACEMENT).trim();
  }

  @Override
  public String getRegexName() {
    return REGEX_NAME;
  }

}