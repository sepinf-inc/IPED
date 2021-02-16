package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.HashMap;
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

    private File scriptFile;
    private Jep jep;
    private Properties confParams;
    private File confDir;
    private Boolean processQueueEnd;
    private boolean isEnabled = true;

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
        if (jep == null) {
            synchronized (this.getClass()) {
                jep = getNewJep(true);
            }
        }
        return jep;
    }

    private Jep getNewJep(boolean callInit) throws JepException {

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

        jep.set("caseData", this.caseData); //$NON-NLS-1$
        jep.set("moduleDir", this.output); //$NON-NLS-1$
        jep.set("worker", this.worker); //$NON-NLS-1$
        jep.set("stats", this.stats); //$NON-NLS-1$
        jep.set("logger", LOGGER); //$NON-NLS-1$
        jep.set("javaArray", new ArrayConverter()); //$NON-NLS-1$
        jep.set("javaTask", this); //$NON-NLS-1$
        jep.set("ImageUtil", new ImageUtil()); //$NON-NLS-1$

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        jep.set("numThreads", Integer.valueOf(localConfig.getNumThreads()));

        jep.runScript(scriptFile.getAbsolutePath());

        if (callInit) {
            jep.invoke("init", confParams, confDir);
            isEnabled = (Boolean) jep.invoke("isEnabled"); //$NON-NLS-1$
        }

        return jep;
    }

    @Override
    public String getName() {
        return scriptFile.getName();
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        this.confParams = confParams;
        this.confDir = confDir;
        try (Jep jep = getNewJep(true)) {
        }
    }

    @Override
    public void finish() throws Exception {

        if (!isEnabled)
            return;

        try (IPEDSource ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer)) {

            IPEDSearcher searcher = new IPEDSearcher(ipedCase);

            jep.set("ipedCase", ipedCase); //$NON-NLS-1$
            jep.set("searcher", searcher); //$NON-NLS-1$
            jep.invoke("finish"); //$NON-NLS-1$

        } finally {
            jep.close();
            jep = null;
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
            jep.invoke("sendToNextTask", item); //$NON-NLS-1$
            
        }catch(JepException e) {
            if(e.toString().contains("Unable to find object with name: sendToNextTask")) { //$NON-NLS-1$
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
                processQueueEnd = (Boolean) getJep().invoke("processQueueEnd"); //$NON-NLS-1$
            } catch (JepException e) {
                processQueueEnd = false;
            }
        }
        return processQueueEnd;
    }

    @Override
    protected void process(IItem item) throws Exception {

        try {
            getJep().invoke("process", item); //$NON-NLS-1$

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
            return ((Number) getJep().invoke("getMaxPermits")).intValue();

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
