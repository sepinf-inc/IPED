package br.gov.pf.labld.iped.swift;

import java.util.HashMap;
import java.util.Map;

public class SwiftCountry {

  private String countryCode;
  private Map<String, SwiftCode> codes = new HashMap<>();

  public SwiftCountry(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void add(SwiftCode swiftCode) {
    this.codes.put(swiftCode.getCode(), swiftCode);
  }

  public SwiftCode get(String code) {
    return this.codes.get(code);
  }

}
