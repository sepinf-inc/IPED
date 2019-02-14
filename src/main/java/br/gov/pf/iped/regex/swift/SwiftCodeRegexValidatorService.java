package br.gov.pf.iped.regex.swift;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

public class SwiftCodeRegexValidatorService extends BasicAbstractRegexValidatorService {

  protected static final Pattern NON_WORD = Pattern.compile("\\W");

  private static final String REGEX_NAME = "SWIFT";

  private SwiftCodeService service = new SwiftCodeService();

  @Override
  public void init(File confDir) {
    // Nothing to do.
  }

  @Override
  public boolean validate(String code) {
    SwiftCode swiftCode = service.getSwiftCode(code);
    return swiftCode != null;
  }

  @Override
  public List<String> getRegexNames() {
    return Arrays.asList(REGEX_NAME);
  }

  @Override
  public String format(String hit) {
    hit = NON_WORD.matcher(hit).replaceAll("");
    hit = hit.toUpperCase();
    return hit;
  }

}
