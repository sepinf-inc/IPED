package br.gov.pf.labld.iped.swift;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;

public class SwiftCodeServiceTest {

  private SwiftCodeService service = new SwiftCodeService();

  @Test
  public void testLoadCountryCode() throws IOException {
    SwiftCountry swiftCountry = service.loadCountry("AD");

    assertNotNull(swiftCountry);
    assertEquals("AD", swiftCountry.getCountryCode());

    swiftCountry = service.loadCountry("AE");

    assertNotNull(swiftCountry);
    assertEquals("AE", swiftCountry.getCountryCode());

    swiftCountry = service.loadCountry("XX");

    assertNull(swiftCountry);
  }

  @Test
  public void testLoadCountryList() throws IOException {
    SwiftCountry swiftCountry = service.loadCountry("AD");

    assertEquals("BACAADAD", swiftCountry.get("BACAADAD").getCode());
    assertEquals("VALBADAD", swiftCountry.get("VALBADAD").getCode());
  }

  @Test
  public void testGetSwiftCode() {
    SwiftCode swiftCode = service.getSwiftCode("BACAADAD");
    assertNotNull(swiftCode);
    assertEquals("BACAADAD", swiftCode.getCode());
  }

}
