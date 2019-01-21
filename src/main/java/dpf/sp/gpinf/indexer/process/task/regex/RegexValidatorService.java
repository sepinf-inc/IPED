package dpf.sp.gpinf.indexer.process.task.regex;

public interface RegexValidatorService {

  boolean validate(String hit);

  String format(String hit);
  
  String getRegexName();

}
