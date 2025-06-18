package iped.engine.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.data.CaseData;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.task.index.IndexItem.KnnVector;
import iped.parsers.python.PythonParser;
import iped.utils.ImageUtil;
import jep.Jep;
import jep.JepException;

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
    private Map<Long, String> instanceName = new ConcurrentHashMap<>();

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

    public class Converter {
        public KnnVector toKnnVector(double[] array) {
            return new KnnVector(array);
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
        setGlobalVar(jep, "caseData", this.caseData);
        setGlobalVar(jep, "moduleDir", this.output);
        setGlobalVar(jep, "worker", this.worker);
        setGlobalVar(jep, "stats", this.stats);
        setGlobalVar(jep, "logger", LOGGER);
        setGlobalVar(jep, "javaConverter", new Converter());
        setGlobalVar(jep, "ImageUtil", new ImageUtil());

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

        long threadId = Thread.currentThread().getId();
        String instanceName = className.toLowerCase() + "_thread_" + threadId;
        jep.eval(instanceName + " = " + moduleName + "." + className + "()");
        this.instanceName.put(threadId, instanceName);

        for (String global : globals.toArray(new String[0])) {
            jep.eval(moduleName + "." + global + " = " + global);
        }

        String taskInstancePerThread = moduleName + "_javaTaskPerThread";
        jep.set(taskInstancePerThread, new TaskInstancePerThread(moduleName, this));
        jep.eval(moduleName + ".javaTask" + " = " + taskInstancePerThread);

        if (init) {
            jep.invoke(getInstanceMethod("init"), ConfigurationManager.get());
        }

        try {
            isEnabled = (Boolean) jep.invoke(getInstanceMethod("isEnabled"));

        } catch (JepException e) {
            if (e.toString().contains(" has no attribute ")) {
                isEnabled = true;
            } else {
                throw e;
            }
        }
    }

    public static class TaskInstancePerThread {

        private static Map<String, PythonTask> map = new ConcurrentHashMap<>();
        private String moduleName;

        private TaskInstancePerThread(String moduleName, PythonTask task) {
            this.moduleName = moduleName;
            map.put(moduleName + Thread.currentThread().getId(), task);
        }

        public PythonTask get() {
            return map.get(moduleName + Thread.currentThread().getId());
        }

    }

    private String getInstanceMethod(String function) {
        return this.instanceName.get(Thread.currentThread().getId()) + "." + function;
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
                configs = (List<Configurable<?>>) j.invoke(getInstanceMethod("getConfigurables"));
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

            getJep().invoke(getInstanceMethod("finish")); //$NON-NLS-1$
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
            getJep().invoke(getInstanceMethod("sendToNextTask"), item); //$NON-NLS-1$
            
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
                processQueueEnd = (Boolean) getJep().invoke(getInstanceMethod("processQueueEnd")); //$NON-NLS-1$
            } catch (JepException e) {
                processQueueEnd = false;
            }
        }
        return processQueueEnd;
    }

    @Override
    public void process(IItem item) throws Exception {

        try {
            getJep().invoke(getInstanceMethod("process"), item); //$NON-NLS-1$

        } catch (JepException e) {
            LOGGER.warn("Exception from " + getName() + " on " + item.getPath() + ": " + e.toString(), e);
            if (e.toString().toLowerCase().contains("invalid thread access")) {
                throw e;
            }
        }
    }

}
