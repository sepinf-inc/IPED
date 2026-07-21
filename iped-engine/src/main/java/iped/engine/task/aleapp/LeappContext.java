package iped.engine.task.aleapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.core.Worker;
import iped.engine.data.CaseData;
import jep.Jep;

public class LeappContext {

    private CaseData caseData;
    private Worker worker;
    private Jep jep;
    private IItem pluginItem;
    private List<IItemReader> foundFiles;
    private Map<String, String> translatedPaths = new HashMap<>();

    private static final ThreadLocal<LeappContext> threadLocal = new ThreadLocal<>();

    private LeappContext(CaseData caseData, Worker worker, Jep jep, IItem pluginItem, List<IItemReader> foundFiles) {
        this.caseData = caseData;
        this.worker = worker;
        this.jep = jep;
        this.foundFiles = foundFiles;
        this.pluginItem = pluginItem;
    }

    public static LeappContext create(CaseData caseData, Worker worker, Jep jep, IItem pluginItem, List<IItemReader> foundFiles) {
        LeappContext context = new LeappContext(caseData, worker, jep, pluginItem, foundFiles);
        threadLocal.set(context);
        return context;
    }

    public static void clear() {
        if (threadLocal.get() != null) {
            threadLocal.get().getTranslatedPaths().keySet().forEach(tempFile -> {
                try {
                    Files.deleteIfExists(Paths.get(tempFile));
                } catch (IOException e) {
                }
            });
            threadLocal.remove();
        }
    }

    public static LeappContext get() {
        return threadLocal.get();
    }

    public CaseData getCaseData() {
        return caseData;
    }

    public Worker getWorker() {
        return worker;
    }

    public Jep getJep() {
        return jep;
    }

    public IItem getPluginItem() {
        return pluginItem;
    }

    public List<IItemReader> getFoundFiles() {
        return foundFiles;
    }

    public Map<String, String> getTranslatedPaths() {
        return translatedPaths;
    }
}
