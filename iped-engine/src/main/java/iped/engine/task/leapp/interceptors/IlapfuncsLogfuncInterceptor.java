package iped.engine.task.leapp.interceptors;

import java.util.Map;

import iped.engine.task.leapp.CallInterceptor;
import iped.engine.task.leapp.LeappContext;
import jep.PyMethod;

/**
 * Replaces the ilapfuncs.logfunc function so ALEAPP plugin log messages are redirected to the IPED log instead of
 * ALEAPP's own report log file.
 */
public class IlapfuncsLogfuncInterceptor extends CallInterceptor {

    public IlapfuncsLogfuncInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.logfunc");
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        String message = (String) getArgumentValue("message", 0, args, kwargs);
        String moduleName = LeappContext.get().getPlugin().getModuleName();
        String pluginName = LeappContext.get().getPlugin().getName();

        logger.info("{} [{}]: {}", moduleName, pluginName, message);

        return null;
    }
}
