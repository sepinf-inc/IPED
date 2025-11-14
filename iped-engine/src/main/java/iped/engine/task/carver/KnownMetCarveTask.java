/*
 * Copyright 2012-2016, Wladimir Leite
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
 * Tarefa específica para carving de arquivos known.met, part.met e preferences.dat do e-Mule.
 *
 * @author Wladimir Leite
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
    private final int blockSize = 512;

    /**
     * Tamanho da assinatura do arquivo preferences.dat.
     */
    private static final int PREFERENCES_DAT_SIGNATURE_LENGTH = 61;

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
        byte[] buf = new byte[blockSize * 2];
        byte[] buf2 = new byte[1 << 20];
        BufferedInputStream is = null;
        long offset = 0;
        try {
            is = evidence.getBufferedInputStream();
            int done = 0;
            if (is.readNBytes(buf, blockSize, blockSize) < blockSize) {
                done++;
            }
            while (true) {
                System.arraycopy(buf, blockSize, buf, 0, blockSize);
                int len = is.readNBytes(buf, blockSize, blockSize);
                if (len < blockSize) {
                    done++;
                    Arrays.fill(buf, blockSize + len, 2 * blockSize, (byte) 0);
                }
                for (int pos = 0; pos < blockSize; pos++) {
                    int read = buf[pos] & 0xFF;

                    // Verifica se foi encontrado o padrão do arquivo known.met (0x0E or 0x0F)
                    if (read == 0x0E || read == 0x0F) {
                        checkKnownMet(is, evidence, pos + 1, offset + pos, buf);
                    }
                    // Verifica se foi encontrado o padrão do arquivo part.met (0xE0 or 0xE2)
                    else if (read == 0xE0 || read == 0xE2) {
                        checkPartMet(is, evidence, pos + 1, offset + pos, buf, buf2);
                    }
                    // Verifica se foi encontrado o padrão do arquivo preferences.dat (0x14)
                    else if (read == 0x14) {
                        checkPreferencesDat(is, evidence, pos + 1, offset + pos, buf);
                    }
                }
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
                                   byte[] buf) throws Exception {
        int numFiles = toInt(buf, pos);
        pos += 4;
        if (numFiles > 0 && numFiles < 65536) {
            long date = toInt(buf, pos) * 1000L;
            if (date > dateMin && date < dateMax) {
                pos += 4;
                pos += 16;
                int numParts = toSmall(buf, pos);
                pos += 2;
                pos += 16 * numParts;
                if (pos < 500) {
                    int numTags = toInt(buf, pos);
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
                                  byte[] buf, byte[] buf2) throws Exception {
        long date = toInt(buf, pos) * 1000L;
        if (date > dateMin && date < dateMax) {
            pos += 20;
            int numParts = toSmall(buf, pos);
            int numTags = 2;
            pos += 2;
            pos += 16 * numParts;
            if (pos < 500) {
                numTags = toInt(buf, pos);
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
    private boolean checkPreferencesDat(BufferedInputStream is, IItem evidence, int pos, long offset, byte[] buf) throws Exception {
        if (matchesPreferencesDatSignature(buf, pos)) {
            addCarvedFile(evidence, offset, PREFERENCES_DAT_SIGNATURE_LENGTH,
                    "Carved-" + offset + "-preferences.dat", //$NON-NLS-1$ //$NON-NLS-2$
                    eMulePreferencesDatMediaType);
            numPreferencesDatItems.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Checks if the bytes read match the preferences.dat signature.
     * Pattern: \14?????\0E????????\6F?\2C\00\00\00?\00\00\00?\00\00\00??????????????????\00\00??\00\00??\00\00??\00\00
     */
    private boolean matchesPreferencesDatSignature(byte[] buf, int pos) {
        // First byte already verified (0x14)
        // buf[0-4] = ????? (5 bytes - any value)

        // buf[5] = 0x0E (position 6 of the signature)
        if ((buf[pos + 5] & 0xFF) != 0x0E)
            return false;

        // buf[6-13] = ???????? (8 bytes - any value)

        // buf[14] = 0x6F (position 15 of the signature)
        if ((buf[pos + 14] & 0xFF) != 0x6F)
            return false;

        // buf[15] = ? (1 byte - any value)

        // buf[16] = 0x2C (position 17 of the signature)
        if ((buf[pos + 16] & 0xFF) != 0x2C)
            return false;

        // buf[17-19] = 0x00, 0x00, 0x00 (positions 18-20)
        if ((buf[pos + 17] & 0xFF) != 0x00 || (buf[pos + 18] & 0xFF) != 0x00 || (buf[pos + 19] & 0xFF) != 0x00)
            return false;

        // buf[20] = ? (1 byte - any value)

        // buf[21-23] = 0x00, 0x00, 0x00 (positions 22-24)
        if ((buf[pos + 21] & 0xFF) != 0x00 || (buf[pos + 22] & 0xFF) != 0x00 || (buf[pos + 23] & 0xFF) != 0x00)
            return false;

        // buf[24] = ? (1 byte - any value)

        // buf[25-27] = 0x00, 0x00, 0x00 (positions 26-28)
        if ((buf[pos + 25] & 0xFF) != 0x00 || (buf[pos + 26] & 0xFF) != 0x00 || (buf[pos + 27] & 0xFF) != 0x00)
            return false;

        // buf[28-45] = ?????????????????? (18 bytes - any value)

        // buf[46-47] = 0x00, 0x00 (positions 47-48)
        if ((buf[pos + 46] & 0xFF) != 0x00 || (buf[pos + 47] & 0xFF) != 0x00)
            return false;

        // buf[48-49] = ?? (2 bytes - any value)

        // buf[50-51] = 0x00, 0x00 (positions 51-52)
        if ((buf[pos + 50] & 0xFF) != 0x00 || (buf[pos + 51] & 0xFF) != 0x00)
            return false;

        // buf[52-53] = ?? (2 bytes - any value)

        // buf[54-55] = 0x00, 0x00 (positions 55-56)
        if ((buf[pos + 54] & 0xFF) != 0x00 || (buf[pos + 55] & 0xFF) != 0x00)
            return false;

        // buf[56-57] = ?? (2 bytes - any value)

        // buf[58-59] = 0x00, 0x00 (positions 59-60)
        if ((buf[pos + 58] & 0xFF) != 0x00 || (buf[pos + 59] & 0xFF) != 0x00)
            return false;

        return true;
    }


    private static final int toInt(byte[] b, int offset) {
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8) | ((b[offset + 2] & 0XFF) << 16)
                | ((b[offset + 3] & 0XFF) << 24);
    }

    private static final int toSmall(byte[] b, int offset) {
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8);
    }

    private static boolean isAcceptedType(MediaType mediaType) {
        return LedCarveTask.isAcceptedType(mediaType);
    }
}
