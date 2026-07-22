package iped.engine.task.aleapp;

import java.util.List;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.core.Worker;
import jep.Jep;

public class LeappContext {

    private FileSeeker fileSeeker;
    private Worker worker;
    private Jep jep;
    private IItem pluginItem;
    
    private List<IItemReader> foundFiles;

    private static final ThreadLocal<LeappContext> threadLocal = new ThreadLocal<>();

    private LeappContext(FileSeeker seeker, Worker worker, Jep jep, IItem pluginItem, List<IItemReader> foundFiles) {
        this.fileSeeker = seeker;
        this.worker = worker;
        this.jep = jep;
        this.foundFiles = foundFiles;
        this.pluginItem = pluginItem;
    }

    public static LeappContext create(FileSeeker seeker, Worker worker, Jep jep, IItem pluginItem, List<IItemReader> foundFiles) {
        LeappContext context = new LeappContext(seeker, worker, jep, pluginItem, foundFiles);
        threadLocal.set(context);
        return context;
    }

    public static void clear() {
        threadLocal.remove();
    }

    public static LeappContext get() {
        return threadLocal.get();
    }

    public FileSeeker getFileSeeker() {
        return fileSeeker;
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

}
