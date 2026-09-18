package iped.engine.task.leapp.interceptors;

import java.util.Map;

import iped.engine.task.leapp.AleappTask;
import iped.engine.task.leapp.CallInterceptor;
import jep.PyMethod;

/**
 * Intercepts ilapfuncs.device_info only to serialize it: ilapfuncs.identifiers is a shared module-global dict,
 * populated by device_info with a read-modify-write sequence, and IPED workers run plugins concurrently, so
 * unsynchronized calls could lose updates.
 *
 * The original Python function is called unchanged, under {@link AleappTask#DEVICE_INFO_LOCK} — the same monitor
 * guarding write_device_info() in AleappTask.processDeviceInfoEvidence(), so the dict cannot be mutated while it is
 * being iterated for the report.
 *
 * NOTE: intercepting in Java (instead of wrapping in Python) preserves device_info's caller attribution: it uses
 * inspect.stack()[1] to record which plugin function reported the value, and the plugin -> Java -> original call
 * round-trip pushes no intermediate Python frame.
 */
public class IlapfuncsDeviceInfoInterceptor extends CallInterceptor {

    public IlapfuncsDeviceInfoInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.device_info");
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {
        synchronized (AleappTask.DEVICE_INFO_LOCK) {
            return super.call(args, kwargs);
        }
    }
}
