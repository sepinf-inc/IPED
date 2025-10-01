package iped.engine.task.aleapp.interceptors;

import java.util.Map;

import iped.engine.task.aleapp.CallInterceptor;
import jep.PyMethod;

/**
 * Replace the ilapfuncs.get_txt_file_content function to return the file content directly in Java
 */
public class IlapfuncsLogfuncInterceptor extends CallInterceptor {

    public IlapfuncsLogfuncInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.logfunc");
    }

    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        String message = (String) getArgumentValue("message", 0, args, kwargs);
        logger.warn(message);
        return null;
    }
}
