/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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

import gpinf.dev.data.EvidenceFile;
import gpinf.video.VideoProcessResult;
import gpinf.video.VideoThumbsMaker;
import gpinf.video.VideoThumbsOutputConfig;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Tarefa de geração de imagem com miniaturas (thumbs) de cenas extraídas de 
 * arquivos de vídeo.
 * @author Wladimir Leite
 */
public class VideoThumbTask extends AbstractTask {
    /** Instância da classe reponsável pelo processo de geração de thumbs. */
    private VideoThumbsMaker videoThumbsMaker;

    /** Lista de configurações de extração a serem geradas por vídeo. */
    private List<VideoThumbsOutputConfig> configs;

    /** Nome da tarefa. */
    private static final String taskName = "Extração de Cenas de Vídeos";

    /** Configuração principal de extração de cenas. */
    private VideoThumbsOutputConfig mainConfig;

    /** Pasta de saída das imagens. */
    private File baseFolder;

    /** Pasta temporária, utilizada como saído do MPlayer na extração de frames. */
    private File tmpFolder;

    /** Nome do arquivo temporário de miniatura gerado. Após conclusão será renomeado pra nome definitivo. */
    private String tempSuffix;

    /** Indica se a tarefa está habilitada ou não. */
    private static boolean taskEnabled = false;

    /** Constante com o nome utilizado para o arquivo de propriedades. */
    private static final String configFileName = "VideoThumbsConfig.txt";

    /** Executável, incluindo caminho do MPlayer. */
    private static String mplayer = "mplayer";

    /** Largura da imagem das cenas geradas. */
    private static int width = 200;

    /** Número de colunas. */
    private static int columns = 3;

    /** Número de linhas. */
    private static int rows = 6;

    /** Timeout da primeira execução do MPlayer. É maior para tratar possível processamento de fontes. */
    private static int timeoutFirst = 180000;

    /** Timeout na chamada de obtenção de propriedades do vídeo. */
    private static int timeoutInfo = 10000;

    /** Timeout na chamada normal de processamento do vídeo. */
    private static int timeoutProcess = 15000;

    /** Opção de redirecionamento da saída do MPlayer para o log, apenas para depuração de problemas. */
    private static boolean verbose = false;

    /** Objeto estático de inicialização. Necessário para garantir que seja feita apenas uma vez. */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /** Objeto estático para sincronizar finalização. */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    /** Objeto estático com total de videos processados . */
    private static final AtomicLong totalProcessed = new AtomicLong();

    /** Objeto estático com total de videos que falharam. */
    private static final AtomicLong totalFailed = new AtomicLong();

    /** Objeto estático com total de tempo gasto no processamento de vídeos, em milisegundos. */
    private static final AtomicLong totalTime = new AtomicLong();

    /** 
     * Set com videos em processamento, estático e sincronizado para evitar que duas threads processem
     * arquivos duplicados simultaneamente.
     */
    private static final Set<String> currentVideos = new HashSet<String>();

    /**
     * Construtor.
     */
    public VideoThumbTask(Worker worker) {
        super(worker);
    }

    /**
     * Inicializa a tarefa de processamento de vídeos.
     * Carrega configurações sobre o tamanho/layout a ser gerado e camimnho do MPlayer,
     * que é o programa responsável pela extração de frames.
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        //Instância objeto responsável pela extração de frames e inicializa parâmetros de utilização
        videoThumbsMaker = new VideoThumbsMaker();

        //Inicializa pasta temporarária e sufixo de arquivos temporários
        tmpFolder = new File(System.getProperty("java.io.tmpdir"));
        tempSuffix = Thread.currentThread().getId() + ".tmp";

        //Inicialização sincronizada
        synchronized (init) {
            if (!init.get()) {
                //Verifica se tarefa está habilitada
                String value = confParams.getProperty("enableVideoThumbs");
                if (value != null && value.trim().equalsIgnoreCase("true")) {
                    taskEnabled = true;
                } else {
                    Log.info(taskName, "Tarefa desabilitada.");
                    init.set(true);
                    return;
                }

                //Lê parâmetros do arquivo de configuração
                UTF8Properties properties = new UTF8Properties();
                File confFile = new File(confDir, configFileName);
                try {
                    properties.load(confFile);

                    //Caminho do MPlayer
                    value = properties.getProperty("MPlayer");
                    if (value != null) mplayer = value.trim();

                    //Layout
                    value = properties.getProperty("Layout");
                    if (value != null) {
                        String[] vals = value.trim().split(",");
                        if (vals.length == 3) {
                            width = Integer.parseInt(vals[0].trim());
                            columns = Integer.parseInt(vals[1].trim());
                            rows = Integer.parseInt(vals[2].trim());
                        }
                    }

                    //Verbose do MPlayer
                    value = properties.getProperty("Verbose");
                    if (value != null && value.trim().equalsIgnoreCase("true")) verbose = true;

                    //Timeouts
                    value = properties.getProperty("Timeouts");
                    if (value != null) {
                        String[] vals = value.trim().split(",");
                        if (vals.length == 3) {
                            timeoutFirst = 1000 * Integer.parseInt(vals[0].trim());
                            timeoutInfo = 1000 * Integer.parseInt(vals[1].trim());
                            timeoutProcess = 1000 * Integer.parseInt(vals[2].trim());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.error(taskName, "Erro lendo arquivo de configuração: " + confFile.getAbsolutePath());
                    taskEnabled = false;
                    init.set(true);
                    throw new RuntimeException("Erro lendo arquivo de configuração de extração de cenas de vídeos!");
                }

                //Configura o caminho do MPlayer, juntando com caminho da pasta principal caso tenha sido utilizado caminho relativo. 
                if (mplayer.indexOf('/') >= 0 || mplayer.indexOf('\\') >= 0) {
                    //Adicionando o caminho do jar principal como referência
                    //Seria melhor pegar o codePath pronto, mas atualmente App.get().codePath está nulo neste ponto.
                    String codePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("+", "/+");
                    codePath = URLDecoder.decode(codePath, "utf-8");
                    codePath = codePath.replace("/ ", "+");
                    mplayer = new File(codePath).getParent() + "/" + mplayer;
                }
                videoThumbsMaker.setMPlayer(mplayer);

                //Testa se o MPlayer está funcionando
                String vmp = videoThumbsMaker.getVersion();
                if (vmp == null) {
                    Log.error(taskName, "MPLAYER NÃO PODE SER EXECUTADO!");
                    Log.error(taskName, "MPlyer Configurado = " + mplayer);
                    Log.error(taskName, "Verifique o caminho e tente executá-lo diretamente na linha de comando.");
                    taskEnabled = false;
                    init.set(true);
                    throw new RuntimeException("Erro na extração de cenas de vídeos: MPlayer não pode ser executado!");
                }
                Log.info(taskName, "Tarefa habilitada.");
                Log.info(taskName, "Versão do MPLAYER utilizada: " + vmp);
                init.set(true);
            }
        }

        //Não continua se tarefa foi desabilitada
        if (!taskEnabled) return;

        //Inicializa parâmetros
        videoThumbsMaker.setMPlayer(mplayer);
        videoThumbsMaker.setVerbose(verbose);
        videoThumbsMaker.setTimeoutFirstCall(timeoutFirst);
        videoThumbsMaker.setTimeoutProcess(timeoutProcess);
        videoThumbsMaker.setTimeoutInfo(timeoutInfo);

        //Cria configurações de extração de cenas
        configs = new ArrayList<VideoThumbsOutputConfig>();
        configs.add(mainConfig = new VideoThumbsOutputConfig(null, width, columns, rows, 2));

        //Inicializa diretório de saída
        baseFolder = new File(output, "view");
        if (!baseFolder.exists()) baseFolder.mkdirs();
    }

    /**
     * Finalização da tarefa. 
     * Apenas grava algumas informações sobre o processamento no Log.
     */
    public void finish() throws Exception {
        synchronized (finished) {
            if (!finished.get()) {
                finished.set(true);
                Log.info(taskName, "Total de Vídeos Processados: " + totalProcessed);
                Log.info(taskName, "Total de Vídeos Não Processados (MPlayer não conseguiu extrair cenas): " + totalFailed);
                Log.info(taskName, "Tempo Total do Processamento de Vídeos (em segundos): " + ((totalTime.longValue() + 500) / 1000));
            }
        }
    }

    /**
     * Método principal do processamento. 
     * Primeiramente verifica se o tipo de arquivo é vídeo.
     * Depois chama método da classe, informando o caminho do arquivo de entrada e 
     * caminho completo de destino.    
     */
    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        //Verifica se está desabilitado e se o tipo de arquivo é tratado
        if (!taskEnabled || !isVideoType(evidence.getMediaType()) || !evidence.isToAddToCase() || evidence.getHash() == null) return;

        //Verifica se há outro vídeo igual em processamento, senão inclui
        synchronized (currentVideos) {
            if (currentVideos.contains(evidence.getHash())) return;
            currentVideos.add(evidence.getHash());
        }

        //Chama o método de extração de cenas
        File mainTmpFile = null;
        try {
            File mainOutFile = Util.getFileFromHash(baseFolder, evidence.getHash(), "jpg");
            if (!mainOutFile.getParentFile().exists()) mainOutFile.getParentFile().mkdirs();

            //Já está pasta? Então não é necessário gerar.
            if (mainOutFile.exists()) return;

            mainTmpFile = new File(mainOutFile.getParentFile(), evidence.getHash() + tempSuffix);
            mainConfig.setOutFile(mainTmpFile);

            long t = System.currentTimeMillis();
            VideoProcessResult r = videoThumbsMaker.createThumbs(evidence.getTempFile(), tmpFolder, configs);
            t = System.currentTimeMillis() - t;
            if (r.isSuccess()) {
                mainTmpFile.renameTo(mainOutFile);
            } else {
                totalFailed.incrementAndGet();
            }
            totalProcessed.incrementAndGet();
            totalTime.addAndGet(t);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //Tenta apaga possível temporários deixados "perdidos" (no caso normal eles foram renomeados)
            if (mainTmpFile != null && mainTmpFile.exists()) mainTmpFile.delete();

            //Retira do Set de arquivos em processamento
            synchronized (currentVideos) {
                currentVideos.remove(evidence.getHash());
            }
        }
    }

    /**
     * Verifica se é vídeo.
     */
    public static boolean isVideoType(MediaType mediaType) {
        return mediaType.getType().equals("video") || mediaType.getBaseType().toString().equals("application/vnd.rn-realmedia");
    }
}