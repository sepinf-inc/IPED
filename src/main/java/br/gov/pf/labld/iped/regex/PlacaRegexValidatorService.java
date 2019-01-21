package br.gov.pf.labld.iped.regex;

import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class PlacaRegexValidatorService implements RegexValidatorService {

  protected static final Pattern NON_WORD = Pattern.compile("\\W");

  private static final String REGEX_NAME = "PLACA";

  @Override
  public boolean validate(String hit) {
    return true;
  }

  @Override
  public String format(String hit) {
    hit = NON_WORD.matcher(hit).replaceAll("");
    hit = hit.toUpperCase();
    return hit;
  }

  @Override
  public String getRegexName() {
    return REGEX_NAME;
  }

}
