package iped.engine.task.leapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.task.leapp.interceptors.IlapfuncsLogfuncInterceptor;
import iped.engine.task.leapp.interceptors.IlapfuncsTsvInterceptor;
import jep.Jep;

public class LeappInterceptors {

    protected static final Logger logger = LoggerFactory.getLogger(LeappInterceptors.class);

    private List<CallInterceptor> interceptors = new ArrayList<>();

    public LeappInterceptors() {
        interceptors.add(new IlapfuncsLogfuncInterceptor());
        interceptors.add(new IlapfuncsTsvInterceptor());
    }

    public void install(Jep jep) {

        disableFunctions(jep);

        makeContextThreadLocal(jep);

        for (CallInterceptor interceptor : interceptors) {
            interceptor.install(jep);
        }
    }

    /**
     * Patches LEAPP's scripts.context.Context so its per-plugin-run state becomes thread-local, isolating concurrent
     * plugin runs on different IPED workers that share the same Python interpreter (Jep SharedInterpreter).
     *
     * The full rationale and the patch itself live in the context_thread_local_patch.py resource.
     */
    private void makeContextThreadLocal(Jep jep) {
        String script;
        try (InputStream is = getClass().getResourceAsStream("context_thread_local_patch.py")) {
            script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load context_thread_local_patch.py resource", e);
        }
        jep.exec(script);
    }

    public void disableFunctions(Jep jep) {

        // not used... prefer tsv
        disableLavaFuncs(jep);

        // timeline is not used... prefer tsv
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.timeline");

        // avoid to use backslash as path separator
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.is_platform_windows", "False");

        // ArtifactHtmlReport is not used... prefer tsv
        disablePythonClass(jep, "scripts.artifact_report", "scripts.artifact_report.ArtifactHtmlReport");
    }

    private void disableLavaFuncs(Jep jep) {

        // Important!! ---> lavafuncs MUST be disabled before import ilapfuncs

        // disable lava init/finalize (not called by IPED explicitly)
        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L312
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.initialize_lava");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_finalize_output");

        // used by plugins that set attribute "output_type" = 'lava'
        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/ilapfuncs.py#L506
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_process_artifact", "[None, None, None]");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_data");

        // used in ilapfuncs.artifact_processor function (get_data_list_with_media)
        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/ilapfuncs.py#L402
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_get_full_media_info", "['', '', '', '', '', '', '', '']");

        // used by check_in_media and check_in_embedded_media
        // (not mandatory to disable since check_in_media and check_in_embedded_media will be disabled)
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_media_item");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_media_references");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_get_media_references");

        // functions that uses lavafuncs (currently only appicons uses it)
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.check_in_media");
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.check_in_embedded_media");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.ilapfuncs.set_media_references");
    }

    private void disablePythonFunction(Jep jep, String module, String function, String returnValue) {
        jep.exec("import " + module);
        jep.exec(function + " = lambda *args, **kwargs: " + returnValue);
    }

    private void disablePythonFunction(Jep jep, String module, String function) {
        disablePythonFunction(jep, module, function, "None");
    }

    private void disablePythonClass(Jep jep, String module, String clazz) {
        jep.exec("import " + module);
        jep.exec("for name in dir(" + clazz + "):\n"
                + "    # We only want to replace public methods, not special ones like __init__\n"
                + "    if not name.startswith('__'):\n"
                + "        attr = getattr(" + clazz + ", name)\n"
                + "        \n"
                + "        # Check if the attribute is a callable method\n"
                + "        if callable(attr):\n"
                + "            # Replace the method with a function that does nothing\n"
                + "            setattr(" + clazz + ", name, lambda *args, **kwargs: None)");
    }
}
