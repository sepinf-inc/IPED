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
import java.nio.charset.Charset;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.analysis.CategoryTokenizer;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.util.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.Log;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import dpf.sp.gpinf.indexer.util.Util;

/**
 * Tarefa de geração de relatório no formato HTML do itens selecionados, gerado quando a entrada do
 * processamento é um arquivo ".IPED".
 *
 * @author Wladimir Leite
 */
public class HTMLReportTask extends AbstractTask {

  /**
   * Collator utilizado para ordenação correta alfabética, incluindo acentuação.
   */
  private static final Collator collator = Collator.getInstance(new Locale("pt", "BR"));

  /**
   * Nome da tarefa.
   */
  private static final String taskName = "Geração de Relatório HTML";

  /**
   * Nome da subpasta com versões de visualização dos arquivos.
   */
  public static final String viewFolder = "view";

  /**
   * Registros organizados por marcador.
   */
  private static final SortedMap<String, List<ReportEntry>> entriesByLabel = new TreeMap<String, List<ReportEntry>>(collator);

  /**
   * Registros organizados por categoria.
   */
  private static final SortedMap<String, List<ReportEntry>> entriesByCategory = new TreeMap<String, List<ReportEntry>>(collator);

  /**
   * Registros sem marcador.
   */
  private static final List<ReportEntry> entriesNoLabel = new ArrayList<ReportEntry>();

  /**
   * Indicador de inicialização, para controle de sincronização entre instâncias da classe.
   */
  private static final AtomicBoolean init = new AtomicBoolean(false);

  /**
   * Objeto com informações que serão incluídas no relatório.
   */
  private ReportInfo info;

  /**
   * Nome da pasta com miniatutas de imagem.
   */
  public static String thumbsFolderName = "thumbs";

  /**
   * Nome da subpasta destino dos os arquivos do relatório.
   */
  public static String reportSubFolderName = "relatorio";

  /**
   * Subpasta com a maior parte dos arquivos HTML e arquivos auxiliares. Static porque pode ser
   * utilizada por todas as instâncias (uma utilizada em cada thread de processamento).
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
   * Flag de controle se a inclusão de miniaturas de cenas de vídeos (que devem estar disponíveis)
   * está habilitada.
   */
  private static boolean videoThumbsEnabled = false;

  /**
   * Map com miniaturas de imagem organizadas por marcador, utilizado para geração de galeria de
   * imagens.
   */
  private static final SortedMap<String, List<String>> imageThumbsByLabel = new TreeMap<String, List<String>>(collator);

  /**
   * Flag de controle que indica que uma lista de categorias deve se mostrada na página de conteúdo,
   * além da lista de marcadores, que é incluída como padrão.
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
  private static final String configFileName = "HTMLReportConfig.txt";

  /**
   * Set com arquivos em processamento, estático e sincronizado para evitar que duas threads
   * processem arquivos duplicados simultaneamente, no caso de geração de miniaturas.
   */
  private static final Set<String> currentFiles = new HashSet<String>();

  /**
   * Armazena modelo de formatação no nome/mat/classe do(s) perito(s).
   */
  private StringBuilder modeloPerito;

  /**
   * Construtor.
   */
  public HTMLReportTask(Worker worker) {
    super(worker);
    collator.setStrength(Collator.TERTIARY);
  }

  @Override
  public boolean isEnabled() {
    return taskEnabled;
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
          if (value != null) {
            itemsPerPage = Integer.parseInt(value.trim());
          }

          value = properties.getProperty("ThumbsPerPage");
          if (value != null) {
            thumbsPerPage = Integer.parseInt(value.trim());
          }

          value = properties.getProperty("ThumbSize");
          if (value != null) {
            thumbSize = Integer.parseInt(value.trim());
          }

          value = properties.getProperty("EnableImageThumbs");
          if (value != null && value.equalsIgnoreCase("true")) {
            imageThumbsEnabled = true;
          }

          value = properties.getProperty("EnableVideoThumbs");
          if (value != null && value.equalsIgnoreCase("true")) {
            videoThumbsEnabled = true;
          }

          value = properties.getProperty("FramesPerStripe");
          if (value != null) {
            framesPerStripe = Integer.parseInt(value.trim());
          }

          value = properties.getProperty("VideoStripeWidth");
          if (value != null) {
            videoStripeWidth = Integer.parseInt(value.trim());
          }

          value = properties.getProperty("EnableCategoriesList");
          if (value != null && value.equalsIgnoreCase("true")) {
            categoriesListEnabled = true;
          }

          value = properties.getProperty("EnableThumbsGallery");
          if (value != null && value.equalsIgnoreCase("true")) {
            thumbsPageEnabled = true;
          }

          info.cabecalho = properties.getProperty("Cabecalho");
          info.classe.add(properties.getProperty("Classe"));
          info.dataLaudo = properties.getProperty("DataLaudo");
          info.dataDocumento = properties.getProperty("DataDocumento");
          info.dataProtocolo = properties.getProperty("DataProtocolo");
          info.documento = properties.getProperty("Documento");
          info.ipl = properties.getProperty("Ipl");
          info.laudo = properties.getProperty("Laudo");
          info.material = properties.getProperty("Material");
          info.matricula.add(properties.getProperty("Matricula"));
          info.perito.add(properties.getProperty("Perito"));
          info.protocolo = properties.getProperty("Protocolo");
          info.solicitante = properties.getProperty("Solicitante");
          info.titulo = properties.getProperty("Titulo");
        } catch (Exception e) {
          e.printStackTrace();
          init.set(true);
          throw new RuntimeException("Erro lendo arquivo de configuração da tarefa de geração de relatório HTML:" + confFile.getAbsolutePath());
        }

        //Obtém parâmetro ASAP, com arquivo contendo informações do caso, se tiver sido especificado 
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

      String reportRoot = "relatorio.htm";
      if (new File(reportSubFolder.getParentFile(), reportRoot).exists()) {
        Log.error(taskName, "Relatório HTML já existente, atualização do relatório ainda não implementada!");
        return;
      }

      IndexFiles.getInstance().firePropertyChange("mensagem", "", "Gerando relatório HTML...");

      // Pasta com arquivos HTML formatado que são utilizados como entrada.
      String codePath = Configuration.appRoot;
      File templatesFolder = new File(new File(codePath), "htmlreport");

      Log.info(taskName, "Pasta do relatório: " + reportSubFolder.getAbsolutePath());
      Log.info(taskName, "Pasta de modelos:   " + templatesFolder.getAbsolutePath());
      if (!templatesFolder.exists()) {
        throw new FileNotFoundException("Para de modelos não encontrada!");
      }

      long t = System.currentTimeMillis();

      reportSubFolder.mkdirs();

      modeloPerito = EncodedFile.readFile(new File(templatesFolder, "modelos/perito.html"), "utf-8").content;
      processBookmarks(templatesFolder);
      if (thumbsPageEnabled && !imageThumbsByLabel.isEmpty()) {
        createThumbsPage();
      }
      processCaseInfo(new File(templatesFolder, "caseinformation.htm"), new File(reportSubFolder, "caseinformation.htm"));
      processContents(new File(templatesFolder, "contents.htm"), new File(reportSubFolder, "contents.htm"));
      copyFile(new File(templatesFolder, reportRoot), reportSubFolder.getParentFile());
      copyFile(new File(templatesFolder, "ajuda.htm"), reportSubFolder);
      copyFiles(new File(templatesFolder, "res"), new File(reportSubFolder, "res"));

      t = (System.currentTimeMillis() - t + 500) / 1000;
      Log.info(taskName, "Tempo de Geração do Relatório (em segundos):  " + t);
    }
  }

  /**
   * Processa um item, extraindo as informações a serem utilizadas no relatório e incluindo nas
   * listas adequadas.
   */
  @Override
  protected void process(EvidenceFile evidence) throws Exception {
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
    reg.category = evidence.getCategories().replace(CategoryTokenizer.SEPARATOR + "", " | ");
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

    String[] labels = evidence.getLabels() == null ? new String[] {""} : evidence.getLabels().split("\\|");
    String[] categories = reg.category.split("\\|");
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
      //Verifica se há outro arquivo igual em processamento, senão inclui
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

      //Retira do Set de arquivos em processamento
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
    EncodedFile arq = EncodedFile.readFile(src, "iso-8859-1");
    replace(arq.content, "%LAUDO%", info.laudo);
    replace(arq.content, "%DATALAUDO%", info.dataLaudo);
    replace(arq.content, "%SIG%", info.perito.size() > 1 ? "s" : "");
    replace(arq.content, "%PERITOS%", formatPeritos());
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

  private String formatPeritos() {
    StringBuilder ret = new StringBuilder();
    for (int i = 0; i < info.perito.size(); i++) {
      if (i > 0) {
        ret.append("<br><br>\n");
      }
      StringBuilder s = new StringBuilder();
      s.append(modeloPerito);
      replace(s, "%PERITO%", info.perito.get(i));
      replace(s, "%CLASSE%", info.classe.size() > i ? formatClass(info.classe.get(i)) : "");
      replace(s, "%MAT%", info.matricula.size() > i ? info.matricula.get(i) : "");
      ret.append(s);
    }
    return ret.toString();
  }

  private String formatClass(String str) {
    if (str != null && str.length() == 1) {
      char c = str.charAt(0);
      if (c >= '1' && c <= '3') {
        str = c + "a Classe";
      } else if (Character.toUpperCase(c) == 'E') {
        str = "Classe Especial";
      }
    }
    return str;
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
    sortRegs();
    StringBuilder modelo = EncodedFile.readFile(new File(templatesFolder, "modelos/arq.html"), "utf-8").content;
    replace(modelo, "%THUMBSIZE%", String.valueOf(thumbSize));
    StringBuilder item = EncodedFile.readFile(new File(templatesFolder, "modelos/item.html"), "utf-8").content;
    int idx = 1;
    for (String marcador : entriesByLabel.keySet()) {
      String id = String.format("arq%06d", idx);
      List<ReportEntry> regs = entriesByLabel.get(marcador);
      processaBookmark(marcador, id, modelo, item, true, regs);
      idx++;
      List<String> l = new ArrayList<String>();
      for (ReportEntry e : regs) {
        if (e.img != null) l.add(e.img);
      }
      if (!l.isEmpty()) imageThumbsByLabel.put(marcador, l);
    }
    if (categoriesListEnabled) {
      for (String categoria : entriesByCategory.keySet()) {
        String id = String.format("arq%06d", idx);
        List<ReportEntry> regs = entriesByCategory.get(categoria);
        processaBookmark(categoria, id, modelo, item, false, regs);
        idx++;
        if (entriesByLabel.isEmpty()) {
          List<String> l = new ArrayList<String>();
          for (ReportEntry e : regs) {
            if (e.img != null) l.add(e.img);
          }
          if (!l.isEmpty()) imageThumbsByLabel.put(categoria, l);
        }
      }
    }
  }

  private void sortRegs() {
    final List<List<ReportEntry>> l = new ArrayList<List<ReportEntry>>(entriesByLabel.values());
    if (categoriesListEnabled) l.addAll(entriesByCategory.values());
    Collections.sort(l, new Comparator<List<ReportEntry>>() {
      public int compare(List<ReportEntry> a, List<ReportEntry> b) {
        return Integer.compare(b.size(), a.size());
      }
    });
    final CustomComparator comparator = new CustomComparator();
    final int numThreads = Configuration.numThreads;
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
        if (threads[i] != null) threads[i].join();
      } catch (InterruptedException e) {}
    }
  }

  private void processaBookmark(final String name, final String id, final StringBuilder model, final StringBuilder item, final boolean isLabel, final List<ReportEntry> regs) throws Exception {
    final int tot = regs.size();
    final int numPages = (tot + itemsPerPage - 1) / itemsPerPage;
    final int numThreads = Configuration.numThreads;
    Thread[] threads = new Thread[numThreads];
    for (int i = 0; i < numThreads; i++) {
      final int idx = i;
      (threads[i] = new Thread() {
        public void run() {
          DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
          NumberFormat longFormat = new DecimalFormat("#,##0");
          for (int page = 1; page <= numPages; page++) {
            if (page % numThreads != idx) continue;
            int start = (page - 1) * itemsPerPage;
            int end = Math.min(tot, start + itemsPerPage);
            try {
              createBookmarkPage(dateFormat, longFormat, name, id, model, item, page, numPages, tot, regs.subList(start, end), isLabel);
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
    return id + "(" + page + ").html";
  }

  private void createBookmarkPage(DateFormat dateFormat, NumberFormat longFormat, String name, String id, StringBuilder model, StringBuilder item, int pag, int totPags, int totRegs, List<ReportEntry> regs, boolean isLabel) throws Exception {
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
          it.append("<table width=\"100%\"><tr><td>");

          StringBuilder img = new StringBuilder();
          if (reg.export != null) {
            img.append("<a href=\"");
            img.append("../").append(reg.export);
            img.append("\">");
          }
          img.append("<img src=\"");
          img.append(getRelativePath(thumbFile, reportSubFolder));
          img.append("\" class=\"thumb\" />");
          if (reg.export != null) {
            img.append("</a>");
          }
          it.append(img);
          it.append("</td></tr></table>\n");
          if (isLabel || entriesByLabel.isEmpty()) {
            reg.img = img.toString();
          }
        }
      } else if (reg.isVideo && videoThumbsEnabled && reg.hash != null) {
        File videoThumbsFile = getVideoThumbsFile(reg.hash);
        File stripeFile = getVideoStripeFile(reg.hash);
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
      } else if (!reg.isVideo && reg.hash != null) {
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
      replace(it, "%TAMANHO%", formatNumber(reg.length, longFormat));
      replace(it, "%EXCLUIDO%", reg.deleted ? "Sim" : "N&atilde;o");
      replace(it, "%CARVED%", reg.carved ? "Sim" : "N&atilde;o");
      replace(it, "%HASH%", reg.hash);
      String export = reg.export == null ? "-" : "<a href=\"../" + reg.export + "\">" + reg.export + "</a>";
      replace(it, "%EXP%", export);
      replace(it, "%DT_CRIACAO%", formatDate(reg.created, dateFormat));
      replace(it, "%DT_MOD%", formatDate(reg.modified, dateFormat));
      replace(it, "%DT_ACESSO%", formatDate(reg.accessed, dateFormat));
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

  private File getVideoStripeFile(String hash) {
    File file = Util.getFileFromHash(new File(reportSubFolder, thumbsFolderName), hash, "jpg");
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
    return file;
  }

  private File getImageThumbFile(String hash) {
    File file = Util.getFileFromHash(new File(output, ImageThumbTask.thumbsFolder), hash, "jpg");
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
    return file;
  }

  private File getVideoThumbsFile(String hash) {
    File file = Util.getFileFromHash(new File(this.output, viewFolder), hash, "jpg");
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

  private void createImageThumb(EvidenceFile evidence, File thumbFile) {
    if (!thumbFile.getParentFile().exists()) {
      thumbFile.getParentFile().mkdirs();
    }

    try {
      GalleryValue value = new GalleryValue(null, null, null);
      BufferedImage img = null;
      if (evidence.getMediaType().getSubtype().startsWith("jpeg")) {
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = ImageUtil.getThumb(stream, value);
        } finally {
          IOUtil.closeQuietly(stream);
        }
      }
      if (img == null) {
        final int sampleFactor = 3;
        BufferedInputStream stream = evidence.getBufferedStream();
        try {
          img = ImageUtil.getSubSampledImage(stream, thumbSize * sampleFactor, thumbSize * sampleFactor, value);
        } finally {
          IOUtil.closeQuietly(stream);
        }
        if (img == null) {
          stream = evidence.getBufferedStream();
          try {
            img = new GraphicsMagicConverter().getImage(stream, thumbSize * sampleFactor);
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
        ImageIO.write(img, "jpeg", thumbFile);
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

  private static void replace(StringBuilder sb, String a, String b) {
    int pos = 0;
    while ((pos = sb.indexOf(a, pos)) >= 0) {
      String rep = b == null ? "-" : b;
      sb.replace(pos, pos + a.length(), rep);
      pos += rep.length();
    }
  }

  private static String formatDate(Date date, DateFormat dateFormat) {
    return date == null ? "-" : dateFormat.format(date);
  }

  private static String formatNumber(Long val, NumberFormat longFormat) {
    return val == null ? "-" : longFormat.format(val);
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
        sb.append("\n");
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

  private void addBookmarkTitle(StringBuilder sb, String bookmark, int size, boolean isLabel) {
    sb.append("<table width=\"100%\"><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\">");
    if (isLabel) {
      sb.append("Marcador: ");
    } else {
      sb.append("Categoria: ");
    }
    sb.append(bookmark);
    sb.append("</th></tr><tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">Contagem de arquivos: </span>");
    sb.append(size);
    sb.append("</td></tr></table>");
  }

  private void writeThumbsPage(StringBuilder sb, File f) {
    sb.insert(0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/common.css\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"res/bookmarks.css\"/><title>Miniaturas</title><style>\n.thumb {width:auto; height:auto; max-width:112px; max-height:112px;}\n</style></head><body>\n<p><img border=\"0\" src=\"res/header.gif\"/>\n\n");
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
    info.perito.clear();
    info.matricula.clear();
    info.classe.clear();
    while ((str = in.readLine()) != null) {
      String[] s = str.split("=", 2);
      if (s.length < 2) {
        continue;
      }
      String chave = s[0];
      String valor = s[1];
      if (chave.equalsIgnoreCase("Titulo")) {
        info.titulo = valor;
      } else if (chave.equalsIgnoreCase("Subtitulo")) {
        subTit = valor;
      } else if (chave.equalsIgnoreCase("Unidade")) {
        unidade = valor;
      } else if (chave.equalsIgnoreCase("Numero")) {
        numero = valor;
      } else if (chave.equalsIgnoreCase("Data")) {
        info.dataLaudo = valor;
      } else if (chave.toUpperCase().startsWith("PCF")) {
        String[] v = valor.split("\\|");
        if (v.length >= 1 && v[0].length() > 0) {
          info.perito.add(v[0]);
          if (v.length >= 2) {
            info.matricula.add(v[1]);
          }
          if (v.length >= 3) {
            info.classe.add(v[2]);
          }
        }
      } else if (chave.equalsIgnoreCase("MATERIAL_DESCR")) {
        matDesc = valor;
      } else if (chave.equalsIgnoreCase("MATERIAL_NUMERO")) {
        matNum = valor;
      } else if (chave.equalsIgnoreCase("NUMERO_IPL")) {
        info.ipl = valor;
      } else if (chave.equalsIgnoreCase("AUTORIDADE")) {
        info.solicitante = valor;
      } else if (chave.equalsIgnoreCase("DOCUMENTO")) {
        info.documento = valor;
      } else if (chave.equalsIgnoreCase("DATA_DOCUMENTO")) {
        info.dataDocumento = valor;
      } else if (chave.equalsIgnoreCase("NUMERO_CRIMINALISTICA")) {
        info.protocolo = valor;
      } else if (chave.equalsIgnoreCase("DATA_CRIMINALISTICA")) {
        info.dataProtocolo = valor;
      }
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
      if (i > 0) {
        mat.append("<br>\n");
      }
      mat.append(md[i]).append(" (Registro Interno do Material: ").append(mn[i]).append(")");
    }
    info.material = mat.toString();
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
 * Classe auxiliar para armazenar dados utilizado na geração de página com informações do caso.
 */
class ReportInfo {

  String laudo, dataLaudo, cabecalho, titulo, ipl, documento, solicitante, protocolo, dataProtocolo, material, dataDocumento;
  List<String> perito = new ArrayList<String>(), classe = new ArrayList<String>(), matricula = new ArrayList<String>();
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

/**
 * Comparador simples de Strings, que ignora maísculas/minúsculas e trata
 * acentuação básica, mas sem o overhead de performance do Collator do Java.
 */
class CustomComparator implements Comparator<String> {
  private final char[] map = new char[Character.MAX_VALUE + 1];
  private static final String[] mappings = new String[] {"A","ÁÀÂÃÄáàâãä","E","ÉÈÊËéèêë","I","ÍÌÎÏíìîï","O","ÓÒÕÔÖóòõôö","U","ÚÙÜÛúùüû","C","Çç","N","Ññ"};

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
      if (c == d) continue;
      if (c >= 'a' && c <= 'z') c -= 32;
      else if (c >= 128) {
        char m = map[c];
        if (m != 0) c = m;
      }
      if (d >= 'a' && d <= 'z') d -= 32;
      else if (d >= 128) {
        char m = map[d];
        if (m != 0) d = m;
      }
      if (c < d) return -1;
      if (c > d) return 1;
    }
    if (i < a.length() && j == b.length()) return 1;
    if (i == a.length() && j < b.length()) return -1;
    return 0;
  }
}
