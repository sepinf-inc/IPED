package br.gov.pf.iped.regex.swift;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SwiftCodeService {

  private static final int LINES_TO_IGNORE = 1;

  private Map<String, SwiftCountry> countryCache = new HashMap<>();

  public SwiftCountry loadCountry(String countryCode) throws IOException {

    URL resource = this.getClass().getClassLoader().getResource("swift" + File.separator + countryCode + ".csv");
    if (resource != null) {
      SwiftCountry swiftCountry = new SwiftCountry(countryCode);
      try (BufferedReader in = new BufferedReader(new InputStreamReader(resource.openStream()))) {

        int lines = 0;
        String line = null;
        while ((line = in.readLine()) != null) {
          if (lines >= LINES_TO_IGNORE) {
            line = line.trim();
            if (!line.isEmpty()) {
              String[] split = line.split(",");
              String code = split[0];
              SwiftCode swiftCode = new SwiftCode(code);
              swiftCountry.add(swiftCode);
            }
          }
          lines++;
        }

      }

      return swiftCountry;
    } else {
      return null;
    }
  }

  public SwiftCode getSwiftCode(String code) {
    SwiftCode swiftCode = null;
    if (code.length() >= 6) {
      String countryCode = code.substring(4, 6);
      SwiftCountry country = getCountry(countryCode);
      if (country != null) {
        swiftCode = country.get(code);
      }
    } else {
      swiftCode = null;
    }
    return swiftCode;
  }

  public SwiftCountry getCountry(String countryCode) {
    synchronized (countryCache) {
      SwiftCountry swiftCountry = null;

      if (countryCache.containsKey(countryCode)) {
        swiftCountry = countryCache.get(countryCode);
      } else {
        try {
          swiftCountry = loadCountry(countryCode);
          countryCache.put(countryCode, swiftCountry);
        } catch (IOException e) {
          swiftCountry = null;
        }
      }

      return swiftCountry;
    }

  }

}
