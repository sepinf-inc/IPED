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
package dpf.sp.gpinf.indexer.desktop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.apache.lucene.document.Document;

import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItemId;

public class CopiarPropriedades extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {

    ArrayList<Integer> uniqueIds;
    ProgressMonitor progressMonitor;
    File file;
    int total;

    public CopiarPropriedades(File file, ArrayList<Integer> uniqueIds) {
        this.file = file;
        this.uniqueIds = uniqueIds;
        this.total = uniqueIds.size();

        progressMonitor = new ProgressMonitor(App.get(), "", "", 0, total); //$NON-NLS-1$ //$NON-NLS-2$
        this.addPropertyChangeListener(this);
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8"); //$NON-NLS-1$
        byte[] utf8bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        fos.write(utf8bom);

        ArrayList<String> fields = new ArrayList<String>();
        for (String field : ResultTableModel.fields) {
            fields.add(field);
        }

        for (int col = 0; col < fields.size(); col++) {
            writer.write(
                    "\"" + fields.get(col).toUpperCase() + "\"" + Messages.getString("CopyProperties.CSVDelimiter")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        writer.write("\r\n"); //$NON-NLS-1$

        int progress = 0;
        App app = App.get();
        SimpleDateFormat df = new SimpleDateFormat(Messages.getString("CopyProperties.CSVDateFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
        for (Integer docId : uniqueIds) {
            this.firePropertyChange("progress", progress, ++progress); //$NON-NLS-1$
            try {
                Document doc = App.get().appCase.getSearcher().doc(docId);
                for (int col = 0; col < fields.size(); col++) {
                    String[] values = new String[1];
                    String field = fields.get(col);
                    if (!field.equals(ResultTableModel.BOOKMARK_COL)) {
                        values = doc.getValues(fields.get(col));
                    } else {
                        IItemId item = App.get().appCase.getItemId(docId);
                        values[0] = Util.concatStrings(App.get().appCase.getMultiMarcadores().getLabelList(item));
                    }
                    if (values.length > 0 && values[0] == null)
                        values[0] = ""; //$NON-NLS-1$

                    if (field.equals(IndexItem.CATEGORY))
                        for (String val : values)
                            val = val.replace("" + CategoryTokenizer.SEPARATOR, " | "); //$NON-NLS-1$ //$NON-NLS-2$

                    if (values.length > 0 && !values[0].isEmpty()
                            && (field.equals(IndexItem.ACCESSED) || field.equals(IndexItem.CREATED)
                                    || field.equals(IndexItem.MODIFIED) || field.equals(IndexItem.CHANGED))) {
                        values[0] = df.format(DateUtil.stringToDate(values[0]));
                    }
                    String value = ""; //$NON-NLS-1$
                    for (int i = 0; i < values.length; i++) {
                        if (i != 0)
                            value += " | "; //$NON-NLS-1$
                        value += values[i];
                    }

                    String escapedVal = value.replace("\"", "\"\"").replaceAll("\r|\n", " "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    writer.write("\"" + escapedVal + "\"" + Messages.getString("CopyProperties.CSVDelimiter")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                writer.write("\r\n"); //$NON-NLS-1$

            } catch (Exception e1) {
                e1.printStackTrace();
            }

            if (this.isCancelled()) {
                break;
            }
        }
        writer.close();
        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (progressMonitor.isCanceled()) {
            this.cancel(true);
        } else if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
            int progress = (Integer) evt.getNewValue();
            progressMonitor.setProgress(progress);
            progressMonitor.setNote(Messages.getString("CopyProperties.Copying") + progress //$NON-NLS-1$
                    + Messages.getString("CopyProperties.from") + total); //$NON-NLS-1$
        }

    }

}
