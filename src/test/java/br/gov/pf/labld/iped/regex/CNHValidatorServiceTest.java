package br.gov.pf.labld.iped.regex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CNHValidatorServiceTest {

  private CNHValidatorService service = new CNHValidatorService();

  @Test
  public void testValidCNH() {
    String cnh = "08355950810";
    assertTrue(service.validate(cnh));
  }

  @Test
  public void testInvalidCNH() {
    String cnh = "08355950811";
    assertFalse(service.validate(cnh));
  }

}
