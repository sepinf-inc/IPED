package iped.app.graph;

import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import org.junit.Test;
import iped.app.ui.App;
import iped.engine.core.Manager;
import sun.misc.Unsafe;

public class LoadGraphDatabaseWorkerTest {
    private static class Probe extends AppGraphAnalytics {
        boolean disabled;
        @Override
        public void setEnabled(boolean enabled) {
            disabled = !enabled;
        }
    }

    @Test
    public void activeProcessingDefersDatabaseInitialization() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        // Model an early processing preview without starting Swing or a processing job.
        App app = (App) unsafe.allocateInstance(App.class);
        Manager manager = (Manager) unsafe.allocateInstance(Manager.class);
        manager.setProcessingFinished(false);
        Field managerField = App.class.getDeclaredField("processingManager");
        managerField.setAccessible(true);
        managerField.set(app, manager);
        Field singleton = App.class.getDeclaredField("app");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        try {
            singleton.set(null, app);
            Probe probe = (Probe) unsafe.allocateInstance(Probe.class);
            LoadGraphDatabaseWorker worker = new LoadGraphDatabaseWorker(probe);
            // There is deliberately no configuration or database available here.
            worker.doInBackground();
            Field awaiting = LoadGraphDatabaseWorker.class.getDeclaredField("awaitingProcessing");
            awaiting.setAccessible(true);
            assertTrue(awaiting.getBoolean(worker));
            assertTrue(probe.disabled);
        } finally {
            singleton.set(null, previous);
        }
    }
}
