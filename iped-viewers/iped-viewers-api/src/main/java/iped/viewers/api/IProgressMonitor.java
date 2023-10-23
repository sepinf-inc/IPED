package iped.viewers.api;

public interface IProgressMonitor {

    void setProgress(long progress);

    void setNote(String note);

    void close();

    void setMaximum(long extraAttribute);

}
