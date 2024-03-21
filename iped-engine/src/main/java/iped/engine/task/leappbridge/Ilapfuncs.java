package iped.engine.task.leappbridge;

import java.util.Collection;
import java.util.HashMap;

import iped.engine.data.Item;
import iped.parsers.python.PythonParser;
import iped.utils.pythonhook.PythonHook;
import jep.Jep;

/**
 * Class created to organize functions that override ilapfuncs ALeapp module
 */
public class Ilapfuncs {
    static HashMap<Integer, StringBuffer> devInfoBuffers = new HashMap<Integer, StringBuffer>();

    public static void install(Jep jep) {
        PythonHook pt = PythonHook.installHook(jep);

        // pt.overrideFileOpen(LeappBridgeTask.class.getMethod("open", Collection.class,
        // Map.class));
        try {
            pt.overrideModuleFunction("scripts.ilapfuncs", "logfunc",
                    Ilapfuncs.class.getMethod("logfunc", String.class));
            pt.overrideModuleFunction("scripts.ilapfuncs", "timeline", Ilapfuncs.class.getMethod("timeline",
                    String.class, String.class, Collection.class, Collection.class));
            pt.overrideModuleFunction("scripts.ilapfuncs", "media_to_html",
                    Ilapfuncs.class.getMethod("media_to_html", String.class, Collection.class, String.class));
            pt.overrideModuleFunction("scripts.ilapfuncs", "logdevinfo",
                    Ilapfuncs.class.getMethod("logdevinfo", String.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Function used to override ilapfuncs.logfunc from ALeapp and intercepts any
     * call of it to be handled by IPED java code
     */
    public static void logfunc(String message) {
        LeappBridgeTask.logger.info(message);
    }

    /*
     * Function used to override ilapfuncs.logdevinfo from ALeapp and intercepts any
     * call of it to be handled by IPED java code
     */
    public static void logdevinfo(String message) {
        Jep jep = PythonParser.getJep();
        Item evidence = (Item) jep.getValue("evidence");
        int leappRepEvidence = evidence.getParentId();
        StringBuffer stringBuffer;
        synchronized (devInfoBuffers) {
            stringBuffer = devInfoBuffers.get(leappRepEvidence);
            if (stringBuffer == null) {
                stringBuffer = new StringBuffer();
                devInfoBuffers.put(leappRepEvidence, stringBuffer);
            }
        }
        synchronized (stringBuffer) {
            stringBuffer.append(message + "<br/>");
        }
    }

    public static void timeline(String reportFolder, String tlactivity, Collection datalist, Collection data_headers) {

    }

    public static String media_to_html(String mediaPath, Collection filesFound, String report_folder) {
        for (Object file : filesFound) {
            if (file.toString().contains(mediaPath)) {
                String resultado = "<a href=\"" + file.toString() + "\"></a>";
                return resultado;
            }
        }
        return "";
    }

    public static StringBuffer getDeviceInfoBuffer(int evidence) {
        return devInfoBuffers.get(evidence);
    }

}
