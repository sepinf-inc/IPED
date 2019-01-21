package br.gov.pf.labld.iped.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoletoRegexValidatorServiceTest {

  private BoletoRegexValidatorService service = new BoletoRegexValidatorService();

  @Test
  public void testValidBoleto() {
    String boleto = "00190000090302122300740963035171576320000010249";
    assertTrue(service.validate(boleto));
  }

  @Test
  public void testInvalidBoleto() {
    String boleto = "00190000090302122300740963035171576320000010111";
    assertFalse(service.validate(boleto));
  }

  @Test
  public void testFormatBoleto() {
    String boleto = "00190000090302122300740963035171576320000010249";
    assertEquals("00190.00009 03021.223007 40963.035171 5 76320000010249", service.format(boleto));
  }

}
