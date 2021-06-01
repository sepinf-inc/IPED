package dpf.inc.sepinf.python;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import jep.JEPClassFinder;
import jep.Jep;
import jep.JepConfig;
import jep.JepException;
import jep.SharedInterpreter;

public class PythonParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonParser.class);

    public static final String PYTHON_PARSERS_FOLDER = "PYTHON_PARSERS_FOLDER";

    public static final String JEP_NOT_FOUND = Messages.getString("PythonModule.JepNotFound");
    public static final String DISABLED = Messages.getString("PythonModule.ModuleDisabled");
    public static final String SEE_MANUAL = Messages.getString("PythonModule.SeeManual");

    private static final Map<Long, Jep> jepPerThread = new HashMap<>();
    private static final Set<String> instancetPerThread = new HashSet<>();
    private static final Map<MediaType, PythonParser> mediaToParserMap = new ConcurrentHashMap<>();
    private static final Map<MediaType, Integer> mediaTypesToQueueOrder = new ConcurrentHashMap<>();
    private static final AtomicBoolean jepNotFoundPrinted = new AtomicBoolean();
    private static boolean inited = false;

    private File scriptFile;


    @SuppressWarnings("unchecked")
    public PythonParser(){

        synchronized (this.getClass()) {
            if (inited) {
                return;
            }
            inited = true;

            // fix for https://github.com/sepinf-inc/IPED/issues/586
            try {
                JepConfig config = new JepConfig();
                config.setClassEnquirer(JEPClassFinder.getInstance());
                SharedInterpreter.setConfig(config);
            } catch (JepException e1) {
                throw new RuntimeException(e1);
            }

            File pythonParsersFolder = new File(System.getProperty(PYTHON_PARSERS_FOLDER));
            File[] scripts = pythonParsersFolder.listFiles();
            if (scripts != null) {
                for (File file : scripts) {
                    if (!file.isFile()) {
                        continue;
                    }
                    PythonParser parser = new PythonParser(file);
                    Jep jep = null;
                    try {
                        jep = getJep();
                        if (jep == null) {
                            return;
                        }
                        Collection<String> mediaTypes = (Collection<String>) jep
                                .invoke(parser.getInstanceMethod("getSupportedTypes"), new ParseContext());

                        for (String mt : mediaTypes) {
                            mediaToParserMap.put(MediaType.parse(mt), parser);
                        }

                    } catch (JepException e) {
                        if (e.toString().contains("ModuleNotFoundError")) {
                            String msg = e.getMessage() + ". " + file.getName() + DISABLED + SEE_MANUAL;
                            LOGGER.error(msg);
                            e.printStackTrace();
                            continue;
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                    try {
                        Map<String, Number> map = (Map<String, Number>) jep
                                .invoke(parser.getInstanceMethod("getSupportedTypesQueueOrder"));
                        for (Entry<String, Number> entry : map.entrySet()) {
                            mediaTypesToQueueOrder.put(MediaType.parse(entry.getKey()), entry.getValue().intValue());
                        }

                    } catch (JepException e) {
                        if (e.toString().contains(" has no attribute ")) {
                            // optional method, ignore.
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

    }

    public static final Map<MediaType, Integer> getMediaTypesToQueueOrder() {
        return mediaTypesToQueueOrder;
    }

    public PythonParser(File script) {
        this.scriptFile = script;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return mediaToParserMap.keySet();
    }

    public static Jep getJep() throws JepException {
        synchronized (jepPerThread) {
            Jep jep = jepPerThread.get(Thread.currentThread().getId());
            if (jep == null) {
                jep = getNewJep();
                jepPerThread.put(Thread.currentThread().getId(), jep);
            }
            return jep;
        }
    }

    private static Jep getNewJep() throws JepException {

        Jep jep;
        try {
            jep = new SharedInterpreter();

        } catch (UnsatisfiedLinkError e) {
            if (!jepNotFoundPrinted.getAndSet(true)) {
                String msg = JEP_NOT_FOUND + SEE_MANUAL;
                LOGGER.error(msg);
                e.printStackTrace();
            }
            return null;
        }

        jep.eval("from jep import redirect_streams");
        jep.eval("redirect_streams.setup()");

        // setGlobalVar(jep, "logger", LOGGER); //$NON-NLS-1$

        return jep;
    }

    private void loadScript(Jep jep) throws JepException {

        if (jep == null) {
            return;
        }

        jep.eval("import sys");
        jep.eval("sys.path.append('" + scriptFile.getParentFile().getAbsolutePath().replace("\\", "\\\\") + "')");

        String className = getClassName();
        String moduleName = className;

        jep.eval("import " + moduleName);

        String instanceName = getInstanceName();
        String eval = instanceName + " = " + moduleName + "." + className + "()";
        jep.eval(eval);

    }

    private String getClassName() {
        return scriptFile.getName().replace(".py", "");
    }

    private String getInstanceName() {
        return getClassName().toLowerCase() + "_thread_" + Thread.currentThread().getId();
    }

    private String getInstanceMethod(String function) throws JepException {
        String instanceName = getInstanceName();
        synchronized (instancetPerThread) {
            if (!instancetPerThread.contains(instanceName)) {
                loadScript(getJep());
                instancetPerThread.add(instanceName);
            }
        }
        String ret = instanceName + "." + function;
        return ret;
    }

    public String getName(String contentType) {
        MediaType type = MediaType.parse(contentType);
        return mediaToParserMap.get(type).getClassName();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        MediaType type = MediaType.parse(metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE));
        mediaToParserMap.get(type).pythonParse(stream, handler, metadata, context);

    }

    private void pythonParse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try {
            getJep().invoke(getInstanceMethod("parse"), stream, handler, metadata, context);

        } catch (JepException e) {
            throw new TikaException("Error from " + getClassName(), e);
        }

    }

}
