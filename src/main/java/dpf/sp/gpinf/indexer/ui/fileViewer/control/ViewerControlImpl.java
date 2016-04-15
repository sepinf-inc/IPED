package dpf.sp.gpinf.indexer.ui.fileViewer.control;

import ag.ion.bion.officelayer.application.IApplicationAssistant;
import ag.ion.bion.officelayer.application.ILazyApplicationInfo;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.internal.application.ApplicationAssistant;

import dpf.sp.gpinf.indexer.search.FileProcessor;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.StreamSource;

import dpf.sp.gpinf.indexer.ui.fileViewer.frames.EmailViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.IcePDFViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ImageViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.NoJavaFXViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TiffViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TikaHtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.Viewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ViewersRepository;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.LOExtractor;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controle da interface gráfica de visualização dos dados
 *
 * @author Marcelo Silva
 */
public class ViewerControlImpl implements ViewerControl {

  private static ViewerControlImpl instance;

  private static Logger LOGGER = LoggerFactory.getLogger(ViewerControlImpl.class);

  private volatile int result = JOptionPane.NO_OPTION;

  private String pathLO = System.getProperty("user.home") + "/.indexador/libreoffice4";

  private LibreOfficeViewer officeViewer = null;

  private ViewerControlImpl() {
  }

  public static ViewerControlImpl getInstance() {
    if (instance == null) {
      instance = new ViewerControlImpl();
    }

    return instance;
  }

  @Override
  public void createViewers(final AppSearchParams params,
      final FileProcessor exibirAjuda) {

    Thread process = new Thread() {

      @Override
      public void run() {
        final boolean javaFX = loadJavaFX();

        final ViewersRepository viewersRepository = new ViewersRepository();

        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {

              params.compositeViewer.addViewer(new HexViewer());
              params.textViewer = new TextViewer(params);
              params.compositeViewer.addViewer(params.textViewer);
              params.compositeViewer.addViewer(viewersRepository);

              viewersRepository.addViewer(new ImageViewer());

              if (javaFX) {
                viewersRepository.addViewer(new HtmlViewer());
                viewersRepository.addViewer(new EmailViewer());
                viewersRepository.addViewer(new TikaHtmlViewer());
                //multiViewer.addViewer(new VideoViewer());
              } else {
                viewersRepository.addViewer(new NoJavaFXViewer());
              }

              viewersRepository.addViewer(new IcePDFViewer());
              viewersRepository.addViewer(new TiffViewer());
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
        }

        params.compositeViewer.initViewers();

        exibirAjuda.execute();

        boolean useLO = false;
        String systemLO = null;
        String compressedLO = params.codePath + "/../tools/libreoffice.zip";
        final String useLOMsg = "Deseja ativar o visualizador de formatos Office?";

        if (System.getProperty("os.name").startsWith("Linux")) {
          try {
            IApplicationAssistant ass = new ApplicationAssistant(params.codePath + "/../lib/nativeview");
            ILazyApplicationInfo[] ila = ass.getLocalApplications();
            if (ila.length != 0) {
              LOGGER.info("Detected LibreOffice {} {}", ila[0].getMajorVersion(), ila[0].getHome());
              if (ila[0].getMajorVersion() != 4 && ila[0].getMajorVersion() != 5) {
                LOGGER.info("Install LibreOffice 4/5 to enable the Libreoffice viewer!");
              } else {
                systemLO = ila[0].getHome();
              }
            }

          } catch (OfficeApplicationException e1) {
            e1.printStackTrace();
          }
        }

        if (systemLO != null || (System.getProperty("os.name").startsWith("Windows") && (new File(pathLO).exists() || new File(compressedLO).exists()))) {

          try {
            SwingUtilities.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                result
                    = JOptionPane.showConfirmDialog(
                        params.mainFrame, useLOMsg,
                        "",
                        JOptionPane.YES_NO_OPTION);
              }
            });
          } catch (Exception e) {
            e.printStackTrace();
          }

          if (result == JOptionPane.YES_OPTION) {
            if (systemLO == null) {
              LOExtractor extractor = new LOExtractor(compressedLO, pathLO);
              useLO = extractor.decompressLO();
            } else {
              useLO = true;
            }
          }

        }

        if (systemLO != null) {
          pathLO = systemLO;
        }

        if (useLO) {
          try {
            SwingUtilities.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                officeViewer = new LibreOfficeViewer(params.codePath + "/../lib/nativeview", pathLO);
                viewersRepository.addViewer(officeViewer);
              }
            });
          } catch (Exception e) {
            e.printStackTrace();
          }

          officeViewer.init();
        }

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            params.dialogBar.setVisible(false);
          }
        });

      }
    };

    process.start();

  }

  private boolean loadJavaFX() {
    boolean javaFX = false;
    String javaVersion = System.getProperty("java.version");
    if (javaVersion.compareTo("1.7") > 0) {
      String minor = javaVersion.substring(javaVersion.indexOf("_") + 1);
      if (!javaVersion.startsWith("1.7") || Integer.valueOf(minor) >= 6) {
        String fxJar = "jfxrt.jar";
        String javaLib = System.getProperty("java.home") + File.separator + "lib";
        if (new File(javaLib + File.separator + "ext" + File.separator + fxJar).exists()) {
          javaFX = true;
        } else {
          javaFX = this.loadJar(new File(javaLib + File.separator + fxJar));
        }
      }
    }
    return javaFX;
  }

  private boolean loadJar(File file) {
    if (!file.exists()) {
      return false;
    }
    try {
      URL jarUrl = file.toURI().toURL();
      ClassLoader sysloader = ClassLoader.getSystemClassLoader();
      Class<?> sysclass = URLClassLoader.class;
      Class<?>[] parameters = new Class[]{URL.class};
      Method method = sysclass.getDeclaredMethod("addURL", parameters);
      method.setAccessible(true);
      method.invoke(sysloader, new Object[]{jarUrl});
      LOGGER.info("{} loaded", jarUrl.toString());
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

  }

  private boolean loadJarDir(File dir) {
    String[] jarNameList = dir.list();
    if (jarNameList != null) {
      for (int i = 0; i < jarNameList.length; i++) {
        File jar = new File(dir, jarNameList[i]);
        if (!loadJar(jar)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void restartLibreOffice() {
    if (officeViewer != null) {
      Thread process = new Thread() {
        @Override
        public void run() {
          officeViewer.restartLO();
          officeViewer.loadFile(new FileContentSource(officeViewer.lastFile));
        }
      };

      process.start();
    }
  }

  @Override
  public void releaseLibreOfficeFocus() {
    if (officeViewer != null) {
      officeViewer.releaseFocus();
    }
  }

  @Override
  public void addViewer(Viewer viewer) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void initViewers() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void loadFile(StreamSource file, StreamSource viewFile, String contentType, Set<String> highlightTerms) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void loadFile(StreamSource file, String contentType, Set<String> highlightTerms) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

}
