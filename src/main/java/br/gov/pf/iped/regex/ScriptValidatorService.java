package br.gov.pf.iped.regex;

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
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.sun.star.lang.IllegalArgumentException;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class ScriptValidatorService implements RegexValidatorService {

  private Map<String, Invocable> scripts = new HashMap<>();
  private ScriptEngineManager manager = new ScriptEngineManager();

  public ScriptValidatorService() {
    super();
  }

  @Override
  public void init(File confDir) {

    File validatorsDir = new File(confDir, "validators");

    for (File file : validatorsDir.listFiles()) {
      registerScript(file);
    }
  }

  protected void registerScript(File file) {
    try {
      Invocable script = loadScript(manager, file);
      Bindings bindings = (Bindings) script.invokeFunction("getRegexNames");
      List<String> regexNames = getStringArrayValues(bindings);
      for (String regexName : regexNames) {
        scripts.put(regexName, script);
      }
    } catch (NoSuchMethodException | IOException | ScriptException e) {
      throw new RuntimeException(e);
    }
  }

  private List<String> getStringArrayValues(Bindings bindings) {
    Integer length = (Integer) bindings.get("length");
    List<String> result = new ArrayList<String>();

    for (int i = 0; i < length; i++) {
      result.add(bindings.get(Integer.toString(i)).toString());
    }

    return result;
  }

  private Invocable loadScript(ScriptEngineManager manager, File file)
      throws IOException, ScriptException, UnsupportedEncodingException, NoSuchMethodException {

    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) { //$NON-NLS-1$

      String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1); // $NON-NLS-1$
      ScriptEngine engine = manager.getEngineByExtension(ext); // $NON-NLS-1$
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
