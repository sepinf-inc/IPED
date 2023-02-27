package iped.app.ui.splash;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class StartUpControlClient {
    private volatile boolean finished;

    public void start() {
        Thread thread = new Thread() {
            public void run() {
                // Get current PID
                long pid = ProcessHandle.current().pid();

                // Sets the directory and file to communicate with the parent process
                // Folder/file are created/deleted in the client, that writes to the file
                File tmpFolder = StartUpControl.getTempFolder();
                if (!tmpFolder.exists()) {
                    try {
                        tmpFolder.mkdirs();
                    } catch (Exception e) {
                    }
                }
                File startUpFile = StartUpControl.getStartUpFile(tmpFolder, pid);
                if (startUpFile.exists()) {
                    try {
                        startUpFile.delete();
                    } catch (Exception e) {
                    }
                }

                // Write file
                try (BufferedWriter out = new BufferedWriter(new FileWriter(startUpFile))) {

                    // Update progress while starting up
                    while (!finished) {
                        int progress = StartUpControl.getCurrentProcessSize();
                        String line = progress + "\n";
                        try {
                            out.write(line);
                            out.flush();
                        } catch (Exception e) {
                        }
                        Thread.sleep(100);
                    }

                    // Start up process finished
                    out.write("END");
                    out.newLine();
                    out.flush();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public void finish() {
        finished = true;
    }
}
