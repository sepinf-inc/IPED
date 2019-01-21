package br.gov.pf.labld.iped.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class TelefoneRegexValidatorService implements RegexValidatorService {

  private static final String REGEX_NAME = "TELEFONE";
  private Pattern NOT_ALLOWED_CHARS = Pattern.compile("[^0-9\\-\\+]");
  private static final String REPLACEMENT = "";

  @Override
  public boolean validate(String hit) {
    hit = extractNotAllowedChars(hit);
    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber phoneNumber = phoneUtil.parse(hit, "BR");
      return phoneUtil.isValidNumber(phoneNumber);
    } catch (NumberParseException e) {
    }
    return false;
  }

  private String extractNotAllowedChars(String hit) {
    Matcher matcher = NOT_ALLOWED_CHARS.matcher(hit);
    hit = matcher.replaceAll(REPLACEMENT);
    return hit;
  }

  @Override
  public String format(String hit) {
    hit = extractNotAllowedChars(hit);
    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber phoneNumber = phoneUtil.parse(hit, "BR");
      hit = phoneUtil.format(phoneNumber, PhoneNumberFormat.INTERNATIONAL);
    } catch (NumberParseException e) {
    }
    return hit;
  }

  @Override
  public String getRegexName() {
    return REGEX_NAME;
  }

}
