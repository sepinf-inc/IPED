package br.gov.pf.iped.regex;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;
import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class TelefoneRegexValidatorService extends BasicAbstractRegexValidatorService {

    private static final String[] REGEX_NAME = { "PHONE", "TELEFONE" };
    private Pattern NOT_ALLOWED_CHARS = Pattern.compile("[^0-9\\-\\+]");
    private static final String REPLACEMENT = "";

    @Override
    public boolean validate(String hit) {
        hit = extractNotAllowedChars(hit);
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            // TODO externalize phone region
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
    public List<String> getRegexNames() {
        return Arrays.asList(REGEX_NAME);
    }

    @Override
    public void init(File confDir) {
        // TODO Auto-generated method stub

    }

}
