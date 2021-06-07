/*
 * Copyright 2015-2016, Wladimir Leite
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.config.LocaleConfig;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.ExternalImageConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.ImageMetadataUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.ReportInfo;
import iped3.IItem;

/**
 * Tarefa de geração de relatório no formato HTML do itens selecionados, gerado
 * quando a entrada do processamento é um arquivo ".IPED".
 *
 * @author Wladimir Leite
 */
public class HTMLReportTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(HTMLReportTask.class);

    /**
     * Collator utilizado para ordenação correta alfabética, incluindo acentuação.
     */
    private static final Collator collator = getCollator();

    /**
     * Nome da subpasta com versões de visualização dos arquivos.
     */
    public static final String viewFolder = "view"; //$NON-NLS-1$

    /**
     * Registros organizados por marcador.
     */
    private static final SortedMap<String, List<ReportEntry>> entriesByLabel = new TreeMap<String, List<ReportEntry>>(
            collator);

    /**
     * Registros organizados por categoria.
     */
    private static final SortedMap<String, List<ReportEntry>> entriesByCategory = new TreeMap<String, List<ReportEntry>>(
            collator);

    /**
     * Registros sem marcador.
     */
    private static final List<ReportEntry> entriesNoLabel = new ArrayList<ReportEntry>();

    /**
     * Tag para registros sem marcador
     */
    private static final String NO_LABEL_NAME = Messages.getString("HTMLReportTask.NoBookmarks"); //$NON-NLS-1$

    /**
     * Mapa de marcadores para seus comentários
     */
    private Map<String, String> labelcomments = new HashMap<>();

    /**
     * Indicador de inicialização, para controle de sincronização entre instâncias
     * da classe.
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Objeto com informações que serão incluídas no relatório.
     */
    private ReportInfo info;

    /**
     * Nome da pasta com miniatutas de imagem.
     */
    public static String thumbsFolderName = "thumbs"; //$NON-NLS-1$

    /**
     * Nome da subpasta destino dos os arquivos do relatório.
     */
    public static String reportSubFolderName = Messages.getString("HTMLReportTask.ReportSubFolder"); //$NON-NLS-1$

    /**
     * Subpasta com a maior parte dos arquivos HTML e arquivos auxiliares. Static
     * porque pode ser utilizada por todas as instâncias (uma utilizada em cada
     * thread de processamento).
     */
    private static File reportSubFolder;

    /**
     * Indica se a tarefa está habilitada ou não.
     */
    private static boolean taskEnabled = false;

    /**
     * Flag de controle se a geração de miniaturas de imagem está habilitada.
     */
    private static boolean imageThumbsEnabled = false;

    /**
     * Flag de controle se a inclusão de miniaturas de cenas de vídeos (que devem
     * estar disponíveis) está habilitada.
     */
    private static boolean videoThumbsEnabled = false;

    /**
     * Map com miniaturas de imagem organizadas por marcador, utilizado para geração
     * de galeria de imagens.
     */
    private static final SortedMap<String, List<String>> imageThumbsByLabel = new TreeMap<String, List<String>>(
            collator);

    /**
     * Flag de controle que indica que uma lista de categorias deve se mostrada na
     * página de conteúdo, além da lista de marcadores, que é incluída como padrão.
     */
    private static boolean categoriesListEnabled = false;

    /**
     * Tamanho da miniatura (utilizado no HTML).
     */
    private static int thumbSize = 112;

    /**
     * Quantidade de frames utilizado na "faixa" de cenas de vídeo.
     */
    private static int framesPerStripe = 8;

    /**
     * Largura (em pixels) da "faixa" de cenas de vídeo.
     */
    private static int videoStripeWidth = 800;

    /**
     * Itens por página HTML.
     */
    private static int itemsPerPage = 100;

    /**
     * Flag de controle se página com galeria de miniaturas de imagem é criada.
     */
    private static boolean thumbsPageEnabled = false;

    /**
     * Miniaturas de imagem na página de galeria.
     */
    private static int thumbsPerPage = 500;

    /**
     * Constante com o nome utilizado para o arquivo de propriedades.
     */
    private static final String configFileName = "HTMLReportConfig.txt"; //$NON-NLS-1$

    /**
     * Set com arquivos em processamento, estático e sincronizado para evitar que
     * duas threads processem arquivos duplicados simultaneamente, no caso de
     * geração de miniaturas.
     */
    private static final Set<String> currentFiles = new HashSet<String>();

    private static final ExternalImageConverter externalImageConverter = new ExternalImageConverter();

    /**
     * Armazena modelo de formatação no nome/mat/classe do(s) perito(s).
     */
    private StringBuilder modeloPerito;

    private static Collator getCollator() {
        LocaleConfig localeConfig = (LocaleConfig) ConfigurationManager.getInstance().findObjects(LocaleConfig.class)
                .iterator().next();

        Collator c = Collator.getInstance(localeConfig.getLocale());
        c.setStrength(Collator.TERTIARY);
        return c;
    }

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
                String value = confParams.getProperty("enableHTMLReport"); //$NON-NLS-1$
                if (value != null && value.trim().equalsIgnoreCase("true")) { //$NON-NLS-1$
                    taskEnabled = true;
                    logger.info("Task enabled."); //$NON-NLS-1$
                } else {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }

                info = new ReportInfo();
                reportSubFolder = new File(this.output.getParentFile(), reportSubFolderName);

                // Lê parâmetros do arquivo de configuração
                UTF8Properties properties = new UTF8Properties();
                File confFile = new File(confDir, configFileName);
                try {
                    properties.load(confFile);

                    value = properties.getProperty("ItemsPerPage"); //$NON-NLS-1$
                    if (value != null) {
                        itemsPerPage = Integer.parseInt(value.trim());
                    }

                    value = properties.getProperty("ThumbsPerPage"); //$NON-NLS-1$
                    if (value != null) {
                        thumbsPerPage = Integer.parseInt(value.trim());
                    }

                    value = properties.getProperty("ThumbSize"); //$NON-NLS-1$
                    if (value != null) {
                        thumbSize = Integer.parseInt(value.trim());
                    }

                    value = properties.getProperty("EnableImageThumbs"); //$NON-NLS-1$
                    if (value != null && value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                        imageThumbsEnabled = true;
                    }

                    value = properties.getProperty("EnableVideoThumbs"); //$NON-NLS-1$
                    if (value != null && value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                        videoThumbsEnabled = true;
                    }

                    value = properties.getProperty("FramesPerStripe"); //$NON-NLS-1$
                    if (value != null) {
                        framesPerStripe = Integer.parseInt(value.trim());
                    }

                    value = properties.getProperty("VideoStripeWidth"); //$NON-NLS-1$
                    if (value != null) {
                        videoStripeWidth = Integer.parseInt(value.trim());
                    }

                    value = properties.getProperty("EnableCategoriesList"); //$NON-NLS-1$
                    if (value != null && value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                        categoriesListEnabled = true;
                    }

                    value = properties.getProperty("EnableThumbsGallery"); //$NON-NLS-1$
                    if (value != null && value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                        thumbsPageEnabled = true;
                    }

                    info.reportHeader = properties.getProperty("Header"); //$NON-NLS-1$
                    // info.classe.add(properties.getProperty("Classe"));
                    info.reportDate = properties.getProperty("ReportDate"); //$NON-NLS-1$
                    info.requestDate = properties.getProperty("RequestDate"); //$NON-NLS-1$
                    info.labCaseDate = properties.getProperty("RecordDate"); //$NON-NLS-1$
                    info.requestForm = properties.getProperty("RequestDoc"); //$NON-NLS-1$
                    info.caseNumber = properties.getProperty("Investigation"); //$NON-NLS-1$
                    info.reportNumber = properties.getProperty("Report"); //$NON-NLS-1$
                    info.fillEvidenceFromText(properties.getProperty("Material")); //$NON-NLS-1$
                    info.examinersID.add(properties.getProperty("ExaminerID")); //$NON-NLS-1$
                    info.examiners.add(properties.getProperty("Examiner")); //$NON-NLS-1$
                    info.labCaseNumber = properties.getProperty("Record"); //$NON-NLS-1$
                    info.requester = properties.getProperty("Requester"); //$NON-NLS-1$
                    info.reportTitle = properties.getProperty("Title"); //$NON-NLS-1$
                } catch (Exception e) {
                    e.printStackTrace();
                    init.set(true);
                    throw new RuntimeException("Error loading conf file: " + confFile.getAbsolutePath()); //$NON-NLS-1$
                }

                // Obtém parâmetro ASAP, com arquivo contendo informações do caso, se tiver sido
                // especificado
                CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
                if (args != null) {
                    File infoFile = args.getAsap();
                    if (infoFile != null) {
                        logger.info("Processing case info file: " + infoFile.getAbsolutePath()); //$NON-NLS-1$
                        if (!infoFile.exists()) {
                            throw new RuntimeException("File not found: " + infoFile.getAbsolutePath()); //$NON-NLS-1$
                        }
                        try {
                            if (infoFile.getName().endsWith(".asap")) //$NON-NLS-1$
                                info.readAsapInfoFile(infoFile);
                            else if (infoFile.getName().endsWith(".json")) //$NON-NLS-1$
                                info.readJsonInfoFile(infoFile);
                            else if (infoFile.getName().endsWith(".report")) { //$NON-NLS-1$
                                ReportInfo ri = ReportInfo.readReportInfoFile(infoFile);
                                ri.reportHeader = info.reportHeader;
                                info = ri;
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            init.set(true);
                            throw new RuntimeException("Error loading case info file: " + infoFile.getAbsolutePath()); //$NON-NLS-1$
                        }
                    }
                }
                init.set(true);
            }
        }
    }

    /**
     * Finaliza a tarefa, gerando os arquivos HTML do relatório na instância que
     * contém o objeto info.
     */
    @Override
    public void finish() throws Exception {

        if (taskEnabled && caseData.containsReport() && info != null) {

            String reportRoot = Messages.getString("HTMLReportTask.ReportFileName"); //$NON-NLS-1$
            if (new File(reportSubFolder.getParentFile(), reportRoot).exists()) {
                logger.error("Html report already exists, report update not implemented yet!"); //$NON-NLS-1$
                return;
            }

            WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    Messages.getString("HTMLReportTask.MakingHtmlReport")); //$NON-NLS-1$

            // Pasta com arquivos HTML formatado que são utilizados como entrada.
            String codePath = Configuration.getInstance().appRoot;
            String reportRootModel = "relatorio.htm"; //$NON-NLS-1$
            File templatesFolder = new File(new File(codePath), "htmlreport"); //$NON-NLS-1$
            if (!new File(templatesFolder, reportRootModel).exists()) {
                LocaleConfig localeConf = (LocaleConfig) ConfigurationManager.getInstance()
                        .findObjects(LocaleConfig.class).iterator().next();
                templatesFolder = new File(new File(codePath), "htmlreport/" + localeConf.getLocale().toLanguageTag()); //$NON-NLS-1$
            }

            logger.info("Report folder: " + reportSubFolder.getAbsolutePath()); //$NON-NLS-1$
            logger.info("Template folder: " + templatesFolder.getAbsolutePath()); //$NON-NLS-1$
            if (!templatesFolder.exists()) {
                throw new FileNotFoundException("Template folder not found!"); //$NON-NLS-1$
            }

            File templateSubFolder = new File(templatesFolder, "modelos"); //$NON-NLS-1$
            if (!templateSubFolder.exists())
                templateSubFolder = new File(templatesFolder, "templates"); //$NON-NLS-1$

            long t = System.currentTimeMillis();

            try (IPEDSource ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer)) {
                for (int labelId : ipedCase.getMarcadores().getLabelMap().keySet()) {
                    String labelName = ipedCase.getMarcadores().getLabelName(labelId);
                    String comments = ipedCase.getMarcadores().getLabelComment(labelId);
                    labelcomments.put(labelName, comments);
                }
            }

            reportSubFolder.mkdirs();

            if (!entriesByLabel.isEmpty() && !entriesNoLabel.isEmpty())
                entriesByLabel.put(NO_LABEL_NAME, entriesNoLabel);

            modeloPerito = EncodedFile.readFile(new File(templateSubFolder, "perito.html"), StandardCharsets.UTF_8).content; //$NON-NLS-1$//$NON-NLS-2$
            processBookmarks(templateSubFolder);
            if (thumbsPageEnabled && !imageThumbsByLabel.isEmpty()) {
                createThumbsPage();
            }
            processCaseInfo(new File(templatesFolder, "caseinformation.htm"), //$NON-NLS-1$
                    new File(reportSubFolder, "caseinformation.htm")); //$NON-NLS-1$
            processContents(new File(templatesFolder, "contents.htm"), new File(reportSubFolder, "contents.htm")); //$NON-NLS-1$ //$NON-NLS-2$

            File reportRootModelFile = new File(templatesFolder, reportRootModel);
            if (!reportRootModelFile.exists())
                reportRootModelFile = new File(templatesFolder, "report.htm"); //$NON-NLS-1$
            Files.copy(reportRootModelFile.toPath(), new File(reportSubFolder.getParentFile(), reportRoot).toPath());

            File help = new File(templatesFolder, "ajuda.htm"); //$NON-NLS-1$
            if (help.exists())
                copyFile(help, reportSubFolder);

            copyFiles(new File(templatesFolder, "res"), new File(reportSubFolder, "res")); //$NON-NLS-1$ //$NON-NLS-2$

            t = (System.currentTimeMillis() - t + 500) / 1000;
            logger.info("Report creation time (seconds): " + t); //$NON-NLS-1$

            externalImageConverter.close();
        }
    }

    /**
     * Processa um item, extraindo as informações a serem utilizadas no relatório e
     * incluindo nas listas adequadas.
     */
    @Override
    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || !caseData.containsReport() || !evidence.isToAddToCase()) {
            return;
        }

        ReportEntry reg = new ReportEntry();
        reg.name = evidence.getName();
        reg.export = evidence.getExportedFile();
        reg.isImage = ImageThumbTask.isImageType(evidence.getMediaType());
        reg.isVideo = VideoThumbTask.isVideoType(evidence.getMediaType());
        reg.length = evidence.getLength();
        reg.ext = evidence.getExt();
        reg.category = evidence.getCategories().replace(CategoryTokenizer.SEPARATOR + "", " | "); //$NON-NLS-1$ //$NON-NLS-2$
        reg.hash = evidence.getHash();
        if (reg.hash != null && reg.hash.isEmpty()) {
            reg.hash = null;
        }
        reg.deleted = evidence.isDeleted();
        reg.carved = evidence.isCarved();
        reg.accessed = evidence.getAccessDate();
        reg.modified = evidence.getModDate();
        reg.created = evidence.getCreationDate();
        reg.path = evidence.getPath();

        String[] labels = new String[] { "" }; //$NON-NLS-1$
        if (!evidence.getLabels().isEmpty())
            labels = evidence.getLabels().toArray(new String[0]);
        String[] categories = reg.category.split("\\|"); //$NON-NLS-1$
        synchronized (init) {
            for (String label : labels) {
                label = label.trim();
                List<ReportEntry> regs = label.length() == 0 ? entriesNoLabel : entriesByLabel.get(label);
                if (regs == null) {
                    entriesByLabel.put(label, regs = new ArrayList<ReportEntry>());
                }
                regs.add(reg);
            }
            for (String category : categories) {
                category = category.trim();
                List<ReportEntry> regs = entriesByCategory.get(category);
                if (regs == null) {
                    entriesByCategory.put(category, regs = new ArrayList<ReportEntry>());
                }
                regs.add(reg);
            }
        }

        if (((imageThumbsEnabled && reg.isImage) || (videoThumbsEnabled && reg.isVideo)) && reg.hash != null) {
            // Verifica se há outro arquivo igual em processamento, senão inclui
            synchronized (currentFiles) {
                if (currentFiles.contains(evidence.getHash())) {
                    return;
                }
                currentFiles.add(evidence.getHash());
            }
            File thumbFile;
            if (reg.isImage) {
                thumbFile = getImageThumbFile(reg.hash);
                if (!thumbFile.exists()) {
                    createImageThumb(evidence, thumbFile);
                }
            } else if (reg.isVideo) {
                thumbFile = getVideoStripeFile(reg.hash);
                if (!thumbFile.exists()) {
                    createVideoStripe(reg, thumbFile);
                }
            }

            // Retira do Set de arquivos em processamento
            synchronized (currentFiles) {
                currentFiles.remove(evidence.getHash());
            }
        }
    }

    private void copyFiles(File src, File target) throws IOException {
        if (!target.exists()) {
            target.mkdir();
        }
        File[] arqs = src.listFiles();
        for (File arq : arqs) {
            if (!arq.isFile()) {
                continue;
            }
            File tf = new File(target, arq.getName());
            if (tf.exists()) {
                continue;
            }
            Files.copy(arq.toPath(), tf.toPath());
        }
    }

    private void copyFile(File src, File targetFolder) throws IOException {
        Files.copy(src.toPath(), new File(targetFolder, src.getName()).toPath());
    }

    private void processCaseInfo(File src, File target) throws Exception {
        EncodedFile arq = EncodedFile.readFile(src, StandardCharsets.UTF_8); //$NON-NLS-1$
        replace(arq.content, "%REPORT%", info.reportNumber); //$NON-NLS-1$
        replace(arq.content, "%REPORT_DATE%", info.reportDate); //$NON-NLS-1$
        replace(arq.content, "%EXAMINERS%", formatPeritos()); //$NON-NLS-1$
        replace(arq.content, "%HEADER%", info.reportHeader); //$NON-NLS-1$
        replace(arq.content, "%TITLE%", info.reportTitle); //$NON-NLS-1$
        replace(arq.content, "%INVESTIGATION%", info.caseNumber); //$NON-NLS-1$
        replace(arq.content, "%REQUEST_DOC%", info.requestForm); //$NON-NLS-1$
        replace(arq.content, "%REQUEST_DATE%", info.requestDate); //$NON-NLS-1$
        replace(arq.content, "%REQUESTER%", info.requester); //$NON-NLS-1$
        replace(arq.content, "%RECORD%", info.labCaseNumber); //$NON-NLS-1$
        replace(arq.content, "%RECORD_DATE%", info.labCaseDate); //$NON-NLS-1$
        replace(arq.content, "%EVIDENCE%", info.getEvidenceDescHtml()); //$NON-NLS-1$
        arq.file = target;
        arq.write();
    }

    private String formatPeritos() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < info.examiners.size(); i++) {
            if (i > 0) {
                ret.append("<br><br>\n"); //$NON-NLS-1$
            }
            StringBuilder s = new StringBuilder();
            s.append(modeloPerito);
            replace(s, "%EXAMINER%", info.examiners.get(i)); //$NON-NLS-1$
            // replace(s, "%CLASSE%", info.classe.size() > i ?
            // formatClass(info.classe.get(i)) : ""); //$NON-NLS-2$
            replace(s, "%EXAMINER_ID%", info.examinersID.size() > i ? info.examinersID.get(i) : ""); //$NON-NLS-1$//$NON-NLS-2$
            ret.append(s);
        }
        return ret.toString();
    }

    private String formatClass(String str) {
        if (str != null && str.length() == 1) {
            char c = str.charAt(0);
            if (c >= '1' && c <= '3') {
                str = c + "a Classe"; //$NON-NLS-1$
            } else if (Character.toUpperCase(c) == 'E') {
                str = "Classe Especial"; //$NON-NLS-1$
            }
        }
        return str;
    }

    private void processContents(File src, File target) throws Exception {
        StringBuilder sb = new StringBuilder();

        int idx = 1;
        if (!entriesByLabel.isEmpty()) {
            sb.append("<p>\n"); //$NON-NLS-1$
            sb.append("\t<span class=\"SmallText1\">" + Messages.getString("HTMLReportTask.Bookmarks") + "</span>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (String marcador : entriesByLabel.keySet()) {
                sb.append("\t\t<br />&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"arq"); //$NON-NLS-1$
                sb.append(String.format("%06d", idx)); //$NON-NLS-1$
                sb.append("(1).html\" target=\"ReportPage\" class=\"MenuText\">"); //$NON-NLS-1$
                sb.append(marcador);
                sb.append("</a>\n"); //$NON-NLS-1$
                idx++;
            }
            sb.append("</p>\n"); //$NON-NLS-1$
        }

        if (categoriesListEnabled && !entriesByCategory.isEmpty()) {
            sb.append("<p>\n"); //$NON-NLS-1$
            sb.append("\t<span class=\"SmallText1\">" + Messages.getString("HTMLReportTask.Categories") + "</span>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (String categoria : entriesByCategory.keySet()) {
                sb.append("\t\t<br />&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"arq"); //$NON-NLS-1$
                sb.append(String.format("%06d", idx)); //$NON-NLS-1$
                sb.append("(1).html\" target=\"ReportPage\" class=\"MenuText\">"); //$NON-NLS-1$
                sb.append(categoria);
                sb.append("</a>\n"); //$NON-NLS-1$
                idx++;
            }
            sb.append("</p>"); //$NON-NLS-1$
        }

        if (thumbsPageEnabled && !imageThumbsByLabel.isEmpty()) {
            sb.append("<p>\n"); //$NON-NLS-1$
            sb.append("<b><a href=\"thumbs_001.htm\" class=\"SmallText2\" target=\"ReportPage\">"); //$NON-NLS-1$
            sb.append(Messages.getString("HTMLReportTask.GalleryLink") + "</a></b></p>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        EncodedFile arq = EncodedFile.readFile(src, StandardCharsets.UTF_8); //$NON-NLS-1$
        replace(arq.content, "%BOOKMARKS%", sb.toString()); //$NON-NLS-1$

        arq.file = target;
        arq.write();
    }

    private void processBookmarks(File templatesFolder) throws Exception {
        sortRegs();
        StringBuilder modelo = EncodedFile.readFile(new File(templatesFolder, "arq.html"), StandardCharsets.UTF_8).content; //$NON-NLS-1$//$NON-NLS-2$
        replace(modelo, "%THUMBSIZE%", String.valueOf(thumbSize)); //$NON-NLS-1$
        StringBuilder item = EncodedFile.readFile(new File(templatesFolder, "item.html"), StandardCharsets.UTF_8).content; //$NON-NLS-1$//$NON-NLS-2$
        int idx = 1;
        for (String marcador : entriesByLabel.keySet()) {
            String id = String.format("arq%06d", idx); //$NON-NLS-1$
            List<ReportEntry> regs = entriesByLabel.get(marcador);
            processaBookmark(marcador, id, modelo, item, true, regs);
            idx++;
            List<String> l = new ArrayList<String>();
            for (ReportEntry e : regs) {
                if (e.img != null)
                    l.add(e.img);
            }
            if (!l.isEmpty())
                imageThumbsByLabel.put(marcador, l);
        }
        if (categoriesListEnabled) {
            for (String categoria : entriesByCategory.keySet()) {
                String id = String.format("arq%06d", idx); //$NON-NLS-1$
                List<ReportEntry> regs = entriesByCategory.get(categoria);
                processaBookmark(categoria, id, modelo, item, false, regs);
                idx++;
                if (entriesByLabel.isEmpty()) {
                    List<String> l = new ArrayList<String>();
                    for (ReportEntry e : regs) {
                        if (e.img != null)
                            l.add(e.img);
                    }
                    if (!l.isEmpty())
                        imageThumbsByLabel.put(categoria, l);
                }
            }
        }
    }

    private void sortRegs() {
        final List<List<ReportEntry>> l = new ArrayList<List<ReportEntry>>(entriesByLabel.values());
        if (categoriesListEnabled)
            l.addAll(entriesByCategory.values());
        Collections.sort(l, new Comparator<List<ReportEntry>>() {
            public int compare(List<ReportEntry> a, List<ReportEntry> b) {
                return Integer.compare(b.size(), a.size());
            }
        });
        final CustomComparator comparator = new CustomComparator();

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        final int numThreads = localConfig.getNumThreads();

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            (threads[i] = new Thread() {
                public void run() {
                    for (int j = idx; j < l.size(); j += numThreads) {
                        Collections.sort(l.get(j), new Comparator<ReportEntry>() {
                            public int compare(ReportEntry a, ReportEntry b) {
                                return comparator.compare(a.path, b.path);
                            }
                        });
                    }
                }
            }).start();
        }
        for (int i = 0; i < numThreads; i++) {
            try {
                if (threads[i] != null)
                    threads[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    private void processaBookmark(final String name, final String id, final StringBuilder model,
            final StringBuilder item, final boolean isLabel, final List<ReportEntry> regs) throws Exception {
        final int tot = regs.size();
        final int numPages = (tot + itemsPerPage - 1) / itemsPerPage;

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        final int numThreads = localConfig.getNumThreads();

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            (threads[i] = new Thread() {
                public void run() {
                    DateFormat dateFormat = new SimpleDateFormat(Messages.getString("HTMLReportTask.Dateformat")); //$NON-NLS-1$
                    NumberFormat longFormat = new DecimalFormat("#,##0"); //$NON-NLS-1$
                    for (int page = 1; page <= numPages; page++) {
                        if (page % numThreads != idx)
                            continue;
                        int start = (page - 1) * itemsPerPage;
                        int end = Math.min(tot, start + itemsPerPage);
                        try {
                            createBookmarkPage(dateFormat, longFormat, name, id, model, item, page, numPages, tot,
                                    regs.subList(start, end), isLabel);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
        for (int i = 0; i < numThreads; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private String getPageId(String id, int page) {
        return id + "(" + page + ").html"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void createBookmarkPage(DateFormat dateFormat, NumberFormat longFormat, String name, String id,
            StringBuilder model, StringBuilder item, int pag, int totPags, int totRegs, List<ReportEntry> regs,
            boolean isLabel) throws Exception {
        File arq = new File(reportSubFolder, getPageId(id, pag));

        StringBuilder sb = new StringBuilder();
        sb.append(model);

        StringBuilder items = new StringBuilder();
        StringBuilder it = new StringBuilder();
        for (int i = 0; i < regs.size(); i++) {
            ReportEntry reg = regs.get(i);
            it.delete(0, it.length());
            it.append(item);

            if (reg.isImage && imageThumbsEnabled && reg.hash != null) {
                File thumbFile = getImageThumbFile(reg.hash);
                if (thumbFile.exists() && thumbFile.length() > 0) {
                    it.append("<table width=\"100%\"><tr><td>"); //$NON-NLS-1$

                    StringBuilder img = new StringBuilder();
                    if (reg.export != null) {
                        img.append("<a href=\""); //$NON-NLS-1$
                        img.append("../").append(reg.export); //$NON-NLS-1$
                        img.append("\">"); //$NON-NLS-1$
                    }
                    img.append("<img src=\""); //$NON-NLS-1$
                    img.append(getRelativePath(thumbFile, reportSubFolder));
                    img.append("\" class=\"thumb\" />"); //$NON-NLS-1$
                    if (reg.export != null) {
                        img.append("</a>"); //$NON-NLS-1$
                    }
                    it.append(img);
                    it.append("</td></tr></table>\n"); //$NON-NLS-1$
                    if (isLabel || entriesByLabel.isEmpty()) {
                        reg.img = img.toString();
                    }
                }
            } else if (reg.isVideo && videoThumbsEnabled && reg.hash != null) {
                File videoThumbsFile = getVideoThumbsFile(reg.hash);
                File stripeFile = getVideoStripeFile(reg.hash);
                if (stripeFile.exists()) {
                    Dimension dim = ImageUtil.getImageFileDimension(stripeFile);
                    it.append(
                            "<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">" //$NON-NLS-1$
                                    + Messages.getString("HTMLReportTask.VideoThumbs") //$NON-NLS-1$
                                    + "</span><span class=\"bkmkColRight bkmkValue\"><a href=\""); //$NON-NLS-1$
                    it.append(getRelativePath(videoThumbsFile, reportSubFolder));
                    it.append("\"><img src=\""); //$NON-NLS-1$
                    it.append(getRelativePath(stripeFile, reportSubFolder)).append("\""); //$NON-NLS-1$
                    if (dim != null) {
                        it.append(" width=\"").append(dim.width).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
                        it.append(" height=\"").append(dim.height).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    it.append(">"); //$NON-NLS-1$
                    it.append("</a></span></div><div class=\"row\">&nbsp;</div>\n"); //$NON-NLS-1$
                }
            } else if (!reg.isVideo && reg.hash != null) {
                File view = Util.findFileFromHash(new File(this.output, viewFolder), reg.hash);
                if (view != null) {
                    it.append(
                            "<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">" //$NON-NLS-1$
                                    + Messages.getString("HTMLReportTask.PreviewReport") //$NON-NLS-1$
                                    + "</span><span class=\"bkmkColRight bkmkValue\"><a href=\""); //$NON-NLS-1$
                    it.append(getRelativePath(view, reportSubFolder));
                    it.append("\">"); //$NON-NLS-1$
                    it.append(view.getName());
                    it.append("</a></span></div>\n"); //$NON-NLS-1$
                }
            }
            replace(it, "%SEQ%", reg.hash); //$NON-NLS-1$
            replace(it, "%NAME%", reg.name); //$NON-NLS-1$
            replace(it, "%PATH%", reg.path); //$NON-NLS-1$
            replace(it, "%TYPE%", reg.category); //$NON-NLS-1$
            replace(it, "%SIZE%", formatNumber(reg.length, longFormat)); //$NON-NLS-1$
            replace(it, "%DELETED%", //$NON-NLS-1$
                    reg.deleted ? Messages.getString("HTMLReportTask.Yes") : Messages.getString("HTMLReportTask.No")); //$NON-NLS-1$ //$NON-NLS-2$
            replace(it, "%CARVED%", //$NON-NLS-1$
                    reg.carved ? Messages.getString("HTMLReportTask.Yes") : Messages.getString("HTMLReportTask.No")); //$NON-NLS-1$ //$NON-NLS-2$
            replace(it, "%HASH%", reg.hash); //$NON-NLS-1$
            String export = reg.export == null ? "-" : "<a href=\"../" + reg.export + "\">" + reg.export + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            replace(it, "%EXPORTED%", export); //$NON-NLS-1$
            replace(it, "%CREATED%", formatDate(reg.created, dateFormat)); //$NON-NLS-1$
            replace(it, "%MODIFIED%", formatDate(reg.modified, dateFormat)); //$NON-NLS-1$
            replace(it, "%ACCESSED%", formatDate(reg.accessed, dateFormat)); //$NON-NLS-1$
            items.append(it);
        }

        StringBuilder p = new StringBuilder();
        p.append("<table width=\"100%\">\n"); //$NON-NLS-1$
        p.append("<tr><td>" + Messages.getString("HTMLReportTask.Page") + " %PAG%" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + Messages.getString("HTMLReportTask.of") + "%TOTPAG%</td>"); //$NON-NLS-1$ //$NON-NLS-2$
        replace(p, "%PAG%", String.valueOf(pag)); //$NON-NLS-1$
        replace(p, "%TOTPAG%", String.valueOf(totPags)); //$NON-NLS-1$
        if (totPags > 1) {
            if (pag > 1) {
                p.append("<td><a href=\"").append(getPageId(id, 1)) //$NON-NLS-1$
                        .append("\">&lt;&lt;&lt;&lt;" + Messages.getString("HTMLReportTask.FirstPage") + "</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                p.append("<td><a href=\"").append(getPageId(id, pag - 1)) //$NON-NLS-1$
                        .append("\">&lt;&lt;" + Messages.getString("HTMLReportTask.PrevPage") + "</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            if (pag < totPags) {
                p.append("<td><a href=\"").append(getPageId(id, pag + 1)) //$NON-NLS-1$
                        .append("\">" + Messages.getString("HTMLReportTask.NextPage") + "&gt;&gt;</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                p.append("<td><a href=\"").append(getPageId(id, totPags)) //$NON-NLS-1$
                        .append("\">" + Messages.getString("HTMLReportTask.LastPage") + "&gt;&gt;&gt;&gt;</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
        p.append("</tr></table>\n"); //$NON-NLS-1$

        replace(sb, "%CATEGORY%", (isLabel ? Messages.getString("HTMLReportTask.Bookmark") //$NON-NLS-1$ //$NON-NLS-2$
                : Messages.getString("HTMLReportTask.Category")) + ": " + name); //$NON-NLS-1$ //$NON-NLS-2$
        replace(sb, "%COMMENTS%", getComments(name)); //$NON-NLS-1$
        replace(sb, "%TOTALCOUNT%", String.valueOf(totRegs)); //$NON-NLS-1$
        replace(sb, "%ITEMS%", items.toString()); //$NON-NLS-1$
        replace(sb, "%PAGS%", p.toString()); //$NON-NLS-1$

        EncodedFile ef = new EncodedFile(sb, Charset.forName("utf-8"), arq); //$NON-NLS-1$
        ef.write();
    }

    private String getComments(String bookmark) {
        String comments = labelcomments.get(bookmark);
        if (comments == null || comments.trim().isEmpty())
            comments = "-"; //$NON-NLS-1$
        return comments;
    }

    private File getVideoStripeFile(String hash) {
        File file = Util.getFileFromHash(new File(reportSubFolder, thumbsFolderName), hash, "jpg"); //$NON-NLS-1$
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    private File getImageThumbFile(String hash) {
        File file = Util.getFileFromHash(new File(output, ThumbTask.thumbsFolder), hash, "jpg"); //$NON-NLS-1$
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    private File getVideoThumbsFile(String hash) {
        File file = Util.getFileFromHash(new File(this.output, viewFolder), hash, "jpg"); //$NON-NLS-1$
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }

    private String getRelativePath(File file, File refFolder) {
        Path pathAbsolute = file.toPath();
        Path pathBase = refFolder.toPath();
        return pathBase.relativize(pathAbsolute).toString().replace('\\', '/');
    }

    private void createImageThumb(IItem evidence, File thumbFile) {
        if (!thumbFile.getParentFile().exists()) {
            thumbFile.getParentFile().mkdirs();
        }
        try {
            if (evidence.getThumb() != null) {
                Files.write(thumbFile.toPath(), evidence.getThumb());
                return;
            }
            BufferedImage img = null;
            if (ImageThumbTask.extractThumb && ImageThumbTask.isJpeg(evidence)) { // $NON-NLS-1$
                BufferedInputStream stream = evidence.getBufferedStream();
                try {
                    img = ImageMetadataUtil.getThumb(stream);
                } finally {
                    IOUtil.closeQuietly(stream);
                }
            }
            if (img == null) {
                final int sampleFactor = 3;
                BufferedInputStream stream = evidence.getBufferedStream();
                try {
                    img = ImageUtil.getSubSampledImage(stream, thumbSize * sampleFactor, thumbSize * sampleFactor);
                } finally {
                    IOUtil.closeQuietly(stream);
                }
                if (img == null) {
                    stream = evidence.getBufferedStream();
                    try {
                        img = externalImageConverter.getImage(stream, thumbSize, false, evidence.getLength());
                    } finally {
                        IOUtil.closeQuietly(stream);
                    }
                }
            }
            if (img != null) {
                if (img.getWidth() > thumbSize || img.getHeight() > thumbSize) {
                    img = resizeThumb(img);
                }
                img = ImageUtil.getCenteredImage(img, thumbSize, thumbSize);
                ImageIO.write(img, "jpeg", thumbFile); //$NON-NLS-1$
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createVideoStripe(ReportEntry reg, File thumbFile) {
        if (!thumbFile.getParentFile().exists()) {
            thumbFile.getParentFile().mkdirs();
        }
        createStripeFile(getVideoThumbsFile(reg.hash), thumbFile);
    }

    private void createStripeFile(File in, File out) {
        try {
            if (!in.exists()) {
                return;
            }
            Object[] read = ImageUtil.readJpegWithMetaData(in);
            BufferedImage img = (BufferedImage) read[0];
            String comment = (String) read[1];
            int nRows = 1;
            int nCols = 1;
            if (comment != null && comment.startsWith("Frames")) { //$NON-NLS-1$
                int p1 = comment.indexOf('=');
                int p2 = comment.indexOf('x');
                if (p1 > 0 && p2 > 0) {
                    nRows = Integer.parseInt(comment.substring(p1 + 1, p2));
                    nCols = Integer.parseInt(comment.substring(p2 + 1));
                }
            }

            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();

            final int border = 2;
            int frameWidth = (imgWidth - 2 * border - border * nCols) / nCols;
            int frameHeight = (imgHeight - 2 * border - border * nRows) / nRows;

            int w = videoStripeWidth / framesPerStripe;
            double rate = (nRows * nCols) * 0.999 / framesPerStripe;
            int h = frameHeight * w / frameWidth;

            BufferedImage stripe = new BufferedImage(framesPerStripe * (w + 1) + 3, h + 4, BufferedImage.TYPE_INT_BGR);
            Graphics2D g2 = (Graphics2D) stripe.getGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.setColor(new Color(222, 222, 222));
            g2.fillRect(0, 0, stripe.getWidth(), stripe.getHeight());
            g2.setColor(new Color(22, 22, 22));
            g2.drawRect(0, 0, stripe.getWidth() - 1, stripe.getHeight() - 1);

            double pos = rate * 0.4;
            for (int j = 0; j < framesPerStripe; j++) {
                int x = j * (w + 1) + 2;
                int idx = Math.min(nCols * nRows - 1, (int) pos);
                int sx = border + (border + frameWidth) * (idx % nCols);
                int sy = border + (border + frameHeight) * (idx / nCols);
                g2.drawImage(img, x, 2, x + w, 2 + h, sx, sy, sx + frameWidth, sy + frameHeight, null);
                pos += rate;
            }
            g2.dispose();

            ImageIO.write(stripe, "jpeg", out); //$NON-NLS-1$
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BufferedImage resizeThumb(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        if (width > height) {
            height = height * thumbSize / width;
            width = thumbSize;
        } else {
            width = width * thumbSize / height;
            height = thumbSize;
        }
        return ImageUtil.resizeImage(img, width, height);
    }

    private static void replace(StringBuilder sb, String a, String b) {
        int pos = 0;
        while ((pos = sb.indexOf(a, pos)) >= 0) {
            String rep = b == null ? "-" : b; //$NON-NLS-1$
            sb.replace(pos, pos + a.length(), rep);
            pos += rep.length();
        }
    }

    private static String formatDate(Date date, DateFormat dateFormat) {
        return date == null ? "-" : dateFormat.format(date); //$NON-NLS-1$
    }

    private static String formatNumber(Long val, NumberFormat longFormat) {
        return val == null ? "-" : longFormat.format(val); //$NON-NLS-1$
    }

    /**
     * Gera páginas com galeria de miniaturas de imagens.
     */
    private void createThumbsPage() {
        int n = 0;
        int page = 1;
        StringBuilder sb = new StringBuilder();

        int tot = 0;
        for (String label : imageThumbsByLabel.keySet()) {
            List<String> l = imageThumbsByLabel.get(label);
            tot += l.size();
        }
        int np = (tot + thumbsPerPage - 1) / thumbsPerPage;

        for (String bookmark : imageThumbsByLabel.keySet()) {
            List<String> l = imageThumbsByLabel.get(bookmark);
            addBookmarkTitle(sb, bookmark, l.size(), !entriesByLabel.isEmpty());
            int cnt = 0;
            for (String s : l) {
                n++;
                sb.append(s);
                sb.append("\n"); //$NON-NLS-1$
                if (n >= thumbsPerPage) {
                    addPageControl(page, np, sb);
                    writeThumbsPage(sb, new File(reportSubFolder, pageName(page)));
                    page++;
                    n = 0;
                    sb.delete(0, sb.length());
                    if (++cnt < l.size()) {
                        addBookmarkTitle(sb, bookmark, l.size(), !entriesByLabel.isEmpty());
                    }
                }
            }
        }
        if (n > 0) {
            addPageControl(page, np, sb);
            writeThumbsPage(sb, new File(reportSubFolder, pageName(page)));
        }
    }

    private void addPageControl(int page, int np, StringBuilder sb) {
        StringBuilder sp = new StringBuilder();
        sp.append("<table width=\"100%\"><tr><td>" + Messages.getString("HTMLReportTask.Page") + " ").append(page) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append(Messages.getString("HTMLReportTask.of")).append(np).append("</td>"); //$NON-NLS-1$ //$NON-NLS-2$

        if (page > 1) {
            sp.append("<td><a href=\"").append(pageName(1)) //$NON-NLS-1$
                    .append("\">&lt;&lt;&lt;&lt;" + Messages.getString("HTMLReportTask.FirstPage") + "</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sp.append("<td><a href=\"").append(pageName(page - 1)) //$NON-NLS-1$
                    .append("\">&lt;&lt;" + Messages.getString("HTMLReportTask.PrevPage") + "</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if (page < np) {
            sp.append("<td><a href=\"").append(pageName(page + 1)) //$NON-NLS-1$
                    .append("\">" + Messages.getString("HTMLReportTask.NextPage") + "&gt;&gt;</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sp.append("<td><a href=\"").append(pageName(np)) //$NON-NLS-1$
                    .append("\">" + Messages.getString("HTMLReportTask.LastPage") + "&gt;&gt;&gt;&gt;</a></td>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        sp.append("</tr></table>\n"); //$NON-NLS-1$

        sb.insert(0, sp.toString());
        sb.append(sp.toString());
    }

    private String pageName(int page) {
        return "thumbs_" + (page / 100) + "" + (page % 100 / 10) + "" + page % 10 + ".htm"; //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private void addBookmarkTitle(StringBuilder sb, String bookmark, int size, boolean isLabel) {
        sb.append("<table width=\"100%\"><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\">"); //$NON-NLS-1$
        if (isLabel) {
            sb.append(Messages.getString("HTMLReportTask.Bookmark") + ": "); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            sb.append(Messages.getString("HTMLReportTask.Category") + ": "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append(bookmark);
        sb.append("</th></tr><tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">" //$NON-NLS-1$
                + Messages.getString("HTMLReportTask.FileCount") + ": </span>"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append(size);
        sb.append("</td></tr></table>"); //$NON-NLS-1$
    }

    private void writeThumbsPage(StringBuilder sb, File f) {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/common.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/bookmarks.css\"/><title>" //$NON-NLS-1$
                + Messages.getString("HTMLReportTask.GalleryTitle") //$NON-NLS-1$
                + "</title><style>\n.thumb {width:auto; height:auto; max-width:112px; max-height:112px;}\n</style></head><body>\n<p><img border=\"0\" src=\"res/header.gif\"/>\n\n"; //$NON-NLS-1$
        sb.insert(0, header);
        sb.append("\n<p><img border=\"0\" src=\"res/header.gif\"/></p></body></html>"); //$NON-NLS-1$
        EncodedFile ef = new EncodedFile(sb, Charset.forName("UTF-8"), f); //$NON-NLS-1$
        try {
            ef.write();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

/**
 * Classe de dados que armazena informações que serão incluídas no relatório.
 *
 * @author Wladimir
 */
class ReportEntry {
    String name, export, ext, category, hash, path, img;
    Long length;
    boolean deleted, carved, isImage, isVideo;
    Date accessed, modified, created;
}

/**
 * Classe auxiliar para ler/escrever arquivos mantendo sua codificação original.
 */
class EncodedFile {

    public StringBuilder content;
    public Charset charset;
    public File file;

    public EncodedFile(StringBuilder sb, Charset charset, File file) {
        super();
        this.content = sb;
        this.file = file;
        this.charset = charset;
    }

    public static EncodedFile readFile(File f, Charset cs) throws Exception {
        InputStreamReader in = new InputStreamReader(new FileInputStream(f), cs);
        char[] buf = new char[(int) f.length()];
        int size = in.read(buf);
        in.close();
        StringBuilder sb = new StringBuilder();
        sb.append(buf, 0, size);
        return new EncodedFile(sb, cs, f);
    }

    public void write() throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter out = new BufferedWriter(
                charset == null ? new OutputStreamWriter(fos) : new OutputStreamWriter(fos, charset));
        out.write(content.toString());
        out.close();
        fos.close();
    }
}

/**
 * Comparador simples de Strings, que ignora maísculas/minúsculas e trata
 * acentuação básica, mas sem o overhead de performance do Collator do Java.
 */
class CustomComparator implements Comparator<String> {
    private final char[] map = new char[Character.MAX_VALUE + 1];
    private static final String[] mappings = new String[] { "A", "ÁÀÂÃÄáàâãä", "E", "ÉÈÊËéèêë", "I", "ÍÌÎÏíìîï", "O", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
            "ÓÒÕÔÖóòõôö", "U", "ÚÙÜÛúùüû", "C", "Çç", "N", "Ññ" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

    public CustomComparator() {
        for (int i = 0; i < mappings.length; i += 2) {
            char to = mappings[i].charAt(0);
            char[] froms = mappings[i + 1].toCharArray();
            for (char from : froms) {
                map[from] = to;
            }
        }
    }

    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        for (; i < a.length() && j < b.length(); i++, j++) {
            char c = a.charAt(i);
            char d = b.charAt(j);
            if (c == d)
                continue;
            if (c >= 'a' && c <= 'z')
                c -= 32;
            else if (c >= 128) {
                char m = map[c];
                if (m != 0)
                    c = m;
            }
            if (d >= 'a' && d <= 'z')
                d -= 32;
            else if (d >= 128) {
                char m = map[d];
                if (m != 0)
                    d = m;
            }
            if (c < d)
                return -1;
            if (c > d)
                return 1;
        }
        if (i < a.length() && j == b.length())
            return 1;
        if (i == a.length() && j < b.length())
            return -1;
        return 0;
    }
}
