package iped.viewers.timelinegraph.datasets;

public interface AsynchronousDataset {
	
	public boolean waitLoaded(); //pauses thread until loading ends. returns true if load ended without cancelation
	public void cancel();

}
