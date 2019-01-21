package br.gov.pf.labld.iped.swift;

import java.util.regex.Pattern;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class SwiftCodeRegexValidatorService implements RegexValidatorService {

  protected static final Pattern NON_WORD = Pattern.compile("\\W");

  private static final String REGEX_NAME = "SWIFT";

  private SwiftCodeService service = new SwiftCodeService();

  @Override
  public boolean validate(String code) {
    SwiftCode swiftCode = service.getSwiftCode(code);
    return swiftCode != null;
  }

  @Override
  public String getRegexName() {
    return REGEX_NAME;
  }

  @Override
  public String format(String hit) {
    hit = NON_WORD.matcher(hit).replaceAll("");
    hit = hit.toUpperCase();
    return hit;
  }

}
