package dpf.inc.sepinf.python;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import jep.Jep;
import jep.JepException;
import jep.SharedInterpreter;

public class PythonParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonParser.class);

    private static final String JEP_NOT_FOUND = "JEP not found!";
    private static final String SEE_MANUAL = "See the manual.";
    public static final String PYTHON_PARSERS_FOLDER = "PYTHON_PARSERS_FOLDER";

    private static volatile JepException jepException = null;

    private static final Map<Long, Jep> jepPerThread = new HashMap<>();
    private static final Set<String> instancetPerThread = new HashSet<>();

    private static final Map<MediaType, PythonParser> mediaToParserMap = new ConcurrentHashMap<>();

    private ArrayList<String> globals = new ArrayList<>();
    private File scriptFile;


    @SuppressWarnings("unchecked")
    public PythonParser(){

        synchronized (this.getClass()) {
            if (!mediaToParserMap.isEmpty()) {
                return;
            }
            File pythonParsersFolder = new File(System.getProperty(PYTHON_PARSERS_FOLDER));
            File[] scripts = pythonParsersFolder.listFiles();
            if (scripts != null) {
                for (File file : scripts) {
                    if (!file.isFile()) {
                        continue;
                    }
                    PythonParser parser = new PythonParser(file);
                    try {
                        Collection<MediaType> mediaTypes = (Collection<MediaType>) parser.getJep()
                                .invoke(parser.getInstanceMethod("getSupportedTypes"), new ParseContext());

                        for (MediaType mt : mediaTypes) {
                            mediaToParserMap.put(mt, parser);
                        }

                    } catch (JepException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

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
            if (jepException == null) {
                String msg = JEP_NOT_FOUND + SEE_MANUAL;
                jepException = new JepException(msg, e);
                LOGGER.error(msg);
                jepException.printStackTrace();
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

        for (String global : globals) {
            jep.eval(moduleName + "." + global + " = " + global);
        }

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

    public String getName() {
        return scriptFile.getName();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        MediaType type = MediaType.parse(metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE));
        mediaToParserMap.get(type).pythonParse(stream, handler, metadata, context);

    }

    private void pythonParse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        if (jepException != null)
            throw new TikaException("JEPException", jepException);

        try {
            getJep().invoke(getInstanceMethod("parse"), stream, handler, metadata, context);

        } catch (JepException e) {
            throw new TikaException("Error from " + getName(), e);
        }

    }

}
