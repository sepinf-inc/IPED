package br.gov.pf.iped.regex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FacebookContactValidatorServiceTest {

  private FacebookContactValidatorService service = new FacebookContactValidatorService();

  @Test
  public void testFacebookUserIdFormat1() {
    String hit = "ufed:UserID: 1102565553";
    assertEquals("1102565553", service.format(hit));
  }

  @Test
  public void testFacebookUserIdFormat2() {
    String hit = "ufed:UserID: 100014843058555";
    assertEquals("100014843058555", service.format(hit));
  }

}
