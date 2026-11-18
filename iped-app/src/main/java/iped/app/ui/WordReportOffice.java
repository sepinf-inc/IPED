package iped.app.ui;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ag.ion.bion.officelayer.application.IOfficeApplication;
import ag.ion.bion.officelayer.application.OfficeApplicationRuntime;
import ag.ion.bion.officelayer.document.DocumentDescriptor;
import ag.ion.bion.officelayer.document.IDocument;
import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.XStorable;
import com.sun.star.uno.UnoRuntime;
import iped.engine.config.Configuration;
import iped.viewers.util.LibreOfficeFinder;

/** Reuse IPED's Office integration in a private worker with the Office program on its classpath. */
public class WordReportOffice implements Closeable {

    private final Path work;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "word-report-office");
        thread.setDaemon(true);
        return thread;
    });
    private Process process;
    private DataOutputStream requests;
    private DataInputStream responses;
    private boolean closed;

    WordReportOffice(Path work) {
        this.work = work;
    }

    void warmUp() throws Exception {
        if (closed) {
            throw new IOException(Messages.getString("WordReport.PreviewFailed"));
        }
        if (process != null && process.isAlive()) {
            return;
        }
        Path executable = executable();
        Files.createDirectories(work);
        String classpath = executable.getParent() + File.pathSeparator + System.getProperty("java.class.path");
        Path java = Path.of(System.getProperty("java.home"), "bin", executable.getFileName().toString().endsWith(".exe") ? "java.exe" : "java");
        Path home = executable.getParent().getParent();
        if ("MacOS".equals(executable.getParent().getFileName().toString())) {
            home = home.getParent();
        }
        // Java expands IPED's many dependency jars; an argument file avoids Windows' command-line limit.
        Path arguments = work.resolve("office-worker.args");
        StringBuilder command = new StringBuilder();
        for (String value : Arrays.asList("-Duser.home=" + System.getProperty("user.home"),
                "-Djava.io.tmpdir=" + work.toAbsolutePath(), "-cp", classpath, WordReportOffice.class.getName(),
                home.toString(), work.toAbsolutePath().toString())) {
            command.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
        }
        Files.writeString(arguments, command, Charset.defaultCharset());
        process = new ProcessBuilder(java.toString(), "@" + arguments.toAbsolutePath())
                .redirectError(work.resolve("office.log").toFile()).start();
        requests = new DataOutputStream(process.getOutputStream());
        responses = new DataInputStream(process.getInputStream());
        awaitResponse();
    }

    Path convert(Path document, Path output) throws Exception {
        warmUp();
        requests.writeUTF(document.toAbsolutePath().toString());
        requests.writeUTF(output.toAbsolutePath().toString());
        requests.flush();
        awaitResponse();
        if (!Files.isRegularFile(output)) {
            throw new IOException(Messages.getString("WordReport.PreviewFailed"));
        }
        return output;
    }

    private void awaitResponse() throws Exception {
        try {
            DataInputStream input = responses;
            if (!reader.submit(() -> input.readBoolean()).get(300, TimeUnit.SECONDS)) {
                throw new IOException(Messages.getString("WordReport.PreviewFailed"));
            }
        } catch (Exception error) {
            stop();
            throw error;
        }
    }

    static Path executable() throws IOException {
        String home = System.getProperty("libreOfficePath");
        if (home == null) {
            home = new LibreOfficeFinder(new File(Configuration.getInstance().appRoot)).getLOPath(false);
        }
        if (home != null) {
            for (String relative : new String[] { "program/soffice.exe", "program/soffice", "Contents/MacOS/soffice", "soffice" }) {
                Path candidate = Path.of(home).resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IOException(Messages.getString("WordReport.NoRenderer"));
    }

    private void stop() {
        Process running = process;
        process = null;
        if (running != null) {
            running.descendants().forEach(ProcessHandle::destroyForcibly);
            running.destroyForcibly();
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try {
            if (requests != null && process != null && process.isAlive()) {
                requests.writeUTF("");
                requests.flush();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    stop();
                }
            }
        } catch (Exception error) {
            stop();
        } finally {
            reader.shutdownNow();
            if (requests != null) {
                requests.close();
            }
            if (responses != null) {
                responses.close();
            }
        }
    }

    /** Binary requests use only the parent's anonymous pipes; document data is never sent to a service. */
    public static void main(String[] args) throws Exception {
        DataOutputStream reply = new DataOutputStream(new FileOutputStream(FileDescriptor.out));
        System.setOut(new PrintStream(System.err));
        IOfficeApplication office = null;
        try (DataInputStream input = new DataInputStream(System.in)) {
            Path work = Path.of(args[1]);
            Path profile = Files.createDirectories(work.resolve("office-profile"));
            Map<String, Object> configuration = new HashMap<>();
            configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, args[0]);
            configuration.put(IOfficeApplication.APPLICATION_TYPE_KEY, IOfficeApplication.LOCAL_APPLICATION);
            configuration.put(IOfficeApplication.APPLICATION_ARGUMENTS_KEY, new String[] { "-env:UserInstallation=" + profile.toUri(),
                    "--headless", "--norestore", "--nodefault", "--nologo" });
            office = OfficeApplicationRuntime.getApplication(configuration);
            office.activate();
            reply.writeBoolean(true);
            reply.flush();
            while (true) {
                String source = input.readUTF();
                if (source.isEmpty()) {
                    break;
                }
                String output = input.readUTF();
                try {
                    DocumentDescriptor descriptor = new DocumentDescriptor();
                    descriptor.setHidden(true);
                    descriptor.setReadOnly(true);
                    descriptor.setMacroExecutionMode(com.sun.star.document.MacroExecMode.NEVER_EXECUTE);
                    IDocument loaded = office.getDocumentService().loadDocument(Path.of(source).toUri().toString(), descriptor);
                    try {
                        // Preserve source pixels without repeatedly recompressing screenshots as JPEG.
                        PropertyValue filter = new PropertyValue();
                        filter.Name = "FilterName";
                        filter.Value = "writer_pdf_Export";
                        PropertyValue compression = new PropertyValue();
                        compression.Name = "UseLosslessCompression";
                        compression.Value = true;
                        PropertyValue resolution = new PropertyValue();
                        resolution.Name = "ReduceImageResolution";
                        resolution.Value = false;
                        PropertyValue data = new PropertyValue();
                        data.Name = "FilterData";
                        data.Value = new PropertyValue[] { compression, resolution };
                        UnoRuntime.queryInterface(XStorable.class, loaded.getXComponent())
                                .storeToURL(Path.of(output).toUri().toString(), new PropertyValue[] { filter, data });
                    } finally {
                        loaded.close();
                    }
                    reply.writeBoolean(true);
                } catch (Exception error) {
                    reply.writeBoolean(false);
                }
                reply.flush();
            }
        } catch (EOFException closed) {
            // The parent exited; release only this worker's Office instance.
        } catch (Exception error) {
            reply.writeBoolean(false);
            reply.flush();
        } finally {
            if (office != null) {
                try {
                    if (office.isActive()) {
                        office.getDesktopService().terminate();
                    }
                } finally {
                    office.dispose();
                }
            }
        }
    }
}
