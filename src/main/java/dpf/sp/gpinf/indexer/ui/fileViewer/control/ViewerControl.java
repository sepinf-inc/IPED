package dpf.sp.gpinf.indexer.ui.fileViewer.control;

import dpf.sp.gpinf.indexer.IFileProcessor;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.util.FileContentSource;
import dpf.sp.gpinf.indexer.util.JarLoader;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import iped3.io.StreamSource;

import dpf.sp.gpinf.indexer.ui.fileViewer.frames.EmailViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.IcePDFViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ImageViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer.NotSupported32BitPlatformExcepion;
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

        LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(params.codePath).getParentFile());
        final String pathLO = loFinder.getLOPath();
        
        if (pathLO != null) {
          try {
            SwingUtilities.invokeAndWait(new Runnable() {
              @Override
              public void run() {
                officeViewer = new LibreOfficeViewer(params.codePath + "/../lib/nativeview", pathLO); //$NON-NLS-1$
                viewersRepository.addViewer(officeViewer);
              }
            });
            officeViewer.init();
          
          } catch (NotSupported32BitPlatformExcepion e) {
              JOptionPane.showMessageDialog(null, Messages.getString("ViewerControl.OfficeViewerUnSupported")); //$NON-NLS-1$
              viewersRepository.removeViewer(officeViewer);
              
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        
        exibirAjuda.execute();

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
