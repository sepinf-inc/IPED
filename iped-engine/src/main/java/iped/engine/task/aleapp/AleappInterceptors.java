package iped.engine.task.aleapp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.data.CaseData;
import iped.engine.task.aleapp.interceptors.IlapfuncsGetBinaryFileContentInterceptor;
import iped.engine.task.aleapp.interceptors.IlapfuncsGetTxtFileContentInterceptor;
import iped.engine.task.aleapp.interceptors.IlapfuncsLogfuncInterceptor;
import iped.engine.task.aleapp.interceptors.IlapfuncsTsvInterceptor;
import iped.engine.task.aleapp.interceptors.PathConstructorInterceptor;
import iped.engine.task.aleapp.interceptors.PythonOpenInterceptor;
import jep.Jep;

public class AleappInterceptors {

    protected static final Logger logger = LoggerFactory.getLogger(AleappInterceptors.class);

    private List<CallInterceptor> interceptors = new ArrayList<>();

    public AleappInterceptors(CaseData caseData) {
        interceptors.add(new IlapfuncsLogfuncInterceptor());
        interceptors.add(new IlapfuncsGetBinaryFileContentInterceptor(caseData));
        interceptors.add(new IlapfuncsGetTxtFileContentInterceptor(caseData));
        interceptors.add(new IlapfuncsTsvInterceptor());
        interceptors.add(new PythonOpenInterceptor(caseData));
        interceptors.add(new PathConstructorInterceptor(caseData));
    }

    public void install(Jep jep) {

        disableFunctions(jep);

        for (CallInterceptor interceptor : interceptors) {
            interceptor.install(jep);
        }
    }

    public void disableFunctions(Jep jep) {
        disableLavaFuncs(jep);
        disablePythonFunction(jep, "scripts.ilapfuncs", "scripts.ilapfuncs.timeline");
        disablePythonClass(jep, "scripts.artifact_report", "scripts.artifact_report.ArtifactHtmlReport");
    }

    private void disableLavaFuncs(Jep jep) {

        // Important!! ---> lavafuncs MUST be disabled before import ilapfuncs

        // disable lava init/finalize (not called by IPED explicitly)
        // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L293
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.initialize_lava");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_finalize_output");

        // used by plugins that set attribute "output_type" = 'lava'
        // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/ilapfuncs.py#L338
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_process_artifact", "[None, None, None]");
        disablePythonFunction(jep, "scripts.lavafuncs", "scripts.lavafuncs.lava_insert_sqlite_data");

        // used in ilapfuncs.artifact_processor function (get_data_list_with_media)
        // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/ilapfuncs.py#L320
        // https://github.com/abrignoni/ALEAPP/blob/v3.4.0/scripts/ilapfuncs.py#L268
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
