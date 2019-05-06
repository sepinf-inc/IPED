package br.gov.pf.iped.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CPFRegexValidatorServiceTest {

  private AbstractDocRegexValidatorService service = new CPFRegexValidatorService();

  @Test
  public void testFormatCPF() {
    String cpf = "57042882008";
    String formatted = "570.428.820-08";

    String result = service.format(cpf);
    assertEquals(formatted, result);
  }

  @Test
  public void testPartiallyFormattedCPF() {
    String cpf = "570428820-08";
    String formatted = "570.428.820-08";

    String result = service.format(cpf);
    assertEquals(formatted, result);
  }

  @Test
  public void testNullCPFIsInvalid() {
    String cpf = null;
    assertFalse(service.validate(cpf));
  }

  @Test
  public void testEmptyCPFIsInvalid() {
    String cpf = "";
    assertFalse(service.validate(cpf));
  }

  @Test
  public void testValidCPFButDummy() {
    String cpf = "11111111111";
    assertFalse(service.validate(cpf));
  }

  @Test
  public void testValidCPF() {
    String cpf = "57042882008";
    assertTrue(service.validate(cpf));
  }

  @Test
  public void testValidFormattedCPF() {
    String cpf = "570.428.820-08";
    assertTrue(service.validate(cpf));
  }

  @Test
  public void testValidPartiallyFormattedCPF() {
    String cpf = "570428820-08";
    assertTrue(service.validate(cpf));
  }

  @Test
  public void testInvalidCPF() {
    String cpf = "57042882011";
    assertFalse(service.validate(cpf));
  }

  @Test
  public void testInvalidFormattedCPF() {
    String cpf = "570.428.820-11";
    assertFalse(service.validate(cpf));
  }

  @Test
  public void testInvalidCPFWithLettersIsInvalid() {
    String cpf = "aaa.428.820-11";
    assertFalse(service.validate(cpf));
  }

}
