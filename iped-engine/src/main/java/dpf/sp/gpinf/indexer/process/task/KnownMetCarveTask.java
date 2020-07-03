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
package dpf.sp.gpinf.indexer.process.task;

import java.io.BufferedInputStream;
import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.IOUtil;
import gpinf.emule.KnownMetEntry;
import gpinf.emule.KnownMetParser;
import iped3.IItem;

/**
 * Tarefa específica para carving de arquivos known.met do e-Mule.
 *
 * @author Wladimir Leite
 */
public class KnownMetCarveTask extends BaseCarveTask {

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
     * Contador de arquivos recuperados.
     */
    private static final AtomicInteger numCarvedItems = new AtomicInteger();

    /**
     * Media type dos arquivos recuperados.
     */

    private static final MediaType eMuleMediaType = MediaType.application("x-emule"); //$NON-NLS-1$

    /**
     * Passo para verificação do início do arquivo.
     */
    private final int step = 512;

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

    /**
     * Inicializa tarefa, realizando controle de alocação de apenas uma thread
     * principal.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                // Verifica se tarefa está habilitada
                String value = confParams.getProperty("enableKnownMetCarving"); //$NON-NLS-1$
                if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
                    taskEnabled = true;
                    logger.info("Task enabled."); //$NON-NLS-1$
                } else {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
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
                logger.info("Carved Items: " + numCarvedItems.get()); //$NON-NLS-1$
            }
        }
    }

    public void process(IItem evidence) {
        // Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || caseData.isIpedReport() || !isAcceptedType(evidence.getMediaType()))
            return;

        // Percorre conteúdo buscando padrões plausíveis de arquivos known.met
        byte[] bb = new byte[1];
        byte[] buf = new byte[step - 1];
        BufferedInputStream is = null;
        long offset = 0;
        try {
            is = evidence.getBufferedStream();
            while (is.read(bb) > 0) {
                byte read = bb[0];
                if (read == 14 || read == 15) {
                    is.read(buf);
                    int numFiles = toInt(buf, 0);
                    if (numFiles > 0 && numFiles < 65536) {
                        int pos = 4;
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
                                    BufferedInputStream inParse = null;
                                    try {
                                        inParse = evidence.getBufferedStream();
                                        inParse.skip(offset);
                                        List<KnownMetEntry> l = KnownMetParser.parseToList(inParse, len);
                                        if (!l.isEmpty()) {
                                            addCarvedFile(evidence, offset, len, "Carved-" + offset + "-known.met", //$NON-NLS-1$ //$NON-NLS-2$
                                                    eMuleMediaType);
                                            numCarvedItems.incrementAndGet();
                                        }
                                    } catch (Exception e) {
                                    } finally {
                                        IOUtil.closeQuietly(inParse);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    is.skip(step - 1);
                }
                offset += step;
            }
        } catch (Exception e) {
            logger.warn(evidence.toString(), e);
        } finally {
            IOUtil.closeQuietly(is);
        }
    }

    private static final int toInt(byte[] b, int offset) {
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8) | ((b[offset + 2] & 0XFF) << 16)
                | ((b[offset + 3] & 0XFF) << 24);
    }

    private static final int toSmall(byte[] b, int offset) {
        return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8);
    }

    private static boolean isAcceptedType(MediaType mediaType) {
        return mediaType.getBaseType().equals(UNALLOCATED_MIMETYPE) || mediaType.getBaseType().equals(mtPageFile);
    }
}