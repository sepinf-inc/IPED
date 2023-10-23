package iped.engine.task.regex.validator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import iped.engine.task.regex.RegexValidatorService;

public class ScriptValidatorService implements RegexValidatorService {

    private Map<String, Invocable> scripts = new HashMap<>();
    private ScriptEngineManager manager = new ScriptEngineManager();

    public ScriptValidatorService() {
        super();
    }

    @Override
    public void init(File confDir) {

        File validatorsDir = new File(confDir, "regex_validators");

        File[] subFiles = validatorsDir.listFiles();
        if (subFiles != null) {
            for (File file : subFiles) {
                registerScript(file);
            }
        }
    }

    protected void registerScript(File file) {
        try {
            Invocable script = loadScript(manager, file);
            Map<String, Object> bindings = (Map<String, Object>) script.invokeFunction("getRegexNames");
            List<String> regexNames = getStringArrayValues(bindings);
            for (String regexName : regexNames) {
                scripts.put(regexName, script);
            }
        } catch (NoSuchMethodException | IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getStringArrayValues(Map<String, Object> bindings) {
        Integer length = ((Number) bindings.get("length")).intValue();
        List<String> result = new ArrayList<String>();

        for (int i = 0; i < length; i++) {
            result.add(bindings.get(Integer.toString(i)).toString());
        }

        return result;
    }

    private Invocable loadScript(ScriptEngineManager manager, File file)
            throws IOException, ScriptException, UnsupportedEncodingException, NoSuchMethodException {

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) { //$NON-NLS-1$

            ScriptEngine engine = manager.getEngineByName("graal.js"); // $NON-NLS-1$
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            bindings.put("polyglot.js.nashorn-compat", true);
            engine.eval(reader);

            return (Invocable) engine;
        }

    }

    @Override
    public List<String> getRegexNames() {
        return new ArrayList<>(scripts.keySet());
    }

    @Override
    public boolean validate(String regexName, String hit) {
        Invocable script = scripts.get(regexName);
        if (script == null) {
            throw new IllegalArgumentException("Script for regex " + regexName + " not found.");
        }
        try {
            return (Boolean) script.invokeFunction("validate", hit);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String format(String regexName, String hit) {
        Invocable script = scripts.get(regexName);
        if (script == null) {
            throw new IllegalArgumentException("Script for regex " + regexName + " not found.");
        }
        try {
            return (String) script.invokeFunction("format", hit);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

}
