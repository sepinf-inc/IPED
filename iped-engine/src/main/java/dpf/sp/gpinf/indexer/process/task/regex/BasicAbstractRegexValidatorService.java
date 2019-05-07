package dpf.sp.gpinf.indexer.process.task.regex;

public abstract class BasicAbstractRegexValidatorService implements RegexValidatorService {

    @Override
    public boolean validate(String regexName, String hit) {
        return validate(hit);
    }

    @Override
    public String format(String regexName, String hit) {
        return format(hit);
    }

    public String format(String hit) {
        return hit;
    }

    protected abstract boolean validate(String hit);

}
