package iped.engine.task;

import java.io.File;

public class PythonTask extends AbstractPythonTask {

    public PythonTask(File scriptFile) {
        this.scriptFile = scriptFile;
        sendToNextTaskExists = false;
    }

}
