package dpf.sp.gpinf.indexer.process.task.regex;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.task.regex.RegexTask.Regex;

public class RegexValidator {

  private Logger LOGGER = LoggerFactory.getLogger(RegexValidator.class);

  private Map<String, RegexValidatorService> services = new HashMap<>();

  private static final RegexValidator INSTANCE = new RegexValidator();

  private RegexValidator() {
    super();
    init();
  }

  private void init() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    init(cl);
  }

  private void init(ClassLoader classLoader) {
    ServiceLoader<RegexValidatorService> loader = ServiceLoader.load(RegexValidatorService.class, classLoader);
    Iterator<RegexValidatorService> iterator = loader.iterator();
    while (iterator.hasNext()) {
      RegexValidatorService validatorService = iterator.next();
      String regexName = validatorService.getRegexName();

      RegexValidatorService previous = services.put(regexName, validatorService);
      if (previous != null) {

        String first = getLocation(previous);
        String second = getLocation(validatorService);

        throw new IllegalStateException(
            "Multiple validation services registered for " + regexName + " (" + first + " and " + second + ")");
      }

      LOGGER.info("Validator " + validatorService.getClass().getName() + " found for " + regexName);
    }
  }

  public static RegexValidator get() {
    return INSTANCE;
  }

  public boolean validate(Regex regex, String hit) {
    String name = regex.name;
    return validate(name, hit);
  }

  public boolean validate(String name, String hit) {
    RegexValidatorService service = services.get(name);
    if (service != null) {
      return service.validate(hit);
    } else {
      return true;
    }
  }

  public String format(Regex regex, String hit) {
    String name = regex.name;
    return format(name, hit);
  }

  private String format(String name, String hit) {
    RegexValidatorService service = services.get(name);
    if (service != null) {
      return service.format(hit);
    } else {
      return hit;
    }
  }

  private String getLocation(RegexValidatorService service) {
    try {
      URL location = service.getClass().getProtectionDomain().getCodeSource().getLocation();
      return location.toString();
    } catch (SecurityException e) {
      return null;
    }
  }

}
