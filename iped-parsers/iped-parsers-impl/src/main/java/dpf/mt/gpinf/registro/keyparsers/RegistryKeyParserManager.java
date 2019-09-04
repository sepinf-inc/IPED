package dpf.mt.gpinf.registro.keyparsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Set;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistryKeyParserManager {
    ScriptEngine engine;
    Invocable inv;

    // singleton
    private static RegistryKeyParserManager registroKeyParserManager = new RegistryKeyParserManager();

    private RegistryKeyParserManager() {
        loadConfigPath();
    }

    private void loadConfigPath() {
        File dir;
        try {
            String configPath = System.getProperty("iped.configPath");

            dir = new File(configPath + "/conf/ParsersCustomConfigs/dpf.mt.gpinf.registro.RegistroParser");

            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".xml");
                }
            });
            for (int i = 0; i < files.length; i++) {
                loadConfigFile(files[i]);
            }

            loadJSFiles(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadJSFiles(File dir) {
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".js");
            }
        });
        ScriptEngineManager manager = new ScriptEngineManager();
        this.engine = manager.getEngineByExtension("js"); // $NON-NLS-1$
        for (int i = 0; i < files.length; i++) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(files[i]), "UTF-8")) {
                engine.eval(reader);
            } catch (IOException | ScriptException e) {
                e.printStackTrace();
            }
        }
        this.inv = (Invocable) engine;
    }

    private void loadConfigFile(File f) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            dbf.setNamespaceAware(false);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();

            Document doc = docBuilder.parse(f);

            Element root = doc.getDocumentElement();

            NodeList parsersEls = root.getElementsByTagName("registryKeyParsers");
            for (int i = 0; i < parsersEls.getLength(); i++) {
                Element parsersEl = (Element) parsersEls.item(i);
                NodeList parserEls = parsersEl.getElementsByTagName("registryKeyParser");
                for (int j = 0; j < parserEls.getLength(); j++) {
                    Element parserEl = (Element) parserEls.item(j);
                    String className = parserEl.getAttribute("class");

                    Class<?> classe = Thread.currentThread().getContextClassLoader().loadClass(className);
                    RegistryKeyParser rkp = (RegistryKeyParser) classe.newInstance();
                    NodeList patternsEl = parserEl.getElementsByTagName("key");
                    for (int k = 0; k < patternsEl.getLength(); k++) {
                        Element patternEl = (Element) patternsEl.item(k);
                        String pattern = patternEl.getAttribute("name");
                        if (pattern.endsWith("/")) {
                            pattern = pattern.substring(0, pattern.length() - 1);
                        }
                        addRegistryKeyParser(pattern, rkp);

                        NodeList decodersEls = patternEl.getElementsByTagName("decoder");
                        for (int l = 0; l < decodersEls.getLength(); l++) {
                            Element decoderEl = (Element) decodersEls.item(l);
                            String function = decoderEl.getAttribute("function");
                            String valueNameDecoder = decoderEl.getAttribute("decodeValueName");
                            if ("yes".equals(valueNameDecoder)) {
                                rkp.addValueNameDecoderFunction(pattern, function);
                            } else {
                                NodeList includeEls = decoderEl.getElementsByTagName("include");
                                if (includeEls.getLength() == 0) {
                                    rkp.decodeAllValueDataDecoderFunction(pattern, function);
                                } else {
                                    for (int m = 0; m < includeEls.getLength(); m++) {
                                        Element includeEl = (Element) includeEls.item(m);
                                        String valueName = includeEl.getAttribute("valueName");
                                        rkp.addValueDataDecoderFunction(pattern, valueName, function);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public static RegistryKeyParserManager getRegistryKeyParserManager() {
        return registroKeyParserManager;
    }

    private KeyPathPatternMap<RegistryKeyParser> map = new KeyPathPatternMap<RegistryKeyParser>();
    private RegistryKeyParser defaultRegistryKeyParser = new HtmlKeyParser();

    public RegistryKeyParser getDefaultRegistryKeyParser() {
        return defaultRegistryKeyParser;
    }

    public RegistryKeyParser getRegistryKeyParser(String keyPath) {
        RegistryKeyParser result = null;

        result = map.getPatternMatch(keyPath);

        return result;
    }

    public boolean hasChildRegistered(String keyPath) {
        Set<String> collection = map.keySet();
        for (Iterator<String> iterator = collection.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (matchesStart(keyPath, value)) {
                return true;
            }
        }
        return false;
    }

    static public boolean matchesStart(String test, String pattern) {

        if (pattern.startsWith(test))
            return true;

        String patternSubts = pattern;
        int wildIndex = patternSubts.indexOf("*");
        while (wildIndex >= 0) {
            if (wildIndex > test.length())
                break;
            String meio = test.substring(wildIndex);
            int slashIndex = meio.indexOf("/");
            if (slashIndex >= 0) {
                meio = meio.substring(0, slashIndex);
                patternSubts = patternSubts.substring(0, patternSubts.indexOf("*")) + meio
                        + patternSubts.substring(patternSubts.indexOf("*") + 1);
            } else {
                patternSubts = patternSubts.substring(0, patternSubts.indexOf("*")) + meio
                        + patternSubts.substring(patternSubts.indexOf("*") + 1);
            }
            wildIndex = patternSubts.indexOf("*");
        }

        return patternSubts.startsWith(test);
    }

    public void addRegistryKeyParser(String keypath, RegistryKeyParser p) {
        map.put(keypath, p);
    }

    public Invocable getInv() {
        return inv;
    }

}
