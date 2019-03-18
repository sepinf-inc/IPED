package dpf.sp.gpinf.indexer.ui.fileViewer.control;

import ag.ion.bion.officelayer.application.IApplicationAssistant;
import ag.ion.bion.officelayer.application.ILazyApplicationInfo;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.internal.application.ApplicationAssistant;
import dpf.sp.gpinf.indexer.IFileProcessor;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.JarLoader;
import iped3.io.StreamSource;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.EmailViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.IcePDFViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ImageViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.MetadataViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.NoJavaFXViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TiffViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TikaHtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.Viewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ViewersRepository;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.AttachmentSearcherImpl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlLinkViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.LOExtractor;

import java.io.File;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controle da interface gráfica de visualização dos dados
 *
 * @author Marcelo Silva
 */
public class ViewerControl implements IViewerControl {

  private static ViewerControl instance;

  private static Logger LOGGER = LoggerFactory.getLogger(ViewerControl.class);

  private volatile int result = JOptionPane.NO_OPTION;

  private String pathLO = System.getProperty("user.home") + "/.indexador/libreoffice4"; //$NON-NLS-1$ //$NON-NLS-2$

  private LibreOfficeViewer officeViewer = null;

  private ViewerControl() {
  }

  public static ViewerControl getInstance() {
    if (instance == null) {
      instance = new ViewerControl();
    }

    return instance;
  }

  @Override
  public void createViewers(final AppSearchParams params,
      final IFileProcessor exibirAjuda) {

    Thread process = new Thread() {

      @Override
      public void run() {
        final boolean javaFX = new JarLoader().loadJavaFX();

        final ViewersRepository viewersRepository = new ViewersRepository();

        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {

              params.compositeViewer.addViewer(new HexViewer());
              params.textViewer = new TextViewer(params);
              params.compositeViewer.addViewer(params.textViewer);
              params.compositeViewer.addViewer(new MetadataViewer());
              params.compositeViewer.addViewer(viewersRepository);

              viewersRepository.addViewer(new ImageViewer());

              if (javaFX) {
                viewersRepository.addViewer(new HtmlViewer());
                viewersRepository.addViewer(new EmailViewer());
                viewersRepository.addViewer(new HtmlLinkViewer(new AttachmentSearcherImpl()));
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
        String compressedLO = params.codePath + "/../tools/libreoffice.zip"; //$NON-NLS-1$
        final String useLOMsg = Messages.getString("EnableLibreOffice"); //$NON-NLS-1$

        if (System.getProperty("os.name").startsWith("Linux")) { //$NON-NLS-1$ //$NON-NLS-2$
          try {
            IApplicationAssistant ass = new ApplicationAssistant(params.codePath + "/../lib/nativeview"); //$NON-NLS-1$
            ILazyApplicationInfo[] ila = ass.getLocalApplications();
            if (ila.length != 0) {
              LOGGER.info("Detected LibreOffice {} {}", ila[0].getMajorVersion(), ila[0].getHome()); //$NON-NLS-1$
              if (ila[0].getMajorVersion() != 4 && ila[0].getMajorVersion() != 5) {
                LOGGER.info("Install LibreOffice 4/5 to enable the Libreoffice viewer!"); //$NON-NLS-1$
              } else {
                systemLO = ila[0].getHome();
              }
            }

          } catch (OfficeApplicationException e1) {
            e1.printStackTrace();
          }
        }

        if (systemLO != null || (System.getProperty("os.name").startsWith("Windows") && (new File(pathLO).exists() || new File(compressedLO).exists()))) { //$NON-NLS-1$ //$NON-NLS-2$
   
         try {
            SwingUtilities.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                result = App.triageGui ? JOptionPane.YES_OPTION :
                    JOptionPane.showConfirmDialog(params.mainFrame, useLOMsg, "", JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
              }
            });
          } catch (Exception e) {
            e.printStackTrace();
          }
         
        if (result == JOptionPane.YES_OPTION) {
            if (systemLO == null) {
              LOExtractor extractor = new LOExtractor(new File(compressedLO), new File(pathLO));
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
                officeViewer = new LibreOfficeViewer(params.codePath + "/../lib/nativeview", pathLO); //$NON-NLS-1$
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
  public void restartLibreOfficeFrame() {
      if (officeViewer != null) {
          Thread process = new Thread() {
            @Override
            public void run() {
              officeViewer.constructLOFrame();
              officeViewer.loadFile(new FileContentSource(officeViewer.lastFile));
            }
          };
          process.start();
      }
  }

  @Override
  public void addViewer(Viewer viewer) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates. //$NON-NLS-1$
  }

  @Override
  public void initViewers() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates. //$NON-NLS-1$
  }

  @Override
  public void loadFile(StreamSource file, StreamSource viewFile, String contentType, Set<String> highlightTerms) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates. //$NON-NLS-1$
  }

  @Override
  public void loadFile(StreamSource file, String contentType, Set<String> highlightTerms) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates. //$NON-NLS-1$
  }

}
