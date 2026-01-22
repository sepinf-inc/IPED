/*
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
package iped.engine.task.carver;

import java.io.BufferedInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.io.SeekableInputStream;
import iped.parsers.emule.KnownMetDecoder;
import iped.parsers.emule.KnownMetEntry;
import iped.utils.IOUtil;

/**
 * Custom carving for e-Mule artifacts (known.met, part.met, and
 * preferences.dat).
 */
public class KnownMetCarveTask extends BaseCarveTask {

    private static final String ENABLE_PARAM = "enableKnownMetCarving";

    private static Logger logger = LoggerFactory.getLogger(KnownMetCarveTask.class);

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Indicador de inicialização, para controle de sincronização entre instâncias
     * da classe.
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Objeto estático para sincronizar finalização.
     */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * Contador de arquivos known.met recuperados.
     */
    private static final AtomicInteger numKnownMetItems = new AtomicInteger();

    /**
     * Contador de arquivos part.met recuperados.
     */
    private static final AtomicInteger numPartMetItems = new AtomicInteger();

    /**
     * Contador de arquivos preferences.dat recuperados.
     */
    private static final AtomicInteger numPreferencesDatItems = new AtomicInteger();

    /**
     * Media type dos arquivos recuperados.
     */

    private static final MediaType eMuleMediaType = MediaType.application("x-emule"); //$NON-NLS-1$

    /**
     * Media type dos arquivos part.met recuperados.
     */

    private static final MediaType eMulePartMetMediaType = MediaType.application("x-emule-part-met"); //$NON-NLS-1$


    /**
     * Media type dos arquivos preferences.dat recuperados.
     */
    private static final MediaType eMulePreferencesDatMediaType = MediaType.application("x-emule-preferences-dat"); //$NON-NLS-1$

    /**
     * Reading block size.
     */
    private final int blockSize = 4096;

    /**
     * Setor padrão usado para alinhamento de known.met/part.met.
     */
    private static final int SECTOR_SIZE = 512;

    /**
     * Tamanho da assinatura do arquivo preferences.dat.
     */
    private static final int PREFERENCES_DAT_SIGNATURE_LENGTH = 61;

    /**
     * Anchor offset for preferences.dat signature (0x2C 00 00 00).
     */
    private static final int PREFERENCES_DAT_ANCHOR_OFFSET = 16;

    /**
     * Anchor bytes used for Boyer-Moore-Horspool scan of preferences.dat.
     */
    private static final byte[] PREFERENCES_DAT_ANCHOR = new byte[] {(byte) 0x2C, 0x00, 0x00, 0x00};

    /**
     * Precomputed Boyer-Moore-Horspool shift table for preferences.dat anchor.
     */
    private static final int[] PREFERENCES_DAT_BMH_SHIFT = buildBmhTable(PREFERENCES_DAT_ANCHOR);

    /**
     * Heurística de data mínima utilizada para filtrar arquivos plausíveis.
     * Aproximadamente -20 anos.
     */
    private static final long dateMin = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 365 * 20;

    /**
     * Data máxima. Aproximadamente +5 anos.
     */
    private static final long dateMax = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 5;

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    /**
     * Inicializa tarefa, realizando controle de alocação de apenas uma thread
     * principal.
     */
    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                taskEnabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                if (taskEnabled) {
                    logger.info("Task enabled."); //$NON-NLS-1$
                } else {
                    logger.info("Task disabled."); //$NON-NLS-1$
                }
                init.set(true);
            }
        }
    }

    /**
     * Finaliza a tarefa.
     */
    @Override
    public void finish() throws Exception {
        synchronized (finished) {
            if (taskEnabled && !finished.get()) {
                finished.set(true);
                logger.info("Known Met Carved Items: " + numKnownMetItems.get()); //$NON-NLS-1$
                logger.info("Part Met Carved Items: " + numPartMetItems.get()); //$NON-NLS-1$
                logger.info("Preferences Dat Carved Items: " + numPreferencesDatItems.get()); //$NON-NLS-1$
                logger.info("Total carved eMule artifacts: " + //$NON-NLS-1$
                    (numKnownMetItems.get() + numPartMetItems.get() + numPreferencesDatItems.get()));
            }
        }
    }

    public void process(IItem evidence) {
        // Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || caseData.isIpedReport() || !isAcceptedType(evidence.getMediaType()) || !isToProcess(evidence)) {
            return;
        }

        // Percorre conteúdo buscando padrões plausíveis de arquivos known.met
        byte[] prevBuf = new byte[blockSize];
        byte[] currBuf = new byte[blockSize];
        byte[] buf2 = new byte[1 << 20];
        BufferedInputStream is = null;
        long offset = 0;
        try {
            is = evidence.getBufferedInputStream();
            int done = 0;
            int firstRead = is.readNBytes(currBuf, 0, blockSize);
            if (firstRead < blockSize) {
                done++;
                Arrays.fill(currBuf, Math.max(0, firstRead), blockSize, (byte) 0);
            }
            WindowBuffer window = new WindowBuffer(prevBuf, currBuf, blockSize);
            while (true) {
                byte[] tmp = prevBuf;
                prevBuf = currBuf;
                currBuf = tmp;
                int len = is.readNBytes(currBuf, 0, blockSize);
                if (len < blockSize) {
                    done++;
                    Arrays.fill(currBuf, Math.max(0, len), blockSize, (byte) 0);
                }
                window.reset(prevBuf, currBuf);
                int firstAligned = (int) ((SECTOR_SIZE - (offset & (SECTOR_SIZE - 1))) & (SECTOR_SIZE - 1));
                for (int pos = firstAligned; pos < blockSize; pos += SECTOR_SIZE) {
                    int read = window.getUnsigned(pos);

                    // Verifica se foi encontrado o padrão do arquivo known.met (0x0E or 0x0F)
                    if (read == 0x0E || read == 0x0F) {
                        checkKnownMet(is, evidence, pos + 1, offset + pos, window);
                    }
                    // Verifica se foi encontrado o padrão do arquivo part.met (0xE0 or 0xE2)
                    else if (read == 0xE0 || read == 0xE2) {
                        checkPartMet(is, evidence, pos + 1, offset + pos, window, buf2);
                    }
                }
                scanPreferencesDat(is, evidence, window, offset);
                if (done >= 2) {
                    break;
                }
                offset += blockSize;
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        } finally {
            IOUtil.closeQuietly(is);
        }
    }

    /**
     * Verifica se foi encontrado o padrão do arquivo known.met (0x0E or 0x0F)
     */
    private boolean checkKnownMet(BufferedInputStream is, IItem evidence, int pos, long offset,
                                   WindowBuffer window) throws Exception {
        int numFiles = toInt(window, pos);
        pos += 4;
        if (numFiles > 0 && numFiles < 65536) {
            long date = toInt(window, pos) * 1000L;
            if (date > dateMin && date < dateMax) {
                pos += 4;
                pos += 16;
                int numParts = toSmall(window, pos);
                pos += 2;
                pos += 16 * numParts;
                if (numParts >= 0 && numParts < 256 && pos < window.length() - 4) {
                    int numTags = toInt(window, pos);
                    if (numTags > 2 && numTags < 100) {
                        int len = 512 * numFiles;
                        SeekableInputStream inParse = null;
                        try {
                            inParse = evidence.getSeekableInputStream();
                            inParse.seek(offset);
                            List<KnownMetEntry> l = KnownMetDecoder.parseToList(inParse, len, true);
                            if (l != null && !l.isEmpty()) {
                                // Check if at least one entry has a defined name and file size (#2116)
                                boolean valid = false;
                                for (KnownMetEntry entry : l) {
                                    if (entry.getName() != null && entry.getFileSize() > 0) {
                                        valid = true;
                                        break;
                                    }
                                }
                                if (valid) {
                                    addCarvedFile(evidence, offset, len, "Carved-" + offset + "-known.met",
                                            eMuleMediaType);
                                    numKnownMetItems.incrementAndGet();
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            IOUtil.closeQuietly(inParse);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks for part.met file pattern and carves if valid.
     */
    private boolean checkPartMet(BufferedInputStream is, IItem evidence, int pos, long offset,
                                  WindowBuffer window, byte[] buf2) throws Exception {
        long date = toInt(window, pos) * 1000L;
        if (date > dateMin && date < dateMax) {
            pos += 20;
            int numParts = toSmall(window, pos);
            int numTags = 2;
            pos += 2;
            pos += 16 * numParts;
            if (pos < 500) {
                numTags = toInt(window, pos);
            }
            if (numTags >= 2 && numTags <= 1024 && numParts <= 4096 && numParts >= 0) {
                SeekableInputStream inParse = null;
                try {
                    inParse = evidence.getSeekableInputStream();
                    inParse.seek(offset);
                    int bytesRead = inParse.readNBytes(buf2, 0, buf2.length);
                    if (bytesRead > 25) {
                        KnownMetEntry entry = new KnownMetEntry();
                        int len = KnownMetDecoder.parseEntry(entry, 1, buf2, true);
                        if (len > 0) {
                            addCarvedFile(evidence, offset, len + 1, "Carved-" + offset + "-part.met", //$NON-NLS-1$ //$NON-NLS-2$
                                    eMulePartMetMediaType);
                            numPartMetItems.incrementAndGet();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    IOUtil.closeQuietly(inParse);
                }
            }
        }
        return false;
    }

    /**
     * Verifica se foi encontrado o padrão do arquivo preferences.dat (0x14)
     */
    private boolean checkPreferencesDat(BufferedInputStream is, IItem evidence, int pos, long offset,
                                         WindowBuffer window) throws Exception {
        if (matchesPreferencesDatQuick(window, pos) && matchesPreferencesDatSignature(window, pos)) {
            addCarvedFile(evidence, offset, PREFERENCES_DAT_SIGNATURE_LENGTH,
                    "Carved-" + offset + "-preferences.dat", //$NON-NLS-1$ //$NON-NLS-2$
                    eMulePreferencesDatMediaType);
            numPreferencesDatItems.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Boyer-Moore-Horspool scan for preferences.dat anchor bytes.
     */
    private void scanPreferencesDat(BufferedInputStream is, IItem evidence, WindowBuffer window, long offset) throws Exception {
        int anchorLen = PREFERENCES_DAT_ANCHOR.length;
        int maxStart = Math.min(blockSize - 1 + PREFERENCES_DAT_ANCHOR_OFFSET, window.length() - anchorLen);
        int i = PREFERENCES_DAT_ANCHOR_OFFSET;
        while (i <= maxStart) {
            int j = anchorLen - 1;
            while (j >= 0 && window.getUnsigned(i + j) == (PREFERENCES_DAT_ANCHOR[j] & 0xFF)) {
                j--;
            }
            if (j < 0) {
                int candidateStart = i - PREFERENCES_DAT_ANCHOR_OFFSET;
                if (candidateStart >= 0
                        && candidateStart + PREFERENCES_DAT_SIGNATURE_LENGTH <= window.length()
                        && window.getUnsigned(candidateStart) == 0x14) {
                    checkPreferencesDat(is, evidence, candidateStart + 1, offset + candidateStart, window);
                }
                i++;
            } else {
                i += PREFERENCES_DAT_BMH_SHIFT[window.getUnsigned(i + anchorLen - 1) & 0xFF];
            }
        }
    }

    /**
     * Checks if the bytes read match the preferences.dat signature.
     * Pattern: \14?????\0E????????\6F?\2C\00\00\00?\00\00\00?\00\00\00??????????????????\00\00??\00\00??\00\00??\00\00
     */
    private boolean matchesPreferencesDatSignature(WindowBuffer window, int pos) {
        // First byte already verified (0x14)
        // buf[0-4] = ????? (5 bytes - any value)

        // buf[5] = 0x0E (position 6 of the signature)
        if (window.getUnsigned(pos + 5) != 0x0E)
            return false;

        // buf[6-13] = ???????? (8 bytes - any value)

        // buf[14] = 0x6F (position 15 of the signature)
        if (window.getUnsigned(pos + 14) != 0x6F)
            return false;

        // buf[15] = ? (1 byte - any value)

        // buf[16] = 0x2C (position 17 of the signature)
        if (window.getUnsigned(pos + 16) != 0x2C)
            return false;

        // buf[17-19] = 0x00, 0x00, 0x00 (positions 18-20)
        if (window.getUnsigned(pos + 17) != 0x00 || window.getUnsigned(pos + 18) != 0x00
                || window.getUnsigned(pos + 19) != 0x00)
            return false;

        // buf[20] = ? (1 byte - any value)

        // buf[21-23] = 0x00, 0x00, 0x00 (positions 22-24)
        if (window.getUnsigned(pos + 21) != 0x00 || window.getUnsigned(pos + 22) != 0x00
                || window.getUnsigned(pos + 23) != 0x00)
            return false;

        // buf[24] = ? (1 byte - any value)

        // buf[25-27] = 0x00, 0x00, 0x00 (positions 26-28)
        if (window.getUnsigned(pos + 25) != 0x00 || window.getUnsigned(pos + 26) != 0x00
                || window.getUnsigned(pos + 27) != 0x00)
            return false;

        // buf[28-45] = ?????????????????? (18 bytes - any value)

        // buf[46-47] = 0x00, 0x00 (positions 47-48), but sometimes 0xFF, 0xFF
        if (((window.getUnsigned(pos + 46) != 0x00 || window.getUnsigned(pos + 47) != 0x00)
                && (window.getUnsigned(pos + 46) != 0xFF || window.getUnsigned(pos + 47) != 0xFF)))
            return false;

        // buf[48-49] = ?? (2 bytes - any value)

        // buf[50-51] = 0x00, 0x00 (positions 51-52)
        if (window.getUnsigned(pos + 50) != 0x00 || window.getUnsigned(pos + 51) != 0x00)
            return false;

        // buf[52-53] = ?? (2 bytes - any value)

        // buf[54-55] = 0x00, 0x00 (positions 55-56)
        if (window.getUnsigned(pos + 54) != 0x00 || window.getUnsigned(pos + 55) != 0x00)
            return false;

        // buf[56-57] = ?? (2 bytes - any value)

        // buf[58-59] = 0x00, 0x00 (positions 59-60)
        if (window.getUnsigned(pos + 58) != 0x00 || window.getUnsigned(pos + 59) != 0x00)
            return false;

        return true;
    }

    /**
     * Fast pre-check for preferences.dat signature to reduce full validations.
     */
    private boolean matchesPreferencesDatQuick(WindowBuffer window, int pos) {
        return window.getUnsigned(pos + 5) == 0x0E
                && window.getUnsigned(pos + 14) == 0x6F
                && window.getUnsigned(pos + 16) == 0x2C
                && window.getUnsigned(pos + 17) == 0x00
                && window.getUnsigned(pos + 18) == 0x00
                && window.getUnsigned(pos + 19) == 0x00;
    }

    private static final class WindowBuffer {
        private byte[] prev;
        private byte[] curr;
        private final int blockSize;

        private WindowBuffer(byte[] prev, byte[] curr, int blockSize) {
            this.prev = prev;
            this.curr = curr;
            this.blockSize = blockSize;
        }

        private void reset(byte[] prev, byte[] curr) {
            this.prev = prev;
            this.curr = curr;
        }

        private int getUnsigned(int pos) {
            return get(pos) & 0xFF;
        }

        private byte get(int pos) {
            if (pos < blockSize) {
                return prev[pos];
            }
            return curr[pos - blockSize];
        }

        private int length() {
            return blockSize * 2;
        }
    }

    private static final int toInt(WindowBuffer window, int offset) {
        return window.getUnsigned(offset) | (window.getUnsigned(offset + 1) << 8)
                | (window.getUnsigned(offset + 2) << 16) | (window.getUnsigned(offset + 3) << 24);
    }

    private static final int toSmall(WindowBuffer window, int offset) {
        return window.getUnsigned(offset) | (window.getUnsigned(offset + 1) << 8);
    }

    private static int[] buildBmhTable(byte[] pattern) {
        int[] table = new int[256];
        Arrays.fill(table, pattern.length);
        for (int i = 0; i < pattern.length - 1; i++) {
            table[pattern[i] & 0xFF] = pattern.length - 1 - i;
        }
        return table;
    }

    private static boolean isSectorAligned(long offset) {
        return (offset & (SECTOR_SIZE - 1)) == 0;
    }

    private static boolean isAcceptedType(MediaType mediaType) {
        return LedCarveTask.isAcceptedType(mediaType);
    }
}
