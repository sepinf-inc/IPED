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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.search.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Tarefa de geração de relatório no formato HTML do itens selecionados, gerado
 * quando a entrada do processamento é um arquivo ".IPED".
 * @author Wladimir Leite
 */
public class HTMLReportTask extends AbstractTask {
    /**
     * Collator utilizado para ordenação correta alfabética, incluindo acentuação.
     */
    private static final Collator collator = Collator.getInstance(new Locale("pt", "BR"));

    /** Nome da tarefa. */
    private static final String taskName = "Geração de Relatório HTML";

    /** Nome da subpasta com versões de visualização dos arquivos. */
    public static final String viewFolder = "view";

    /** Registros organizados por marcador. */
    private static final SortedMap<String, List<ReportEntry>> entriesByLabel = new TreeMap<String, List<ReportEntry>>(collator);

    /** Registros organizados por categoria. */
    private static final SortedMap<String, List<ReportEntry>> entriesByCategory = new TreeMap<String, List<ReportEntry>>(collator);

    /** Registros sem marcador. */
    private static final List<ReportEntry> entriesNoLabel = new ArrayList<ReportEntry>();

    /** Indicador de inicialização, para controle de sincronização entre instâncias da classe. */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /** Objeto com informações que serão incluídas no relatório. */
    private ReportInfo info;

    /** Nome da pasta com miniatutas de imagem. */
    public static String thumbsFolderName = "thumbs";

    /** Nome da subpasta destino dos os arquivos do relatório. */
    public static String reportSubFolderName = "relatorio";

    /** 
     * Subpasta com a maior parte dos arquivos HTML e arquivos auxiliares. 
     * Static porque pode ser utilizada por todas as instâncias (uma utilizada em cada thread
     * de processamento). 
     */
    private static File reportSubFolder;

    /** Indica se a tarefa está habilitada ou não. */
    private static boolean taskEnabled = false;

    /** Flag de controle se a geração de miniaturas de imagem está habilitada. */
    private static boolean imageThumbsEnabled = false;

    /** Flag de controle se a inclusão de miniaturas de cenas de vídeos (que devem estar disponíveis) está habilitada. */
    private static boolean videoThumbsEnabled = false;

    /** Map com miniaturas de imagem organizadas por marcador, utilizado para geração de galeria de imagens. */
    private static final SortedMap<String, List<String>> imageThumbsByLabel = new TreeMap<String, List<String>>(collator);

    /** 
     * Flag de controle que indica que uma lista de categorias deve se mostrada na página de conteúdo,
     * além da lista de marcadores, que é incluída como padrão.
     */
    private static boolean categoriesListEnabled = false;

    /** Tamanho da miniatura (utilizado no HTML). */
    private static int thumbSize = 112;

    /** Quantidade de frames utilizado na "faixa" de cenas de vídeo. */
    private static int framesPerStripe = 8;

    /** Largura (em pixels) da "faixa" de cenas de vídeo. */
    private static int videoStripeWidth = 800;

    /** Itens por página HTML. */
    private static int itemsPerPage = 100;

    /** Flag de controle se página com galeria de miniaturas de imagem é criada. */
    private static boolean thumbsPageEnabled = false;

    /** Miniaturas de imagem na página de galeria. */
    private static int thumbsPerPage = 500;

    /** Constante com o nome utilizado para o arquivo de propriedades. */
    private static final String configFileName = "HTMLReportConfig.txt";

    /** Formato de datas no relatório. */
    private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    /** Formato de números no relatório. */
    private final DecimalFormat longFormat = new DecimalFormat("#,##0");

    /** 
     * Set com arquivos em processamento, estático e sincronizado para evitar que duas threads processem
     * arquivos duplicados simultaneamente, no caso de geração de miniaturas.
     */
    private static final Set<String> currentFiles = new HashSet<String>();

    /** Construtor. */
    public HTMLReportTask() {
        collator.setStrength(Collator.TERTIARY);
    }

    /**
     * Inicializa tarefa, realizando controle de alocação de apenas uma thread principal. 
     */
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        synchronized (init) {
            if (!init.get()) {
                //Verifica se tarefa está habilitada
                String value = confParams.getProperty("enableHTMLReport");
                if (value != null && value.trim().equalsIgnoreCase("true")) {
                    taskEnabled = true;
                    Log.info(taskName, "Tarefa habilitada.");
                } else {
                    Log.info(taskName, "Tarefa desabilitada.");
                    init.set(true);
                    return;
                }

                info = new ReportInfo();
                reportSubFolder = new File(this.output.getParentFile(), reportSubFolderName);

                //Lê parâmetros do arquivo de configuração
                UTF8Properties properties = new UTF8Properties();
                File confFile = new File(confDir, configFileName);
                try {
                    properties.load(confFile);

                    value = properties.getProperty("ItemsPerPage");
                    if (value != null) itemsPerPage = Integer.parseInt(value.trim());

                    value = properties.getProperty("ThumbsPerPage");
                    if (value != null) thumbsPerPage = Integer.parseInt(value.trim());

                    value = properties.getProperty("ThumbSize");
                    if (value != null) thumbSize = Integer.parseInt(value.trim());

                    value = properties.getProperty("EnableImageThumbs");
                    if (value != null && value.equalsIgnoreCase("true")) imageThumbsEnabled = true;

                    value = properties.getProperty("EnableVideoThumbs");
                    if (value != null && value.equalsIgnoreCase("true")) videoThumbsEnabled = true;

                    value = properties.getProperty("FramesPerStripe");
                    if (value != null) framesPerStripe = Integer.parseInt(value.trim());

                    value = properties.getProperty("VideoStripeWidth");
                    if (value != null) videoStripeWidth = Integer.parseInt(value.trim());

                    value = properties.getProperty("EnableCategoriesList");
                    if (value != null && value.equalsIgnoreCase("true")) categoriesListEnabled = true;

                    value = properties.getProperty("EnableThumbsGallery");
                    if (value != null && value.equalsIgnoreCase("true")) thumbsPageEnabled = true;

                    info.cabecalho = properties.getProperty("Cabecalho");
                    info.classe = properties.getProperty("Classe");
                    info.dataLaudo = properties.getProperty("DataLaudo");
                    info.dataDocumento = properties.getProperty("DataDocumento");
                    info.dataProtocolo = properties.getProperty("DataProtocolo");
                    info.documento = properties.getProperty("Documento");
                    info.ipl = properties.getProperty("Ipl");
                    info.laudo = properties.getProperty("Laudo");
                    info.material = properties.getProperty("Material");
                    info.matricula = properties.getProperty("Matricula");
                    info.perito = properties.getProperty("Perito");
                    info.protocolo = properties.getProperty("Protocolo");
                    info.solicitante = properties.getProperty("Solicitante");
                    info.titulo = properties.getProperty("Titulo");
                } catch (Exception e) {
                    e.printStackTrace();
                    init.set(true);
                    throw new RuntimeException("Erro lendo arquivo de configuração da tarefa de geração de relatório HTML:" + confFile.getAbsolutePath());
                }

                //Obtém parâmetro ASAP, com arquivo contendo informações do caso, se este foi específicado 
                CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
                if (args != null) {
                    List<String> info = args.getCmdArgs().get("-asap");
                    if (info != null && info.size() > 0) {
                        File infoFile = new File(info.get(0));
                        if (infoFile != null) {
                            Log.info(taskName, "Processando arquivo com informações do caso: " + infoFile.getAbsolutePath());
                            if (!infoFile.exists()) {
                                throw new RuntimeException("Arquivo não encontrado:" + infoFile.getAbsolutePath());
                            }
                            try {
                                readInfoFile(infoFile);
                            } catch (Exception e) {
                                e.printStackTrace();
                                init.set(true);
                                throw new RuntimeException("Erro processando arquivo com informações do caso:" + infoFile.getAbsolutePath());
                            }
                        }
                    }
                }
                init.set(true);
            }
        }
    }

    /**
     * Finaliza a tarefa, gerando os arquivos HTML do relatório na instância que contém o objeto info.
     */
    @Override
    public void finish() throws Exception {
        if (taskEnabled && caseData.containsReport() && info != null) {
            IndexFiles.getInstance().firePropertyChange("mensagem", "", "Gerando relatório HTML...");

            // Pasta com arquivos HTML formatado que são utilizados como entrada.
            String codePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("+", "/+");
            codePath = URLDecoder.decode(codePath, "utf-8");
            codePath = codePath.replace("/ ", "+");
            File templatesFolder = new File(new File(codePath).getParent(), "htmlreport");

            Log.info(taskName, "Pasta do relatório: " + reportSubFolder.getAbsolutePath());
            Log.info(taskName, "Pasta de modelos:   " + templatesFolder.getAbsolutePath());
            if (!templatesFolder.exists()) {
                throw new FileNotFoundException("Para de modelos não encontrada!");
            }

            long t = System.currentTimeMillis();

            File rep = reportSubFolder;
            if (!rep.exists()) rep.mkdir();
            processBookmarks(templatesFolder);
            if (thumbsPageEnabled && !imageThumbsByLabel.isEmpty()) createThumbsPage();
            processCaseInfo(new File(templatesFolder, "caseinformation.htm"), new File(reportSubFolder, "caseinformation.htm"));
            processContents(new File(templatesFolder, "contents.htm"), new File(reportSubFolder, "contents.htm"));
            copyFile(new File(templatesFolder, "relatorio.htm"), reportSubFolder.getParentFile());
            copyFile(new File(templatesFolder, "ajuda.htm"), reportSubFolder);
            copyFiles(new File(templatesFolder, "res"), new File(reportSubFolder, "res"));

            t = (System.currentTimeMillis() - t + 500) / 1000;
            Log.info(taskName, "Tempo de Geração do Relatório (em segundos):  " + t);
        }
    }

    /**
     * Processa um item, extraindo as informações a serem utilizadas no relatório e
     * incluindo nas listas adequadas.
     */
    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        if (!taskEnabled || !caseData.containsReport() || !evidence.isToAddToCase()) return;

        ReportEntry reg = new ReportEntry();
        reg.name = evidence.getName();
        reg.export = evidence.getExportedFile();
        reg.isImage = isImageType(evidence.getMediaType());
        reg.isVideo = VideoThumbTask.isVideoType(evidence.getMediaType());
        reg.length = evidence.getLength();
        reg.ext = evidence.getExt();
        reg.category = evidence.getCategories().replace(CategoryTokenizer.SEPARATOR + "", " | ");
        reg.hash = evidence.getHash();
        reg.deleted = evidence.isDeleted();
        reg.carved = evidence.isCarved();
        reg.accessed = evidence.getAccessDate();
        reg.modified = evidence.getModDate();
        reg.created = evidence.getCreationDate();
        reg.path = evidence.getPath();

        String[] labels = evidence.getLabels() == null ? new String[] {""} : evidence.getLabels().split("\\|");
        String[] categories = reg.category.split("\\|");
        synchronized (init) {
            for (String label : labels) {
                label = label.trim();
                List<ReportEntry> regs = label.length() == 0 ? entriesNoLabel : entriesByLabel.get(label);
                if (regs == null) entriesByLabel.put(label, regs = new ArrayList<ReportEntry>());
                regs.add(reg);
            }
            for (String category : categories) {
                category = category.trim();
                List<ReportEntry> regs = entriesByCategory.get(category);
                if (regs == null) entriesByCategory.put(category, regs = new ArrayList<ReportEntry>());
                regs.add(reg);
            }
        }

        if (((imageThumbsEnabled && reg.isImage) || (videoThumbsEnabled && reg.isVideo)) && reg.hash != null) {
            //Verifica se há outro arquivo igual em processamento, senão inclui
            synchronized (currentFiles) {
                if (currentFiles.contains(evidence.getHash())) return;
                currentFiles.add(evidence.getHash());
            }
            File thumbFile = getThumbFile(reg.hash);
            if (reg.isImage) createImageThumb(evidence, thumbFile);
            else if (reg.isVideo) createVideoThumb(reg, thumbFile);

            //Retira do Set de arquivos em processamento
            synchronized (currentFiles) {
                currentFiles.remove(evidence.getHash());
            }
        }
    }

    private void copyFiles(File src, File target) throws IOException {
        if (!target.exists()) target.mkdir();
        File[] arqs = src.listFiles();
        for (File arq : arqs) {
            if (!arq.isFile()) continue;
            File tf = new File(target, arq.getName());
            if (tf.exists()) continue;
            Files.copy(arq.toPath(), tf.toPath());
        }
    }

    private void copyFile(File src, File targetFolder) throws IOException {
        Files.copy(src.toPath(), new File(targetFolder, src.getName()).toPath());
    }

    private void processCaseInfo(File src, File target) throws Exception {
        EncodedFile arq = EncodedFile.readFile(src, "iso-8859-1");
        replace(arq.content, "%LAUDO%", info.laudo);
        replace(arq.content, "%DATALAUDO%", info.dataLaudo);
        replace(arq.content, "%PERITO%", info.perito);
        replace(arq.content, "%CLASSE%", info.classe + "a Classe");
        replace(arq.content, "%MAT%", info.matricula);
        replace(arq.content, "%CABECALHO%", info.cabecalho);
        replace(arq.content, "%TITULO%", info.titulo);
        replace(arq.content, "%IPL%", info.ipl);
        replace(arq.content, "%DOC%", info.documento + " de " + info.dataDocumento);
        replace(arq.content, "%SOL%", info.solicitante);
        replace(arq.content, "%PROT%", info.protocolo);
        replace(arq.content, "%DATAPROT%", info.dataProtocolo);
        replace(arq.content, "%MATERIAL%", info.material);
        arq.file = target;
        arq.write();
    }

    private void processContents(File src, File target) throws Exception {
        StringBuilder sb = new StringBuilder();

        int idx = 1;
        if (!entriesByLabel.isEmpty()) {
            sb.append("<p>\n");
            sb.append("\t<span class=\"SmallText1\">Marcadores</span>\n");
            for (String marcador : entriesByLabel.keySet()) {
                sb.append("\t\t<br />&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"arq");
                sb.append(String.format("%06d", idx));
                sb.append("(1).html\" target=\"ReportPage\" class=\"MenuText\">");
                sb.append(marcador);
                sb.append("</a>\n");
                idx++;
            }
            sb.append("</p>\n");
        }

        if (categoriesListEnabled && !entriesByCategory.isEmpty()) {
            sb.append("<p>\n");
            sb.append("\t<span class=\"SmallText1\">Categorias</span>\n");
            for (String categoria : entriesByCategory.keySet()) {
                sb.append("\t\t<br />&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"arq");
                sb.append(String.format("%06d", idx));
                sb.append("(1).html\" target=\"ReportPage\" class=\"MenuText\">");
                sb.append(categoria);
                sb.append("</a>\n");
                idx++;
            }
            sb.append("</p>");
        }

        if (thumbsPageEnabled && !imageThumbsByLabel.isEmpty()) {
            sb.append("<p>\n");
            sb.append("<b><a href=\"miniaturas_001.htm\" class=\"SmallText2\" target=\"ReportPage\">");
            sb.append("Galeria de Imagens</a></b></p>\n");
        }

        EncodedFile arq = EncodedFile.readFile(src, "iso-8859-1");
        replace(arq.content, "%BOOKMARKS%", sb.toString());

        arq.file = target;
        arq.write();
    }

    private void processBookmarks(File templatesFolder) throws Exception {
        StringBuilder modelo = EncodedFile.readFile(new File(templatesFolder, "modelos/arq.html"), "utf-8").content;
        StringBuilder item = EncodedFile.readFile(new File(templatesFolder, "modelos/item.html"), "utf-8").content;
        int idx = 1;
        for (String marcador : entriesByLabel.keySet()) {
            String id = String.format("arq%06d", idx);
            processaBookmark(marcador, id, modelo, item, true, entriesByLabel.get(marcador));
            idx++;
        }
        for (String categoria : entriesByCategory.keySet()) {
            String id = String.format("arq%06d", idx);
            processaBookmark(categoria, id, modelo, item, false, entriesByCategory.get(categoria));
            idx++;
        }
    }

    private void processaBookmark(String name, String id, StringBuilder model, StringBuilder item, boolean isLabel, List<ReportEntry> regs) throws Exception {
        Collections.sort(regs, new Comparator<ReportEntry>() {
            public int compare(ReportEntry a, ReportEntry b) {
                int cmp = collator.compare(a.path, b.path);
                if (cmp == 0) cmp = collator.compare(a.name, b.name);
                return cmp;
            }
        });

        int tot = regs.size();
        int numPages = (tot + itemsPerPage - 1) / itemsPerPage;
        int start = 0;
        for (int page = 1; page <= numPages; page++) {
            int end = Math.min(tot, start + itemsPerPage);
            createBookmarkPage(name, id, model, item, page, numPages, tot, regs.subList(start, end), isLabel);
            start = end;
        }
    }

    private String getPageId(String id, int page) {
        return id + "(" + page + ").html";
    }

    private void createBookmarkPage(String name, String id, StringBuilder model, StringBuilder item, int pag, int totPags, int totRegs, List<ReportEntry> regs, boolean isLabel) throws Exception {
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
                File thumbFile = getThumbFile(reg.hash);
                if (thumbFile.exists()) {
                    it.append("<table width=\"100%\"><tr><td>");

                    StringBuilder img = new StringBuilder();
                    img.append("<a href=\"");
                    img.append("../").append(reg.export);
                    img.append("\"/><img src=\"");
                    img.append(getRelativePath(thumbFile,reportSubFolder));
                    img.append("\" width=\"");
                    img.append(thumbSize);
                    img.append("\" height=\"");
                    img.append(thumbSize);
                    img.append("\"/>");

                    it.append(img);
                    it.append("</td></tr></table>\n");

                    if (isLabel) {
                        List<String> l = imageThumbsByLabel.get(name);
                        if (l == null) imageThumbsByLabel.put(name, l = new ArrayList<String>());
                        l.add(img.toString());
                    }
                }
            } else if (reg.isVideo && videoThumbsEnabled && reg.hash != null) {
                File videoThumbsFile = getVideoThumbsFile(reg.hash);
                File stripeFile = getThumbFile(reg.hash);
                if (stripeFile.exists()) {
                    Dimension dim = ImageUtil.getImageFileDimension(stripeFile);
                    it.append("<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">Cenas Extraídas do Vídeo</span><span class=\"bkmkColRight bkmkValue\"><a href=\"");
                    it.append(getRelativePath(videoThumbsFile, reportSubFolder));
                    it.append("\"><img src=\"");
                    it.append(getRelativePath(stripeFile, reportSubFolder)).append("\"");
                    if (dim != null) {
                        it.append(" width=\"").append(dim.width).append("\"");
                        it.append(" height=\"").append(dim.height).append("\"");
                    }
                    it.append(">");
                    it.append("</a></span></div><div class=\"row\">&nbsp;</div>\n");
                }
            } else if (!reg.isVideo && !reg.isImage && reg.hash != null) {
                File view = Util.findFileFromHash(new File(this.output, viewFolder), reg.hash);
                if (view != null) {
                    it.append("<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">Versão de Visualização</span><span class=\"bkmkColRight bkmkValue\"><a href=\"");
                    it.append(getRelativePath(view, reportSubFolder));
                    it.append("\">");
                    it.append(view.getName());
                    it.append("</a></span></div>\n");
                }
            }

            replace(it, "%SEQ%", reg.hash);
            replace(it, "%NOME%", reg.name);
            replace(it, "%CAMINHO%", reg.path);
            replace(it, "%TIPO%", reg.category);
            replace(it, "%TAMANHO%", formatNumber(reg.length));
            replace(it, "%EXCLUIDO%", reg.deleted ? "Sim" : "N&atilde;o");
            replace(it, "%CARVED%", reg.carved ? "Sim" : "N&atilde;o");
            replace(it, "%HASH%", reg.hash);
            replace(it, "%EXP%", "../" + reg.export);
            replace(it, "%EXP_DESC%", reg.export);
            replace(it, "%DT_CRIACAO%", formatDate(reg.created));
            replace(it, "%DT_MOD%", formatDate(reg.modified));
            replace(it, "%DT_ACESSO%", formatDate(reg.accessed));
            items.append(it);
        }

        StringBuilder p = new StringBuilder();
        p.append("<table width=\"100%\">\n");
        p.append("<tr><td>P&aacute;gina %PAG% de %TOTPAG%</td>");
        replace(p, "%PAG%", String.valueOf(pag));
        replace(p, "%TOTPAG%", String.valueOf(totPags));
        if (totPags > 1) {
            if (pag > 1) {
                p.append("<td><a href=\"").append(getPageId(id, 1)).append("\">&lt;&lt;&lt;&lt;Primeira P&aacute;gina</a></td>\n");
                p.append("<td><a href=\"").append(getPageId(id, pag - 1)).append("\">&lt;&lt;P&aacute;gina anterior</a></td>\n");
            }
            if (pag < totPags) {
                p.append("<td><a href=\"").append(getPageId(id, pag + 1)).append("\">Pr&oacute;xima p&aacute;gina&gt;&gt;</a></td>\n");
                p.append("<td><a href=\"").append(getPageId(id, totPags)).append("\">&Uacute;ltima P&aacute;gina&gt;&gt;&gt;&gt;</a></td>\n");
            }
        }
        p.append("</tr></table>\n");

        replace(sb, "%CATEGORIA%", (isLabel ? "Marcador" : "Categoria") + ": " + name);
        replace(sb, "%CONTARQ%", String.valueOf(totRegs));
        replace(sb, "%ITEMS%", items.toString());
        replace(sb, "%PAGS%", p.toString());

        EncodedFile ef = new EncodedFile(sb, Charset.forName("utf-8"), arq);
        ef.write();
    }

    private File getThumbFile(String hash) {
        File file = Util.getFileFromHash(new File(reportSubFolder, thumbsFolderName), hash, "jpg");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        return file;
    }

    private File getVideoThumbsFile(String hash) {
    	File file = Util.getFileFromHash(new File(this.output, viewFolder), hash, "jpg");
    	if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        return file;
    }

    private String getRelativePath(File file, File refFolder) {
        Path pathAbsolute = file.toPath();
        Path pathBase = refFolder.toPath();
        return pathBase.relativize(pathAbsolute).toString().replace('\\', '/');
    }

    private void createImageThumb(EvidenceFile evidence, File thumbFile) {
        if (!thumbFile.getParentFile().exists()) thumbFile.getParentFile().mkdirs();

        try {
            GalleryValue value = new GalleryValue(null, null, -1);
            BufferedImage img = null;
            if (evidence.getMediaType().getSubtype().startsWith("jpeg")) {
            	BufferedInputStream stream = evidence.getBufferedStream();
            	try{
            		img = ImageUtil.getThumb(stream, value);
            	}finally{
            		IOUtil.closeQuietly(stream);
            	}
            }
            if (img == null) {
                final int sampleFactor = 3;
                BufferedInputStream stream = evidence.getBufferedStream();
            	try{
            		img = ImageUtil.getSubSampledImage(stream, thumbSize * sampleFactor, thumbSize * sampleFactor, value);
            	}finally{
            		IOUtil.closeQuietly(stream);
            	}
                if (img == null) {
                	stream = evidence.getBufferedStream();
                	try{
                		img = new GraphicsMagicConverter().getImage(stream, thumbSize * sampleFactor);
                	}finally{
                		IOUtil.closeQuietly(stream);
                	}
                }
            }
            if (img != null) {
                if (img.getWidth() > thumbSize || img.getHeight() > thumbSize) {
                    img = resizeThumb(img);
                }
                img = ImageUtil.getCenteredImage(img, thumbSize, thumbSize);
                ImageIO.write(img, "jpeg", thumbFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createVideoThumb(ReportEntry reg, File thumbFile) {
        if (!thumbFile.getParentFile().exists()) thumbFile.getParentFile().mkdirs();
        createStripeFile(getVideoThumbsFile(reg.hash), thumbFile);
    }

    private void createStripeFile(File in, File out) {
        try {
            if (!in.exists()) return;
            Object[] read = ImageUtil.readJpegWithMetaData(in);
            BufferedImage img = (BufferedImage) read[0];
            String comment = (String) read[1];
            int nRows = 1;
            int nCols = 1;
            if (comment != null && comment.startsWith("Frames")) {
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

            ImageIO.write(stripe, "jpeg", out);
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

    private void replace(StringBuilder sb, String a, String b) {
        while (true) {
            int pos = sb.indexOf(a);
            if (pos >= 0) sb.replace(pos, pos + a.length(), b == null ? "-" : b);
            else break;
        }
    }

    private String formatDate(Date date) {
        return date == null ? "-" : dateFormat.format(date);
    }

    private String formatNumber(Long val) {
        return val == null ? "-" : longFormat.format(val);
    }

    /**
     * Verifica se é imagem.
     */
    public static boolean isImageType(MediaType mediaType) {
        return mediaType.getType().equals("image") || mediaType.toString().endsWith("msmetafile") || mediaType.toString().endsWith("x-emf");
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
            addBookmarkTitle(sb, bookmark, l.size());
            int cnt = 0;
            for (String s : l) {
                n++;
                sb.append(s);
                sb.append("\n");
                if (n >= thumbsPerPage) {
                    addPageControl(page, np, sb);
                    writeThumbsPage(sb, new File(reportSubFolder, pageName(page)));
                    page++;
                    n = 0;
                    sb.delete(0, sb.length());
                    if (++cnt < l.size()) {
                        addBookmarkTitle(sb, bookmark, l.size());
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
        sp.append("<table width=\"100%\"><tr><td>P&aacute;gina ").append(page).append(" de ").append(np).append("</td>");

        if (page > 1) {
            sp.append("<td><a href=\"").append(pageName(1)).append("\">&lt;&lt;&lt;&lt;Primeira P&aacute;gina</a></td>\n");
            sp.append("<td><a href=\"").append(pageName(page - 1)).append("\">&lt;&lt;P&aacute;gina anterior</a></td>\n");
        }
        if (page < np) {
            sp.append("<td><a href=\"").append(pageName(page + 1)).append("\">Pr&oacute;xima p&aacute;gina&gt;&gt;</a></td>\n");
            sp.append("<td><a href=\"").append(pageName(np)).append("\">&Uacute;ltima P&aacute;gina&gt;&gt;&gt;&gt;</a></td>\n");
        }

        sp.append("</tr></table>\n");

        sb.insert(0, sp.toString());
        sb.append(sp.toString());
    }

    private String pageName(int page) {
        return "miniaturas_" + (page / 100) + "" + (page % 100 / 10) + "" + page % 10 + ".htm";
    }

    private void addBookmarkTitle(StringBuilder sb, String bookmark, int size) {
        sb.append("<table width=\"100%\"><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\"> Marcador: ");
        sb.append(bookmark);
        sb.append("</th></tr><tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">Contagem de arquivos: </span>");
        sb.append(size);
        sb.append("</td></tr></table>");
    }

    private void writeThumbsPage(StringBuilder sb, File f) {
        sb.insert(0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/common.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/bookmarks.css\"/><title>Miniaturas</title></head><body>\n<p><img border=\"0\" src=\"res/header.gif\"/>\n\n");
        sb.append("\n<p><img border=\"0\" src=\"res/header.gif\"/></p></body></html>");
        EncodedFile ef = new EncodedFile(sb, Charset.forName("UTF-8"), f);
        try {
            ef.write();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 
     * Lê arquivo com informações do caso (para inclusão em página informativa do relatório). 
     */
    private void readInfoFile(File asap) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(asap), Charset.forName("cp1252")));
        String str = null;
        String subTit = "";
        String numero = "";
        String unidade = "";
        String matDesc = "";
        String matNum = "";
        while ((str = in.readLine()) != null) {
            String[] s = str.split("=", 2);
            if (s.length < 2) continue;
            if (s[0].equalsIgnoreCase("Titulo")) info.titulo = s[1];
            else if (s[0].equalsIgnoreCase("Subtitulo")) subTit = s[1];
            else if (s[0].equalsIgnoreCase("Unidade")) unidade = s[1];
            else if (s[0].equalsIgnoreCase("Numero")) numero = s[1];
            else if (s[0].equalsIgnoreCase("Data")) info.dataLaudo = s[1];
            else if (s[0].equalsIgnoreCase("PCF1")) {
                String[] v = s[1].split("\\|");
                info.perito = v[0];
                if (v.length >= 2) info.matricula = v[1];
                if (v.length >= 3) info.classe = v[2];
            } else if (s[0].equalsIgnoreCase("MATERIAL_DESCR")) matDesc = s[1];
            else if (s[0].equalsIgnoreCase("MATERIAL_NUMERO")) matNum = s[1];
            else if (s[0].equalsIgnoreCase("NUMERO_IPL")) info.ipl = s[1];
            else if (s[0].equalsIgnoreCase("AUTORIDADE")) info.solicitante = s[1];
            else if (s[0].equalsIgnoreCase("DOCUMENTO")) info.documento = s[1];
            else if (s[0].equalsIgnoreCase("DATA_DOCUMENTO")) info.dataDocumento = s[1];
            else if (s[0].equalsIgnoreCase("NUMERO_CRIMINALISTICA")) info.protocolo = s[1];
            else if (s[0].equalsIgnoreCase("DATA_CRIMINALISTICA")) info.dataProtocolo = s[1];
        }
        in.close();

        info.titulo += " (" + subTit + ")";
        info.laudo = numero + "-" + unidade;

        String[] md = matDesc.split("\\|");
        String[] mn = matNum.split("\\|");
        if (md.length != mn.length) {
            md = new String[] {matDesc};
            mn = new String[] {matNum};
        }
        StringBuilder mat = new StringBuilder();
        for (int i = 0; i < md.length; i++) {
            if (i > 0) mat.append("<br>\n");
            mat.append(md[i]).append(" (Registro Interno do Material: ").append(mn[i]).append(")");
        }
        info.material = mat.toString();
    }
}

/**
 * Classe de dados que armazena informações que serão incluídas no relatório.
 * @author Wladimir
 *
 */
class ReportEntry {
    String name, export, ext, category, hash, path;
    Long length;
    boolean deleted, carved, isImage, isVideo;
    Date accessed, modified, created;
}

/**
 * Classe auxiliar para armazenar dados utilizado na geração de página com informações do caso.
 */
class ReportInfo {
    String laudo, dataLaudo, cabecalho, titulo, perito, classe, matricula, ipl, documento, solicitante, protocolo, dataProtocolo, material, dataDocumento;
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

    public static EncodedFile readFile(File f, String charset) throws Exception {
        Charset cs = Charset.forName(charset);
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
        BufferedWriter out = new BufferedWriter(charset == null ? new OutputStreamWriter(fos) : new OutputStreamWriter(fos, charset));
        out.write(content.toString());
        out.close();
        fos.close();
    }
}