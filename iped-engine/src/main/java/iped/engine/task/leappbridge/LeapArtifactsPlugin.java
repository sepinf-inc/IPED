package iped.engine.task.leappbridge;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.HashSet;

import jep.python.PyCallable;

/**
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 * 
 *         Class that represents a identified and loaded ALeapp plugin
 */

public class LeapArtifactsPlugin {
    String moduleName;
    String methodName;
    PyCallable method;

    String name;
    HashSet<String> patterns = new HashSet<String>();
    HashSet<PathMatcher> compiledPatterns = new HashSet<PathMatcher>();

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashSet<String> getPatterns() {
        return patterns;
    }

    public HashSet<PathMatcher> getCompiledPatterns() {
        return compiledPatterns;
    }

    public void addPattern(String pattern) {
        patterns.add(pattern);
        compiledPatterns.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));
    }

    public PyCallable getMethod() {
        return method;
    }

    public void setMethod(PyCallable method) {
        this.method = method;
        this.methodName = (String) method.getAttr("__name__");
    }

}
