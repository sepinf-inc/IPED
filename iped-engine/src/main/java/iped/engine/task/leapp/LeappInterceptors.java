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
import iped.engine.task.leapp.interceptors.LavaInsertSqliteDataInterceptor;
import jep.Jep;

public class LeappInterceptors {

    protected static final Logger logger = LoggerFactory.getLogger(LeappInterceptors.class);

    private List<CallInterceptor> interceptors = new ArrayList<>();

    public LeappInterceptors() {
        interceptors.add(new IlapfuncsLogfuncInterceptor());
        // results are captured from lava_insert_sqlite_data (NOT tsv/timeline/html):
        // it is the only output call receiving raw typed headers and raw values
        interceptors.add(new LavaInsertSqliteDataInterceptor());
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

        disableLavaFuncs(jep);

        // tsv, timeline and kml outputs are not used: results are captured from
        // lava_insert_sqlite_data, which receives the raw typed data
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.tsv");
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.timeline");
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.kmlgen");
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.set_media_references");

        // avoid to use backslash as path separator
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.is_platform_windows", "False");

        // html output is not used
        disablePythonClass(jep, "scripts.artifact_report", "scripts.artifact_report.ArtifactHtmlReport");

        patchMediaFunctions(jep);
    }

    /**
     * Media handling: plugins register media files through check_in_media, whose return value ends up in the
     * data_list cells of 'media' typed columns.
     *
     * The original implementation copies files and registers them in the LAVA media database (disabled here). It is
     * replaced by a version that simply returns the extraction path of the media file (resolved against the
     * thread-local Context), which LavaInsertSqliteDataInterceptor then maps back to the original case item and adds
     * to the subitem's linkedItems.
     *
     * get_data_list_with_media is also patched: artifact_processor calls it whenever a 'media' column exists (BEFORE
     * checking output types) to build the html/tsv views, and it would crash against the disabled
     * lava_get_full_media_info stub. Since html/tsv outputs are disabled, it just passes data through.
     */
    private void patchMediaFunctions(Jep jep) {
        jep.exec("import scripts.ilapfuncs");
        jep.exec("from scripts.context import Context as _iped_leapp_context");

        jep.exec("def _iped_check_in_media(file_path, name='', *args, **kwargs):"
                + " return _iped_leapp_context.get_source_file_path(file_path)");
        jep.exec("scripts.ilapfuncs.check_in_media = _iped_check_in_media");

        // embedded media has no corresponding case item to link: keep it disabled
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.check_in_embedded_media");

        jep.exec("scripts.ilapfuncs.get_data_list_with_media = lambda media_header_info, data_list: (data_list, data_list)");
    }

    private void disableLavaFuncs(Jep jep) {

        // Important!! ---> lavafuncs MUST be disabled before import ilapfuncs

        // disable lava init/finalize (not called by IPED explicitly)
        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/aleapp.py#L312
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.initialize_lava");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_finalize_output");

        // artifact_processor unpacks lava_process_artifact's return: keep the shape.
        // LavaInsertSqliteDataInterceptor does not need these values, the types come
        // from the data_headers tuples
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_process_artifact", "[None, None, None]");

        // disabled here (BEFORE scripts.ilapfuncs is imported, so its from-import
        // captures the no-op) as a safety net for plugins importing it directly from
        // lavafuncs; the binding actually used by artifact_processor is
        // scripts.ilapfuncs.lava_insert_sqlite_data, which is replaced later by
        // LavaInsertSqliteDataInterceptor
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_data");

        // used in ilapfuncs.artifact_processor function (get_data_list_with_media)
        // https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/ilapfuncs.py#L402
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_get_full_media_info", "['', '', '', '', '', '', '', '']");

        // used by the original check_in_media/check_in_embedded_media (replaced in
        // patchMediaFunctions, so these are just a safety net)
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_media_item");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_media_references");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_get_media_references");
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
