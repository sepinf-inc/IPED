package iped.app.timelinegraph.datasets;

/* Dataset that implements methods to check if it is already loaded for use */
public interface AsynchronousDataset {

    public boolean waitLoaded(); // pauses thread until loading ends. returns true if load ended without
                                 // cancelation

    public void cancel();

    public void notifyVisibleRange(double lowerBound, double upperBound);

}
