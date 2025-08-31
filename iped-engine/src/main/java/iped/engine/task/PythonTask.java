package iped.engine.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.data.CaseData;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.parsers.python.PythonParser;
import iped.utils.ImageUtil;
import jep.Jep;
import jep.JepException;
import jep.NDArray;

public class PythonTask extends AbstractTask {

    private static final String JEP_NOT_FOUND = PythonParser.JEP_NOT_FOUND;
    private static final String DISABLED = PythonParser.DISABLED;
    private static final String SEE_MANUAL = PythonParser.SEE_MANUAL;

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonTask.class);
    private static Map<File, JepException> jepExceptionPerScript = new ConcurrentHashMap<>();
    private static volatile File lastInstalledScript;
    private static volatile IPEDSource ipedCase;
    private static volatile int numInstances = 0;

    private Map<Long, Boolean> scriptLoaded = new ConcurrentHashMap<>();

    private List<String> globals = Collections.synchronizedList(new ArrayList<>());
    private File scriptFile;
    private String moduleName;
    private Boolean processQueueEnd;
    private boolean isEnabled = true;
    private boolean sendToNextTaskExists = true;
    private boolean throwExceptionInsteadOfLogging = false;

    public PythonTask(File scriptFile) {
        this.scriptFile = scriptFile;
    }

    public void setThrowExceptionInsteadOfLogging(boolean value) {
        this.throwExceptionInsteadOfLogging = value;
    }

    public void setCaseData(CaseData caseData) {
        super.caseData = caseData;
    }

    private class ArrayConverter {
        public NDArray<?> getNDArray(byte[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(int[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(long[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(float[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(double[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(boolean[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(short[] array) {
            return new NDArray(array);
        }

        public NDArray<?> getNDArray(char[] array) {
            return new NDArray(array);
        }
    }

    private Jep getJep() throws JepException {
        return getJep(true);
    }

    private Jep getJep(boolean init) throws JepException {
        Jep jep = PythonParser.getJep();
        long threadId = Thread.currentThread().getId();
        if (scriptLoaded.get(threadId) == null) {
            loadScript(jep, init);
            scriptLoaded.put(threadId, true);
        }
        return jep;
    }

    private void setGlobalVars(Jep jep) throws JepException {
        setGlobalVar(jep, "caseData", this.caseData); //$NON-NLS-1$
        setGlobalVar(jep, "moduleDir", this.output); //$NON-NLS-1$
        setGlobalVar(jep, "worker", this.worker); //$NON-NLS-1$
        setGlobalVar(jep, "stats", this.stats); //$NON-NLS-1$
        setGlobalVar(jep, "logger", LOGGER); //$NON-NLS-1$
        setGlobalVar(jep, "javaArray", new ArrayConverter()); //$NON-NLS-1$
        setGlobalVar(jep, "ImageUtil", new ImageUtil()); //$NON-NLS-1$

        LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        setGlobalVar(jep, "numThreads", Integer.valueOf(localConfig.getNumThreads()));
    }

    private void setGlobalVar(Jep jep, String name, Object obj) throws JepException {
        jep.set(name, obj); // $NON-NLS-1$
        globals.add(name);
    }

    private void setModuleVar(Jep jep, String moduleName, String name, Object obj) throws JepException {
        setGlobalVar(jep, name, obj);
        jep.eval(moduleName + "." + name + " = " + name);
    }

    private void loadScript(Jep jep, boolean init) throws JepException {

        if (jep == null) {
            return;
        }

        setGlobalVars(jep);

        jep.eval("import sys");
        jep.eval("sys.path.append('" + scriptFile.getParentFile().getAbsolutePath().replace("\\", "\\\\") + "')");

        String className = scriptFile.getName().replace(".py","");
        moduleName = className;

        jep.eval("import " + moduleName);

        // imports the script responsible for holding the instances per worker globally
        jep.eval("from PythonTaskInstancesHolder import PythonTaskInstancesHolder");
        jep.eval("PythonTaskInstancesHolder = PythonTaskInstancesHolder()");

        // sets logger object
        jep.eval("PythonTaskInstancesHolder.logger = logger");

        int workerId = 0;
        if(this.worker!=null)
            workerId = worker.id;

        for (String global : globals.toArray(new String[0])) {
            if (StringUtils.equals(global, "worker")) { // worker have to be defined in python module instance (see issue #2598)
                //seta uma variavel local no modulo, que deve ser chamada com o self. (ex. self.worker)
                jep.eval("PythonTaskInstancesHolder.getInstance(" + workerId + ", '" + moduleName + "')."  + global + " = " + global);
            } else {
                //seta uma variavel global no modulo
                jep.eval(moduleName + "." + global + " = " + global);
            }
        }
        
        // sets one PythonTask per worker per script
        String taskInstancePerWorker = moduleName + "_javaTaskPerWorker_" + workerId;
        jep.set(taskInstancePerWorker, this);
        jep.eval("PythonTaskInstancesHolder.getInstance(" + workerId + ", '" + moduleName + "').javaTask" + " = " + taskInstancePerWorker);

        if (init) {
            callPythonModuleFunction(jep, "init", ConfigurationManager.get());
        }

        try {
            isEnabled = (Boolean) callPythonModuleFunction(jep, "isEnabled");

        } catch (JepException e) {
            if (e.toString().contains(" has no attribute ")) {
                isEnabled = true;
            } else {
                throw e;
            }
        }
    }

    @Override
    public String getName() {
        return scriptFile.getName();
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        try {
            Jep j = getJep(false);
            List<Configurable<?>> configs = null;
            if (j != null) {
                int workerId = 0;
                if(this.worker!=null)
                    workerId = this.worker.id;
                configs = (List<Configurable<?>>) callPythonModuleFunction(j, "getConfigurables");
            }
            return configs != null ? configs : Collections.emptyList();

        } catch (JepException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        try {
            Jep jep = PythonParser.getJep();
            if (jep == null) {
                isEnabled = false;
                if (throwExceptionInsteadOfLogging) {
                    throw new Exception(JEP_NOT_FOUND + SEE_MANUAL);
                }
            } else {
                loadScript(jep, true);
            }
        } catch (JepException e) {
            String msg = e.getMessage() + ". " + scriptFile.getName() + DISABLED + SEE_MANUAL;
            if (throwExceptionInsteadOfLogging) {
                throw new Exception(msg);
            }
            if (jepExceptionPerScript.get(scriptFile) == null) {
                LOGGER.error(msg);
                e.printStackTrace();
                jepExceptionPerScript.put(scriptFile, e);
            }
            isEnabled = false;
        }
        lastInstalledScript = scriptFile;
        numInstances++;
    }

    @Override
    public void finish() throws Exception {

        if (ipedCase == null) {
            ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer);
        }

        if (isEnabled) {
            IPEDSearcher searcher = new IPEDSearcher(ipedCase);
            setModuleVar(getJep(), moduleName, "ipedCase", ipedCase); //$NON-NLS-1$
            setModuleVar(getJep(), moduleName, "searcher", searcher); //$NON-NLS-1$

            callPythonModuleFunction(getJep(), "finish");
        }

        if (--numInstances == 0) {
            ipedCase.close();
        }

        if (isEnabled && lastInstalledScript.equals(scriptFile)) {
            getJep().close();
        }

    }
    
    public void sendToNextTaskSuper(IItem item) throws Exception {
        super.sendToNextTask(item);
    }
    
    @Override
    protected void sendToNextTask(IItem item) throws Exception {

        if (!isEnabled || !sendToNextTaskExists) {
            super.sendToNextTask(item);
            return;
        }
        try {
            // Pass itself as a parameter to call the sendToNextTaskSuper in the correct instance (see issue #2598)
            callPythonModuleFunction(getJep(), "sendToNextTask", item);
            
        }catch(JepException e) {
            if (e.toString().contains(" has no attribute 'sendToNextTask'")) {
                sendToNextTaskExists = false;
                super.sendToNextTask(item);
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    protected boolean processQueueEnd() {
        if (processQueueEnd == null) {
            try {
                processQueueEnd = (Boolean) callPythonModuleFunction(getJep(), "processQueueEnd");
            } catch (JepException e) {
                processQueueEnd = false;
            }
        }
        return processQueueEnd;
    }

    @Override
    public void process(IItem item) throws Exception {

        try {
            callPythonModuleFunction(getJep(), "process", item);

        } catch (JepException e) {
            LOGGER.warn("Exception from " + getName() + " on " + item.getPath() + ": " + e.toString(), e);
            if (e.toString().toLowerCase().contains("invalid thread access")) {
                throw e;
            }
        }
    }

    // This method is used to call a method in the script instance using the  
    // PythonTaskInstancesHolder utility script to call the correct instance
    private Object callPythonModuleFunction(Jep jep, String strFunctionName, Object... args) throws JepException {
        int workerId = 0;
        if(this.worker!=null)
            workerId = this.worker.id;
            
        if(args!=null && args.length == 1)
            return jep.invoke("PythonTaskInstancesHolder.callFunction", workerId, this.moduleName, strFunctionName, args[0]);
        else if(args!=null && args.length == 2)
            return jep.invoke("PythonTaskInstancesHolder.callFunction", workerId, this.moduleName, strFunctionName, args[0], args[1]);
        
        return jep.invoke("PythonTaskInstancesHolder.callFunction", workerId, this.moduleName, strFunctionName);
    }

}
