package iped.engine.task;

public class ScriptTaskComplianceException extends Exception {
    public ScriptTaskComplianceException(Exception e) {
        super(e);
    }

    public ScriptTaskComplianceException(String string) {
        super(string);
    }

}
