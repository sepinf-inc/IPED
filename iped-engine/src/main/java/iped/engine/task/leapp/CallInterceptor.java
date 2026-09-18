package iped.engine.task.leapp;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private String pythonModule;
    private String pythonFunction;
    private boolean isClassMethod = false;

    private PyCallable originalCall;

    public CallInterceptor(String pythonModule, String pythonFunction, boolean isClassMethod) {
        this.pythonModule = pythonModule;
        this.pythonFunction = pythonFunction;
        this.isClassMethod = isClassMethod;
    }

    public CallInterceptor(String pythonModule, String pythonFunction) {
        this(pythonModule, pythonFunction, false);
    }

    public void install(Jep jep) {

        if (StringUtils.isNotBlank(pythonModule)) {
            jep.exec("import " + pythonModule);
        }

        // "_iped_leapp_interceptor" is only a temporary handoff variable used during
        // install: both branches below capture this interceptor immediately, so the
        // global is deleted at the end. The name is prefixed because the interpreter
        // namespace is shared with PythonParser/PythonTask scripts.
        jep.set("_iped_leapp_interceptor", this);

        if (isClassMethod) {

            String clazz = StringUtils.substringBeforeLast(pythonFunction, ".");
            String method = StringUtils.substringAfterLast(pythonFunction, ".");

            originalCall = jep.getValue("getattr(" + clazz + ", \"" + method + "\")", PyCallable.class);
            if (originalCall == null) {
                throw new IllegalStateException("Original call is null for: " + pythonFunction);
            }

            // The keyword-only default "_interceptor=..." captures the interceptor at def
            // time. Referencing the global directly in the body would be resolved at CALL
            // time (late binding), breaking once the temp global is deleted below.
            jep.exec("def _iped_leapp_interceptor_method(self, *args, _interceptor=_iped_leapp_interceptor, **kwargs):"
                    + " return _interceptor.call(self, *args, **kwargs)");

            // the class attribute keeps the function reference: the temp global can go
            jep.exec("setattr(" + clazz + ", \"" + method + "\", _iped_leapp_interceptor_method)");
            jep.exec("del _iped_leapp_interceptor_method");

        } else {

            originalCall = jep.getValue(pythonFunction, PyCallable.class);
            if (originalCall == null) {
                throw new IllegalStateException("Original call is null for: " + pythonFunction);
            }

            // "_iped_leapp_interceptor.call" is evaluated NOW, at exec time: the module
            // attribute ends up holding a bound callable, independent of the global name.
            jep.exec(pythonFunction + " = _iped_leapp_interceptor.call");
        }

        jep.exec("del _iped_leapp_interceptor");
    }

    // The three call() overloads below exist because Jep dispatches by the Python
    // call shape (positional-only, kwargs-only, or both), selected via @PyMethod.
    // Subclasses overriding call(Object[], Map) must repeat the @PyMethod
    // annotation, otherwise Jep dispatch breaks.

    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        if (logger.isDebugEnabled()) {
            logger.debug("JAVA INTERCEPTOR: ---- 001 ----");
            logger.debug("JAVA INTERCEPTOR: call: " + pythonFunction);
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