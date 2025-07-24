package iped.engine.task.aleapp;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.ICaseData;
import jep.Jep;
import jep.PyMethod;
import jep.python.PyCallable;

/**
 * A Java class that can be used as a Python function.
 *
 * It intercepts calls to Python function and then calls the original function.
 */
public class CallInterceptor {

    protected static final Logger logger = LoggerFactory.getLogger(CallInterceptor.class);

    protected ICaseData caseData;
    private String pythonModule;
    private String pythonCall;
    private boolean isClass = false;

    private PyCallable originalCall;

    public CallInterceptor(ICaseData caseData, String pythonModule, String pythonCall, boolean isClass) {
        this.caseData = caseData;
        this.pythonModule = pythonModule;
        this.pythonCall = pythonCall;
        this.isClass = isClass;
    }

    public CallInterceptor(ICaseData caseData, String pythonModule, String pythonCall) {
        this(caseData, pythonModule, pythonCall, false);
        this.caseData = caseData;
    }

    public CallInterceptor(String pythonModule, String pythonCall) {
        this(null, pythonModule, pythonCall);
    }

    public void install(Jep jep) {

        if (StringUtils.isNotBlank(pythonModule)) {
            jep.exec("import " + pythonModule);
        }

        if (isClass) {

            String clazz = StringUtils.substringBeforeLast(pythonCall, ".");
            String method = StringUtils.substringAfterLast(pythonCall, ".");

            originalCall = jep.getValue("getattr(" + clazz + ", \"" + method + "\")", PyCallable.class);

            jep.set("interceptor", this);

            jep.exec("def interceptor_method(self, *args, **kwargs):"
                    + "    interceptor.call(self, *args, **kwargs)");

            jep.exec("setattr(" + clazz + ", \"" + method + "\", interceptor_method)");

        } else {

            originalCall = jep.getValue(pythonCall, PyCallable.class);

            jep.set("interceptor", this);

            jep.exec(pythonCall + " = interceptor.call");
        }
    }

    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        if (logger.isDebugEnabled()) {
            logger.debug("JAVA INTERCEPTOR: ---- 001 ----");
            logger.debug("JAVA INTERCEPTOR: call: " + pythonCall);
            logger.debug("JAVA INTERCEPTOR: varargs: " + Arrays.toString(args));
            logger.debug("JAVA INTERCEPTOR: kwargs: " + kwargs);
        }

        handleArgs(args, kwargs);

        return this.originalCall.call(args, kwargs);
    }

    @PyMethod(varargs = true, kwargs = false)
    public Object call(Object... args) throws Exception {
        return call(args, null);
    }

    @PyMethod(varargs = false, kwargs = true)
    public Object call(Object str, Map<String, Object> kwargs) throws Exception {
        Object[] args = new Object[] { str };
        return call(args, kwargs);
    }

    protected void handleArgs(Object[] args, Map<String, Object> kwargs) throws Exception {
    }

    protected Object getArgumentValue(String key, int index, Object[] args, Map<String, Object> kwargs) {
        if (key != null && kwargs != null && kwargs.containsKey(key)) {
            return kwargs.get(key);
        }

        if (index >= 0 && args != null && args.length > index) {
            return args[index];
        }

        return null;
    }

    protected void setArgumentValue(String key, int index, Object value, Object[] args, Map<String, Object> kwargs) {

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Setting value: [%s,%d] <= %s", key, index, value));
        }

        if (key != null && kwargs != null && kwargs.containsKey(key)) {
            kwargs.put(key, value);
            return;
        }

        if (index >= 0 && args != null && args.length > index) {
            args[index] = value;
            return;
        }

        throw new IllegalArgumentException(String.format("Invalid key or index: [%s,%d] <= %s %s", key, index, Arrays.toString(args), kwargs));
    }

}