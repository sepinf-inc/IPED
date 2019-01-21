package dpf.sp.gpinf.indexer.process.task.regex;

public abstract class BasicRegexValidatorService implements RegexValidatorService {

  @Override
  public String format(String hit) {
    return hit;
  }

}
