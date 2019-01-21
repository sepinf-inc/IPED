package br.gov.pf.labld.iped.regex;

import java.util.regex.Pattern;

public class BoletoRegexValidatorService extends AbstractDocRegexValidatorService {

  protected static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

  private static final String REGEX_NAME = "BOLETO";

  private static final int[] WEIGHTS = { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4,
      3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

  @Override
  public String format(String boleto) {
    boleto = NON_DIGIT.matcher(boleto).replaceAll("");

    StringBuilder builder = new StringBuilder();

    builder.append(boleto.substring(0, 5)).append(".");
    builder.append(boleto.substring(5, 10)).append(" ");
    builder.append(boleto.substring(10, 15)).append(".");
    builder.append(boleto.substring(15, 21)).append(" ");
    builder.append(boleto.substring(21, 26)).append(".");
    builder.append(boleto.substring(26, 32)).append(" ");
    builder.append(boleto.substring(32, 33)).append(" ");
    builder.append(boleto.substring(33, boleto.length()));

    return builder.toString();
  }

  @Override
  public boolean validate(String boleto) {

    boleto = NON_DIGIT.matcher(boleto).replaceAll("");
    if (isRepeated(boleto))
      return false;

    int digito1 = calcBoletoDigit(boleto.substring(0, 9));
    int digito2 = calcBoletoDigit(boleto.substring(10, 20));
    int digito3 = calcBoletoDigit(boleto.substring(21, 31));

    int digito4 = calcDigit(boleto.substring(0, 4) + boleto.substring(33, 47) + boleto.substring(4, 9)
        + boleto.substring(10, 11) + boleto.substring(11, 20) + boleto.substring(21, 31), WEIGHTS);

    return boleto.equalsIgnoreCase(boleto.substring(0, 9) + digito1 + boleto.substring(10, 20) + digito2
        + boleto.substring(21, 31) + digito3 + (digito4 == 0 ? 1 : digito4) + boleto.substring(33, 47));
  }

  @Override
  public String getRegexName() {
    return REGEX_NAME;
  }

  private static final boolean isRepeated(final String str) {
    for (char c : str.toCharArray()) {
      if (c != str.charAt(0)) {
        return false;
      }
    }
    return true;
  }

  private static int calcBoletoDigit(String field) {
    int sum = 0;
    int multiplier = 2;
    int partial;

    for (int index = field.length() - 1, digit; index >= 0; index--) {
      digit = Integer.parseInt(field.substring(index, index + 1));
      partial = digit * multiplier;
      if (partial > 9) {
        sum += Integer.parseInt(Integer.toString(partial).substring(0, 1))
            + Integer.parseInt(Integer.toString(partial).substring(1, 2));
      } else {
        sum += partial;
      }
      if (multiplier == 2) {
        multiplier = 1;
      } else {
        multiplier = 2;
      }
    }

    sum = 10 - sum % 10;
    return sum > 9 ? 0 : sum;
  }

  @Override
  protected int getNumVerifiers() {
    return 1;
  }

  @Override
  protected int getAcceptableLength() {
    return 48;
  }

  @Override
  protected int[] getWeights() {
    return WEIGHTS;
  }

}
