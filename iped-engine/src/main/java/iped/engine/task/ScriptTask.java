/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;

public class ScriptTask extends AbstractTask implements IScriptTask {

    private static IPEDSource ipedCase;
    private static int numInstances = 0;

    private File scriptFile;
    private ScriptEngine engine;
    private Invocable inv;
    private String scriptName;

    public ScriptTask(File scriptFile) {
        this.scriptFile = scriptFile;
        try {
            loadScript(this.scriptFile);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadScript(File file) throws IOException, ScriptException, UnsupportedEncodingException, NoSuchMethodException {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) { //$NON-NLS-1$
            ScriptEngineManager manager = new ScriptEngineManager();
            String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1); // $NON-NLS-1$
            engine = manager.getEngineByExtension(ext); // $NON-NLS-1$
            if(engine==null) {
                throw new ScriptException("No engine configured for this file type."); 
            }else {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                engine.eval(reader);
                inv = (Invocable) engine;
            }
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        try {
            List<Configurable<?>> configs = (List<Configurable<?>>) inv.invokeFunction("getConfigurables");
            return configs != null ? configs : Collections.emptyList();

        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        engine.put("caseData", this.caseData); //$NON-NLS-1$
        engine.put("moduleDir", this.output); //$NON-NLS-1$
        engine.put("worker", this.worker); //$NON-NLS-1$
        engine.put("stats", this.stats); //$NON-NLS-1$

        scriptName = (String) inv.invokeFunction("getName"); //$NON-NLS-1$

        inv.invokeFunction("init", configurationManager); //$NON-NLS-1$

        numInstances++;
    }

    @Override
    public void finish() throws Exception {

        if (ipedCase == null)
            ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer);

        try {
            IPEDSearcher searcher = new IPEDSearcher(ipedCase);

            engine.put("ipedCase", ipedCase); //$NON-NLS-1$
            engine.put("searcher", searcher); //$NON-NLS-1$

            inv.invokeFunction("finish"); //$NON-NLS-1$

        } finally {
            // remove references to heavy objects
            engine.getBindings(ScriptContext.ENGINE_SCOPE).clear();
            if (--numInstances == 0)
                ipedCase.close();
        }
    }

    @Override
    public void process(IItem e) throws Exception {
        inv.invokeFunction("process", e); //$NON-NLS-1$
    }

    @Override
    public String getName() {
        return scriptName;
    }

    @Override
    public String getScriptFileName() {
        return scriptFile.getAbsolutePath();
    }
    
    /**
     * Stub interface to validate script code
     * @author patrick.pdb
     *
     */
    public interface ITask{
        public abstract List<Configurable<?>> getConfigurables();
        public abstract void init(ConfigurationManager configurationManager) throws Exception;
        abstract public void finish() throws Exception;
        abstract public void process(IItem evidence) throws Exception;        
    }

    @Override
    public Class<? extends AbstractTask> checkTaskCompliance(String src) throws ScriptTaskComplianceException {
        ITask task = null;
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            engine = manager.getEngineByExtension("js"); // $NON-NLS-1$
            engine.eval(new StringReader(src));
            inv = (Invocable) engine;
            task = inv.getInterface(ITask.class);
        }catch (Exception e) {
            throw new ScriptTaskComplianceException(e);
        }
        if(task==null) {
            throw new ScriptTaskComplianceException("Not all methods implementations found. Required methods are: getConfigurables, init, finish and process.");
        }
        return null;
    }

}
