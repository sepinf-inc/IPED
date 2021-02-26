package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import iped3.IItem;
import jep.Jep;
import jep.JepException;
import jep.NDArray;
import jep.SharedInterpreter;

public class PythonTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonTask.class);
    private static AtomicBoolean jepChecked = new AtomicBoolean();
    private static final Map<Integer, Jep> jepPerWorker = new HashMap<>();
    private static volatile File lastInstalledScript;

    private ArrayList<String> globals = new ArrayList<>();
    private File scriptFile;
    private String moduleName;
    private Properties confParams;
    private File confDir;
    private Boolean processQueueEnd;
    private boolean isEnabled = true;
    private boolean scriptLoaded = false;

    public PythonTask(File scriptFile) {
        this.scriptFile = scriptFile;
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
        synchronized (this.getClass()) {
            Jep jep = jepPerWorker.get(this.worker.id);
            if (jep == null) {
                jep = getNewJep();
                jepPerWorker.put(this.worker.id, jep);
            }
            if (!scriptLoaded) {
                loadScript(jep);
                scriptLoaded = true;
            }
            return jep;
        }
    }

    private Jep getNewJep() throws JepException {

        Jep jep;
        try {
            jep = new SharedInterpreter();

        } catch (UnsatisfiedLinkError e) {
            if (!jepChecked.getAndSet(true)) {
                LOGGER.error(
                        "JEP not found, all python modules will be disabled. If you want to enable them see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules");
                e.printStackTrace();
            }
            isEnabled = false;
            return null;
        }

        jep.eval("from jep import redirect_streams");
        jep.eval("redirect_streams.setup()");

        setGlobalVar(jep, "caseData", this.caseData); //$NON-NLS-1$
        setGlobalVar(jep, "moduleDir", this.output); //$NON-NLS-1$
        setGlobalVar(jep, "worker", this.worker); //$NON-NLS-1$
        setGlobalVar(jep, "stats", this.stats); //$NON-NLS-1$
        setGlobalVar(jep, "logger", LOGGER); //$NON-NLS-1$
        setGlobalVar(jep, "javaArray", new ArrayConverter()); //$NON-NLS-1$
        setGlobalVar(jep, "javaTask", this); //$NON-NLS-1$
        setGlobalVar(jep, "ImageUtil", new ImageUtil()); //$NON-NLS-1$

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        setGlobalVar(jep, "numThreads", Integer.valueOf(localConfig.getNumThreads()));

        return jep;
    }

    private void setGlobalVar(Jep jep, String name, Object obj) throws JepException {
        jep.set(name, obj); // $NON-NLS-1$
        globals.add(name);
    }

    private void setModuleVar(Jep jep, String moduleName, String name, Object obj) throws JepException {
        setGlobalVar(jep, name, obj);
        jep.eval(moduleName + "." + name + " = " + name);
    }

    private void loadScript(Jep jep) throws JepException {

        moduleName = scriptFile.getName().replace(".py", "");

        jep.eval("import sys");
        try {
            jep.eval("sys.path.append('" + scriptFile.getParentFile().getCanonicalPath().replace("\\", "/") + "')");
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
        jep.eval("import " + moduleName);

        for (String global : globals) {
            jep.eval(moduleName + "." + global + " = " + global);
        }

        jep.invoke(getModuleFunction("init"), confParams, confDir);

        try {
            isEnabled = (Boolean) jep.invoke(getModuleFunction("isEnabled"));

        } catch (JepException e) {
            if (e.toString().contains(" has no attribute ")) {
                isEnabled = true;
            } else {
                throw e;
            }
        }
    }

    private String getModuleFunction(String function) {
        return moduleName + "." + function;
    }

    @Override
    public String getName() {
        return scriptFile.getName();
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        this.confParams = confParams;
        this.confDir = confDir;
        try (Jep jep = getNewJep()) {
            loadScript(jep);
        }
        lastInstalledScript = scriptFile;
    }

    @Override
    public void finish() throws Exception {

        if (isEnabled) {
            try (IPEDSource ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer)) {
                IPEDSearcher searcher = new IPEDSearcher(ipedCase);
                setModuleVar(getJep(), moduleName, "ipedCase", ipedCase); //$NON-NLS-1$
                setModuleVar(getJep(), moduleName, "searcher", searcher); //$NON-NLS-1$

                getJep().invoke(getModuleFunction("finish")); //$NON-NLS-1$
            }
        }

        if (lastInstalledScript.equals(scriptFile)) {
            getJep().close();
        }

    }
    
    public void sendToNextTaskSuper(IItem item) throws Exception {
        super.sendToNextTask(item);
    }
    
    private boolean methodExists = true;
    
    @Override
    protected void sendToNextTask(IItem item) throws Exception {

        if (!isEnabled || !methodExists) {
            super.sendToNextTask(item);
            return;
        }
        try {
            getJep().invoke(getModuleFunction("sendToNextTask"), item); //$NON-NLS-1$
            
        }catch(JepException e) {
            if (e.toString().contains(" has no attribute ")) {
                methodExists = false;
                super.sendToNextTask(item);
                return;
            }
            LOGGER.warn("Exception from " + getName() + " on " + item.getPath() + ": " + e.toString());
            LOGGER.debug("", e);
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
                processQueueEnd = (Boolean) getJep().invoke(getModuleFunction("processQueueEnd")); //$NON-NLS-1$
            } catch (JepException e) {
                processQueueEnd = false;
            }
        }
        return processQueueEnd;
    }

    @Override
    protected void process(IItem item) throws Exception {

        try {
            getJep().invoke(getModuleFunction("process"), item); //$NON-NLS-1$

        } catch (JepException e) {
            LOGGER.warn("Exception from " + getName() + " on " + item.getPath() + ": " + e.toString(), e);

        }
    }

    private static HashMap<File, Semaphore> semaphorePerScript = new HashMap<>();

    private Semaphore getSemaphore() {
        synchronized (semaphorePerScript) {
            Semaphore semaphore = semaphorePerScript.get(scriptFile);
            if (semaphore == null) {
                semaphore = new Semaphore(getMaxPermits());
                semaphorePerScript.put(scriptFile, semaphore);
            }
            return semaphore;
        }
    }

    private int getMaxPermits() {
        try {
            return ((Number) getJep().invoke(getModuleFunction("getMaxPermits"))).intValue();

        } catch (JepException e) {
            return Integer.MAX_VALUE;
        }
    }

    public void acquirePermit() throws InterruptedException {
        getSemaphore().acquire();
    }

    public void releasePermit() {
        getSemaphore().release();
    }

}
