package iped.engine.task.leapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.core.Worker;
import jep.Jep;

public class LeappContext {

    private LeappTool tool;
    private FileSeeker fileSeeker;
    private Worker worker;
    private PluginSpec plugin;
    private Jep jep;
    private IItem pluginItem;
    
    private List<IItemReader> foundFiles;

    // data registered by the plugin through check_in_embedded_media, by the reference handed back to it
    private final Map<String, EmbeddedMedia> embeddedMedia = new HashMap<>();

    /** A media that is not a file of the extraction, but data stored inside one (see IlapfuncsEmbeddedMediaInterceptor). */
    public static class EmbeddedMedia {
        public final byte[] data;
        public final String name;
        public final String extension;

        public EmbeddedMedia(byte[] data, String name, String extension) {
            this.data = data;
            this.name = name;
            this.extension = extension;
        }
    }

    private static final ThreadLocal<LeappContext> threadLocal = new ThreadLocal<>();

    private LeappContext(LeappTool tool, FileSeeker seeker, Worker worker, Jep jep, PluginSpec plugin,  IItem pluginItem, List<IItemReader> foundFiles) {
        this.tool = tool;
        this.fileSeeker = seeker;
        this.worker = worker;
        this.plugin = plugin;
        this.jep = jep;
        this.foundFiles = foundFiles;
        this.pluginItem = pluginItem;
    }

    public static LeappContext create(LeappTool tool, FileSeeker seeker, Worker worker, Jep jep, PluginSpec plugin, IItem pluginItem, List<IItemReader> foundFiles) {
        LeappContext context = new LeappContext(tool, seeker, worker, jep, plugin, pluginItem, foundFiles);
        threadLocal.set(context);
        return context;
    }

    public static void clear() {
        threadLocal.remove();
    }

    public static LeappContext get() {
        return threadLocal.get();
    }

    public LeappTool getTool() {
        return tool;
    }

    public FileSeeker getFileSeeker() {
        return fileSeeker;
    }

    public Worker getWorker() {
        return worker;
    }

    public PluginSpec getPlugin() {
        return plugin;
    }

    public Jep getJep() {
        return jep;
    }

    public IItem getPluginItem() {
        return pluginItem;
    }

    public Map<String, EmbeddedMedia> getEmbeddedMedia() {
        return embeddedMedia;
    }

    public List<IItemReader> getFoundFiles() {
        return foundFiles;
    }

}
