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
package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.IItem;

public class ScriptTask extends AbstractTask {

    private static IPEDSource ipedCase;
    private static int numInstances = 0;

    private File scriptFile;
    private ScriptEngine engine;
    private Invocable inv;

    public ScriptTask(File scriptFile) {
        this.scriptFile = scriptFile;
        try {
            loadScript(this.scriptFile);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadScript(File file)
            throws IOException, ScriptException, UnsupportedEncodingException, NoSuchMethodException {

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) { //$NON-NLS-1$

            ScriptEngineManager manager = new ScriptEngineManager();
            String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1); // $NON-NLS-1$
            engine = manager.getEngineByExtension(ext); // $NON-NLS-1$
            engine.eval(reader);
            inv = (Invocable) engine;
        }

    }

    @Override
    public void init(Properties confProps, File configPath) throws Exception {

        engine.put("caseData", this.caseData); //$NON-NLS-1$
        engine.put("moduleDir", this.output); //$NON-NLS-1$
        engine.put("worker", this.worker); //$NON-NLS-1$
        engine.put("stats", this.stats); //$NON-NLS-1$

        inv.invokeFunction("init", confProps, configPath); //$NON-NLS-1$

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
        try {
            return (String) inv.invokeFunction("getName"); //$NON-NLS-1$

        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

}
