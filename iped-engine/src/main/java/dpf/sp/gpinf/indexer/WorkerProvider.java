package dpf.sp.gpinf.indexer;

import javax.swing.SwingWorker;

public class WorkerProvider {

    private static SwingWorker instance;

    public static SwingWorker getInstance() {
        return instance;
    }

    public static void setInstance(SwingWorker worker) {
        instance = worker;
    }

}
