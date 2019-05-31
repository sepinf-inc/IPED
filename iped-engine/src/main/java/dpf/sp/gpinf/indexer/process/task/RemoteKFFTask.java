/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.binary.Hex;

import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.process.Worker;
import iped3.IItem;

/**
 *
 * @author Fredim
 */
public class RemoteKFFTask extends AbstractTask {

    private MessageDigest digestMD5_512 = null;
    private MessageDigest digestMD5_64k = null;
    private static final int LIST_SIZE = 1000;
    private List<IItem> listItem = new ArrayList<>();
    int count = 0;
    private boolean addedToList = false;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        digestMD5_512 = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
        digestMD5_64k = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
    }

    @Override
    public void finish() throws Exception {

    }

    @Override
    protected void process(IItem evidence) throws Exception {
        InputStream in = evidence.getStream();
        try {
            String[] partialHashes = partialMd5Digest(in);
            evidence.setExtraAttribute("MD5_512", partialHashes[0]); //$NON-NLS-1$
            evidence.setExtraAttribute("MD5_64K", partialHashes[1]); //$NON-NLS-1$

            // System.out.println(evidence.getPath()+ " " + partialHashes[0]);
        } finally {
            in.close();
        }

        if ((!evidence.isQueueEnd()) && (!evidence.isDir()) && (!evidence.isRoot())
                && ((evidence.getMediaType() == null)
                        || (evidence.getMediaType().equals(SleuthkitReader.UNALLOCATED_MIMETYPE)))) {
            listItem.add(evidence);
            addedToList = true;
        } else {
            addedToList = false;
        }

        if ((listItem.size() == LIST_SIZE) || (evidence.isQueueEnd())) {
            if (listItem.size() > 0) {
                saveListItem(listItem);
            }
            // zipar
            // enviar
            // receber
            // unzipar
            // tratar
        }

    }

    @Override
    protected void sendToNextTask(IItem evidence) throws Exception {

        if ((listItem.size() == LIST_SIZE) || (evidence.isQueueEnd())) {
            for (IItem item : listItem) {
                // System.out.println(worker.getName() + " Enviando item: " + item.getId());
                super.sendToNextTask(item);
            }
            listItem.clear();
        }
        if ((!addedToList)) {
            super.sendToNextTask(evidence);
        }
        // System.out.println("Worker " + worker.getName() + " Lista: " +
        // listEvidenceFile.size());

    }

    private String[] partialMd5Digest(final InputStream is) throws IOException {
        digestMD5_512.reset();
        digestMD5_64k.reset();

        byte[] buffer = new byte[65536];
        int read = 0;
        int lsize = 0;
        while (read != -1 && (lsize += read) < buffer.length) {
            read = is.read(buffer, lsize, buffer.length - lsize);
        }
        if (lsize < 512) {
            digestMD5_512.update(buffer, 0, lsize);
        } else {
            digestMD5_512.update(buffer, 0, 512);
        }
        digestMD5_64k.update(buffer, 0, lsize);

        byte[] d_512 = digestMD5_512.digest();
        byte[] d_64k = digestMD5_64k.digest();

        String md5_512 = new String(Hex.encodeHex(d_512));
        String md5_64k = new String(Hex.encodeHex(d_64k));

        return new String[] { md5_512, md5_64k };
    }

    private void saveListItem(List<IItem> listItem) throws Exception {
        File fileTmp;

        fileTmp = File.createTempFile("tmp", ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
        PrintWriter out = new PrintWriter(fileTmp);
        for (IItem item : listItem) {
            String partialMD5_512 = (String) item.getExtraAttribute("MD5_512"); //$NON-NLS-1$
            String partialMD5_64k = (String) item.getExtraAttribute("MD5_64K"); //$NON-NLS-1$
            String fullPath = (String) item.getPath();
            String fileSize = Long.toString(item.getLength());
            out.println(item.getId() + ";" + item.getExtraAttribute("MD5_512") + ";" + item.getExtraAttribute("MD5_64K") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    + ";" + item.getPath() + ";" + item.getLength()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        out.close();
        System.out.println(fileTmp.getAbsolutePath());
    }

}
