package iped.utils.pythonhook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import jep.Jep;

/**
 * Class used to help python method and class override
 * 
 * @author Patrick Dalla Bernardina (patrick.dalla@gmail.com)
 *
 */
public class PythonHook {

    private Jep jep;

    HashMap<String, HashMap<String, String>> modifications = new HashMap<String, HashMap<String, String>>();
    HashMap<String, List<String>> appends = new HashMap<String, List<String>>();
    HashSet<String> modulesToClear = new HashSet<String>();

    static HashMap<Jep, PythonHook> installedHooks = new HashMap<Jep, PythonHook>();

    /**
     * Installs hook on specified Jep instance
     */
    public static PythonHook installHook(Jep jep) {
        PythonHook result = installedHooks.get(jep);
        if (result == null) {
            result = new PythonHook(jep);
            installedHooks.put(jep, result);
        }
        return result;
    }

    public static PythonHook getHook(Jep jep) {
        return installedHooks.get(jep);
    }

    private PythonHook(Jep jep) {
        this.jep = jep;
        installHook();
    }

    public void addModification(String module, String regex, String replacement) {
        HashMap<String, String> modification = modifications.get(module);
        if (modification == null) {
            modification = new HashMap<String, String>();
            modifications.put(module, modification);
        }
        modification.put(regex, replacement);
    }

    public boolean hasModule(String module) {
        return modifications.get(module) != null || appends.get(module) != null;
    }

    public String processHook(String filename, String module, String source) {
        List<String> append = appends.get(module);
        if (modulesToClear.contains(module)) {
            source = "";
        } else {
            HashMap<String, String> modification = modifications.get(module);
            if (modification == null && append == null) {
                return null;
            }
            if (modification != null) {
                for (Entry<String, String> fileModifications : modification.entrySet()) {
                    source = source.replace(fileModifications.getKey(), fileModifications.getValue());
                }
            }
        }
        if (append != null) {
            for (String appendStr : append) {
                source = source.concat(appendStr);
            }
        }
        return source;
    }

    boolean fileOpenOverriden = false;
    public void overrideFileOpen(Method method) {
        if (!fileOpenOverriden) {
            String packageName = method.getDeclaringClass().getPackageName();
            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();

            jep.eval(contextWrapper);

            StringBuffer def = new StringBuffer("lambda *args, **kwargs:");
            def.append("ContextWrapper(");
            def.append(className + "." + methodName + "(");
            def.append("locals()['args'],locals()['kwargs']");
            def.append(")");
            def.append(")");

            // overrides open
            jep.eval("from " + packageName + " import " + className);
            jep.eval("globals()['__builtins__']['oldopen']=globals()['__builtins__']['open']");
            jep.eval("globals()['__builtins__']['open']=" + def);
            fileOpenOverriden = true;
        }
    }

    public void installHook() {
        jep.eval("import traceback");
        jep.eval("import sys");
        jep.eval("import importlib");
        jep.eval("import builtins");
        jep.eval("from java.lang import System");
        jep.eval("from importlib.util import spec_from_loader");
        jep.eval("from iped.utils.pythonhook import FileHook");
        jep.set("javahook", this);

        jep.eval(installHookClass);

        jep.eval("sys.meta_path.insert(0, ImphookFileLoader())");
        jep.eval("sys.path_importer_cache.clear()");
    }

    private static String installHookClass = "class ImphookFileLoader(object):\n"
            + "    def find_spec(self, fullname, paths, target=None):\n"
            + "        loaders = importlib._bootstrap_external._get_supported_file_loaders()\n"
            + "        fileLoader = importlib._bootstrap_external.FileFinder.path_hook(*loaders)\n"
            + "        foundPath=None\n"
            + "        if(paths):\n" 
            + "            for path in paths:\n" 
            + "                if('.zip\\\\' in path or '.zip/' in path):\n"
            + "                    return None\n"
            + "                if(fileLoader(path)):\n"
            + "                    foundPath=path\n"
            + "                    break\n" + "        if(foundPath):\n"
            + "            if(javahook.hasModule(fullname)):\n"
            + "                return spec_from_loader(fullname, self, origin=foundPath, is_package=True)\n"
            + "            else:\n" + "                return None\n"
            + "        else:\n"
            + "            return None\n"
            + "    def create_module(self, spec):\n"
            + "        modules=spec.name.split('.')\n"
            + "        with open(spec.origin+'/'+modules[len(modules)-1]+'.py',  encoding='utf8') as f:\n"
            + "            source = f.read()\n"   
            + "        source = javahook.processHook(spec.origin, spec.name, source)\n"
            + "        if(source == None):\n"
            + "            return None\n" 
            + "        else:\n"
            + "            mod = type(importlib)(\"\")\n"
            + "            exec(source, vars(mod))\n" 
            + "            return mod\n"
            + "\n"
            + "    def exec_module(self, mod):\n"
            + "        pass\n"
            + "";

    public void overrideModuleFunction(String module, String functionName, Method method) {
        String packageName = method.getDeclaringClass().getPackageName();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        StringBuffer params = new StringBuffer("");
        for (int i = 0; i < method.getParameterCount(); i++) {
            params.append("param" + i);
            if (i < method.getParameterCount()) {
                params.append(",");
            }
        }
        addModification(module, "def " + functionName, "def " + functionName
                + "(" + params + "):\n    from " + packageName + " import " + className + "\n    return " + className
                + "."
                + methodName + "(" + params + ")\ndef " + functionName + "2");
    }

    /*
     * Replaces a class declaration with the java implemented class. If the original
     * python class has methods with keywords, any call to it will raise keywords
     * not supported as this feature is not supported by JEP to call java object
     * methods
     */
    public void overrideClass(String module, String pythonClass, Class class1) {
        String packageName = class1.getPackageName();
        String className = class1.getSimpleName();
        addModification(module, "class " + pythonClass,
                "from " + packageName + " import " + className + " as " + pythonClass + "\n"
                        + "class " + pythonClass + "2");
    }

    /*
     * Replaces a class declaration with the java implemented class. If the original
     * python class has methods with keywords, any call to it will raise keywords
     * not supported as this feature is not supported by JEP to call java object
     * methods
     */
    public void wrapsClass(String module, String pythonClass, Class class1) {
        String packageName = class1.getPackageName();
        String className = class1.getSimpleName();
        addClearModule(module);
        addAppend(module, "from " + packageName + " import " + className + "\n");
        String append = "\n\nclass " + pythonClass + "(RemoveKeywordArgsWrapper):\n"
                + "    def __init__(self, *args):\n"
                + "        super().__init__(" + className + "(*args))\n";
        addAppend(module, "\n" + removeKeywordsWrapper);
        addAppend(module, append);
    }

    private void addClearModule(String module) {
        modulesToClear.add(module);
    }

    private void addAppend(String module, String str) {
        List<String> append = appends.get(module);
        if (append == null) {
            append = new ArrayList<String>();
            appends.put(module, append);
        }
        append.add(str);
    }

    private static String removeKeywordsWrapper = "class RemoveKeywordArgsWrapper:\n" + "    def __init__(self, w):\n"
            + "                self.wrapped = w\n" + "                pass\n"
            + "    def __getattribute__(self, name):\n" + "        if(name == \"wrapped\"):\n"
            + "            return object.__getattribute__(self, name)\n"
            + "        attr = object.__getattribute__(self.wrapped, name)\n" + "        if hasattr(attr, '__call__'):\n"
            + "            def newfunc(*args, **kwargs):\n"
            + "                a = list(args)\n" + "                a.insert(0,self.wrapped)\n"
            + "                args = tuple(a)\n"
            + "                result = attr(*args)\n"
            + "                return result\n" + "            return newfunc\n" + "        else:\n"
            + "            return attr\n" + "";

    private static String contextWrapper = "class ContextWrapper:\n" + "    def __init__(self, w):\n"
            + "                self.wrapped = w\n" + "                pass\n"
            + "    def __enter__(self):\n" + "        self.wrapped.enter()\n" + "        return self.wrapped\n"
            + "    def __exit__(self, *args):\n"
            + "        self.wrapped.exit(locals()['args'])\n"
            + "    def __getattribute__(self, name):\n" + "        if(name == \"wrapped\"):\n"
            + "            return object.__getattribute__(self, name)\n"
            + "        attr = object.__getattribute__(self.wrapped, name)\n" + "        if hasattr(attr, '__call__'):\n"
            + "            def newfunc(*args, **kwargs):\n" + "                a = list(args)\n"
            + "                a.insert(0,self.wrapped)\n" + "                args = tuple(a)\n"
            + "                result = attr(*args)\n" + "                return result\n"
            + "            return newfunc\n" + "        else:\n" + "            return attr\n" + "";

}