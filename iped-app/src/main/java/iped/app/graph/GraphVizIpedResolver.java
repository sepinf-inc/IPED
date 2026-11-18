package iped.app.graph;

import java.io.IOException;

import javax.swing.JOptionPane;

import org.kharon.layout.graphviz.GraphVizAlgorithm;
import org.kharon.layout.graphviz.GraphVizResolver;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.engine.config.Configuration;
import iped.utils.IOUtil;

public class GraphVizIpedResolver implements GraphVizResolver {

    @Override
    public String resolveBinaryPath(GraphVizAlgorithm algo) {
        String path;
        if (isWindows()) {
            path = Configuration.getInstance().appRoot + "/tools/graphviz/" + algo.getCmd() + ".exe";
        } else {
            path = algo.getCmd();
        }
        checkGraphVizPresence(path);
        return path;
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        if (os == null) {
            throw new IllegalStateException("os.name");
        }
        os = os.toLowerCase();
        return os.startsWith("windows");
    }

    private void checkGraphVizPresence(String cmd) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);
        pb.command(cmd, "-V");
        try {
            Process p = pb.start();
            IOUtil.ignoreInputStream(p);
            p.waitFor();
            if (p.exitValue() != 0)
                throw new IOException();

        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(App.get(), Messages.getString("GraphAnalysis.GraphvizError", cmd), "Error",
                    JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Error testing graphviz.", e);
        }
    }

}
