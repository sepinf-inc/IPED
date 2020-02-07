package dpf.sp.gpinf.indexer.process.task.regex;

import java.io.File;
import java.util.List;

public interface RegexValidatorService {

    void init(File confDir);

    boolean validate(String regexName, String hit);

    String format(String regexName, String hit);

    List<String> getRegexNames();

}
