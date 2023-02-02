package iped.app.ui.splash;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class StartUpControlServer {
    private volatile boolean finished;
    private volatile int progress;

    public void start() {
        Thread thread = new Thread() {
            public void run() {
                // Wait unit child process PID is available
                long pid = -1;
                while (true) {
                    String val = System.getProperty(StartUpControl.ipedChildProcessPID);
                    if (val != null) {
                        try {
                            pid = Long.parseLong(val);
                            break;
                        } catch (NumberFormatException e) {
                        }
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                    }
                }

                // Sets the directory and file to communicate with child process
                File tmpFolder = StartUpControl.getTempFolder();
                File startUpFile = StartUpControl.getStartUpFile(tmpFolder, pid);
                startUpFile.deleteOnExit();

                // Wait for the control file
                while (!startUpFile.exists()) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                }

                // Read file
                try (BufferedReader in = new BufferedReader(new FileReader(startUpFile))) {
                    while (true) {
                        String line = in.readLine();
                        if (line != null) {
                            try {
                                if (line.equals("END")) {
                                    finished = true;
                                    break;
                                }
                                progress = Integer.parseInt(line);
                            } catch (Exception e) {
                            }
                        }
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Delete file in the server, as reading is after writing.
                try {
                    startUpFile.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isFinished() {
        return finished;
    }

    public int getProgress() {
        return progress;
    }
}
