package iped.engine.task.leappbridge;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.HashSet;

public class LeapArtifactsPlugin {
    String moduleName;
    String methodName;
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

    public void setMethodName(String methodName) {
        this.methodName = methodName;
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

}
