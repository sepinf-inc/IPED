package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class RIFRegexValidatorService extends BasicAbstractRegexValidatorService {

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
  public List<String> getRegexNames() {
    return Arrays.asList(REGEX_NAME);
  }

    @Override
    public void init(File confDir) {
        // TODO Auto-generated method stub
        
    }

}