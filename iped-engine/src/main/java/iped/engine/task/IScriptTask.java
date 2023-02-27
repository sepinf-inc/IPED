package iped.engine.task;

public interface IScriptTask {
    public String getScriptFileName();
    
    /**
     * Basic validation to the script.
     * 
     * @return suggested class to position this task before in execution pipeline
     * @throws ScriptTaskComplianceException
     */
    public Class<? extends AbstractTask> checkTaskCompliance()throws ScriptTaskComplianceException;        
}
