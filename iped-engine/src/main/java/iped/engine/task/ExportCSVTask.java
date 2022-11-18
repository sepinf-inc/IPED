/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.localization.Messages;
import iped.engine.lucene.analysis.CategoryTokenizer;
import iped.engine.util.Util;
import iped.utils.HashValue;
import iped.utils.IOUtil;

/**
 * Responsável por gerar arquivo CSV com as propriedades dos itens processados.
 */
public class ExportCSVTask extends AbstractTask {

    private static final String ENABLE_PARAM = "exportFileProps"; //$NON-NLS-1$

    private static final String CSV_NAME = Messages.getString("ExportCSVTask.CsvName"); //$NON-NLS-1$
    private static final String HEADER = Messages.getString("ExportCSVTask.CsvColNames"); //$NON-NLS-1$
    private static final String SEPARATOR = Messages.getString("ExportCSVTask.CsvSeparator"); //$NON-NLS-1$
    private static final String LINK_FUNCTION = Messages.getString("ExportCSVTask.LinkFunction"); //$NON-NLS-1$
    private static final String LINK_NAME = Messages.getString("ExportCSVTask.LinkName"); //$NON-NLS-1$
    private static final int MIN_FLUSH_SIZE = 1 << 23;

    private static boolean exportFileProps = false;
    private static StringBuilder staticList = new StringBuilder();

    private CmdLineArgs args;
    private File tmp;

    /**
     * Indica que itens ignorados, como duplicados ou conhecidos (hash), devem ser
     * listados no arquivo CSV.
     *
     * @return true
     */
    @Override
    protected boolean processIgnoredItem() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return exportFileProps;
    }

    @Override
    protected void process(IItem evidence) throws IOException {

        if (!exportFileProps || (caseData.isIpedReport() && !evidence.isToAddToCase())) {
            return;
        }

        StringBuilder list = new StringBuilder();

        String value = evidence.getName();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        if (IOUtil.hasFile(evidence)) {
            value = evidence.getIdInDataSource();
        } else {
            value = ""; //$NON-NLS-1$
        }
        if (!value.isEmpty() && caseData.containsReport() && evidence.isToAddToCase() && !evidence.isToIgnore()) {
            value = "=" + LINK_FUNCTION + "(\"" + value + "\"" + SEPARATOR + "\"" + LINK_NAME + "\")";
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        Long length = evidence.getLength();
        if (length == null) {
            value = ""; //$NON-NLS-1$
        } else {
            value = length.toString();
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = evidence.getExt();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = Util.concatStrings(evidence.getLabels());
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = evidence.getCategories().replace("" + CategoryTokenizer.SEPARATOR, " | "); //$NON-NLS-1$ //$NON-NLS-2$
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = (String) evidence.getExtraAttribute(HashTask.HASH.MD5.toString());
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = (String) evidence.getExtraAttribute(HashTask.HASH.SHA1.toString());
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = Boolean.toString(evidence.isDeleted());
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = Boolean.toString(evidence.isCarved());
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        Date date = evidence.getAccessDate();
        if (date == null) {
            value = ""; //$NON-NLS-1$
        } else {
            value = date.toString();
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        date = evidence.getModDate();
        if (date == null) {
            value = ""; //$NON-NLS-1$
        } else {
            value = date.toString();
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        date = evidence.getCreationDate();
        if (date == null) {
            value = ""; //$NON-NLS-1$
        } else {
            value = date.toString();
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        value = evidence.getPath();
        if (value == null) {
            value = ""; //$NON-NLS-1$
        }
        list.append("\"" + escape(value) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        list.append(SEPARATOR);

        String trackID = Util.getTrackID(evidence);
        list.append("\"").append(trackID).append("\"");

        list.append("\r\n"); //$NON-NLS-1$

        synchronized (this.getClass()) {
            staticList.append(list);
            if (staticList.length() >= MIN_FLUSH_SIZE) {
                flush(output);
            }
        }
    }

    private String escape(String value) {
        StringBuilder str = new StringBuilder();
        for (char c : value.trim().toCharArray())
            if (c >= '\u0020' && !(c >= '\u007F' && c <= '\u009F'))
                str.append(c);

        return str.toString().replace("\"", "\"\""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static synchronized void flush(File output) throws IOException {
        if (!output.exists()) {
            writeHeader(output);
        }
        try (OutputStream os = Files.newOutputStream(output.toPath(), StandardOpenOption.APPEND)) {
            os.write(staticList.toString().getBytes(StandardCharsets.UTF_8));
        }
        staticList = new StringBuilder();
    }

    private static void writeHeader(File file) throws IOException {
        try (OutputStream os = Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW);
                Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            byte[] utf8bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
            os.write(utf8bom);
            writer.write(HEADER);
        }
    }

    public static void commit(File moduleDir) throws IOException {
        if (!exportFileProps)
            return;
        File csv = new File(moduleDir.getParentFile(), CSV_NAME);
        flush(csv);
        Util.fsync(csv.toPath());
    }

    public void finish() throws IOException {
        if (exportFileProps && staticList != null) {
            flush(output);
            staticList = null;

            if (!args.isContinue() && !args.isRestart())
                return;

            // clean duplicate entries in csv
            try (BufferedWriter writer = Files.newBufferedWriter(tmp.toPath(), StandardOpenOption.CREATE);
                    BufferedReader reader = Files.newBufferedReader(output.toPath())) {
                HashSet<HashValue> added = new HashSet<>();
                String line = null;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    HashValue trackID = null;
                    if (!header) {
                        int idx = line.lastIndexOf(SEPARATOR + "\"");
                        trackID = new HashValue(line.substring(idx + 2, line.length() - 1));
                    }
                    if (header || added.add(trackID)) {
                        writer.write(line);
                        writer.write("\r\n");
                    }
                    header = false;
                }
            }
            output.delete();
            tmp.renameTo(output);
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        EnableTaskProperty result = ConfigurationManager.get().findObject(EnableTaskProperty.class);
        if(result == null) {
            result = new EnableTaskProperty(ENABLE_PARAM);
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        this.output = new File(output.getParentFile(), CSV_NAME);

        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if (output.exists() && !args.isAppendIndex() && !args.isContinue() && !args.isRestart()) {
            Files.delete(output.toPath());
        }

        tmp = new File(output.getAbsolutePath() + ".tmp");
        if (tmp.exists()) {
            Files.delete(tmp.toPath());
        }

        exportFileProps = configurationManager.getEnableTaskProperty(ENABLE_PARAM);

    }

}
