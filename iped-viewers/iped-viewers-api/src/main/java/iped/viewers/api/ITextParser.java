package iped.viewers.api;

import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.TreeMap;

public interface ITextParser {

    public FileChannel getParsedFile();

    public void setParsedFile(FileChannel file);

    public boolean cancel(boolean value);

    public void execute();

    public TreeMap<Long, int[]> getSortedHits();

    public void setSortedHits(TreeMap<Long, int[]> hits);

    public ArrayList<Long> getHits();

    public void setHits(ArrayList<Long> hits);

    public ArrayList<Long> getViewRows();

    public void setViewRows(ArrayList<Long> viewRows);

    public IProgressMonitor getProgressMonitor();

    public void setProgressMonitor(IProgressMonitor monitor);

    public boolean getFirstHitAutoSelected();

    public void setFirstHitAutoSelected(boolean val);

}
