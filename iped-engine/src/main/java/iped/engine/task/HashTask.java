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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.HashTaskConfig;
import iped.parsers.whatsapp.WhatsAppParser;

/**
 * Classe para calcular e manipular hashes.
 */
public class HashTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(HashTask.class);

    private static final int HASH_BUFFER_LEN = 1024 * 1024;

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public enum HASH {
        MD5("md5"), //$NON-NLS-1$
        SHA1("sha-1"), //$NON-NLS-1$
        SHA256("sha-256"), //$NON-NLS-1$
        SHA512("sha-512"), //$NON-NLS-1$
        EDONKEY("edonkey"); //$NON-NLS-1$

        private String name;

        HASH(String val) {
            this.name = val;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private HashMap<String, MessageDigest> digestMap = new LinkedHashMap<String, MessageDigest>();

    private HashTaskConfig hashConfig;

    @Override
    public boolean isEnabled() {
        return hashConfig.isEnabled();
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new HashTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        hashConfig = configurationManager.findObject(HashTaskConfig.class);

        for (String algorithm : hashConfig.getAlgorithms()) {
            MessageDigest digest = null;
            if (!algorithm.equalsIgnoreCase(HASH.EDONKEY.toString())) {
                digest = MessageDigest.getInstance(algorithm.toUpperCase());
            } else {
                digest = MessageDigest.getInstance("MD4"); //$NON-NLS-1$
            }
            digestMap.put(algorithm, digest);
            if (HASH.SHA256.toString().equals(algorithm)) {
                System.setProperty(WhatsAppParser.SHA256_ENABLED_SYSPROP, Boolean.TRUE.toString());
            }
        }
        if (isEnabled()) {
            System.setProperty(WhatsAppParser.HASH_TASK_ENABLED_SYSPROP, Boolean.TRUE.toString());
        }
    }

    @Override
    public void finish() throws Exception {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public void process(IItem evidence) {

        if (evidence.isQueueEnd()) {
            return;
        }

        if (evidence.getHash() != null || digestMap.isEmpty()
                || evidence.getExtraAttribute(IgnoreHardLinkTask.IGNORE_HARDLINK_ATTR) != null) {
            return;
        }

        if (evidence.getLength() == null) {
            evidence.setHash("");
            return;
        }

        try (InputStream in = evidence.getBufferedInputStream()) {

            byte[] readBuf = new byte[HASH_BUFFER_LEN];
            byte[] hashBuf = new byte[HASH_BUFFER_LEN];
            byte[] tempBuf = null;
            int len;

            AtomicReference<CountDownLatch> countDown = new AtomicReference<>(null);
            AtomicReference<Exception> ex = new AtomicReference<Exception>(null);

            while ((len = in.read(readBuf)) >= 0 && !Thread.currentThread().isInterrupted()) {

                if (countDown.get() != null) {
                    countDown.get().await();
                }

                countDown.set(new CountDownLatch(digestMap.size()));

                // swap hashBuf <-> readBuf
                tempBuf = hashBuf;
                hashBuf = readBuf;
                readBuf = tempBuf;

                final int currLen = len;
                final byte[] currHashBuf = hashBuf;
                for (String algo : digestMap.keySet()) {
                    executorService.execute(() -> {
                        try {
                            if (!algo.equals(HASH.EDONKEY.toString())) {
                                digestMap.get(algo).update(currHashBuf, 0, currLen);
                            } else {
                                updateEd2k(currHashBuf, currLen);
                            }
                        } catch (Exception e) {
                            ex.set(e);
                        } finally {
                            countDown.get().countDown();
                        }
                    });
                }

                if (ex.get() != null) {
                    throw ex.get();
                }
            }

            if (countDown.get() != null) {
                countDown.get().await();
            }

            boolean defaultHash = true;
            for (String algo : digestMap.keySet()) {
                byte[] hash;
                if (!algo.equals(HASH.EDONKEY.toString())) {
                    hash = digestMap.get(algo).digest();
                } else {
                    hash = digestEd2k();
                }

                String hashString = getHashString(hash);
                evidence.setExtraAttribute(algo, hashString);

                if (defaultHash) {
                    evidence.setHash(hashString);
                }
                defaultHash = false;
            }

        } catch (Exception e) {
            if (e instanceof IOException) {
                evidence.setExtraAttribute("ioError", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                stats.incIoErrors();
            }
            LOGGER.warn("{} Error computing hash {}\t{}", Thread.currentThread().getName(), evidence.getPath(), //$NON-NLS-1$
                    e.toString());
            // e.printStackTrace();

        }

    }

    private static int CHUNK_SIZE = 9500 * 1024;
    private int chunk = 0, total = 0;
    private ByteArrayOutputStream out = new ByteArrayOutputStream();

    private void updateEd2k(byte[] buffer, int len) throws IOException {

        MessageDigest md4 = digestMap.get(HASH.EDONKEY.toString());
        if (chunk + len >= CHUNK_SIZE) {
            int offset = CHUNK_SIZE - chunk;
            md4.update(buffer, 0, offset);
            out.write(md4.digest());
            chunk = len - offset;
            md4.update(buffer, offset, chunk);
        } else {
            md4.update(buffer, 0, len);
            chunk += len;
        }
        total += len;
    }

    private byte[] digestEd2k() throws IOException {

        MessageDigest md4 = digestMap.get(HASH.EDONKEY.toString());
        if (total == 0 || total % CHUNK_SIZE != 0) {
            out.write(md4.digest());
        }

        if (out.size() > md4.getDigestLength()) {
            md4.update(out.toByteArray());
            out.reset();
            out.write(md4.digest());
        }

        byte[] ed2k = out.toByteArray();

        chunk = 0;
        total = 0;
        out = new ByteArrayOutputStream();

        return ed2k;
    }

    public static String getHashString(byte[] hash) {
        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%1$02X", b)); //$NON-NLS-1$
        }

        return result.toString();
    }

}
