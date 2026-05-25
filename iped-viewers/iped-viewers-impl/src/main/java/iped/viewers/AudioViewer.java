package iped.viewers;

<<<<<<< audio-viewer
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.io.IStreamSource;
import iped.io.URLUtil;
import iped.viewers.api.AbstractViewer;
import uk.co.caprica.vlcj.player.base.MediaApi;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

public class AudioViewer extends AbstractViewer {

	@SuppressWarnings("unused")
	private static Logger LOGGER = LoggerFactory.getLogger(AudioViewer.class);
	private MediaApi player;
	private EmbeddedMediaPlayer mediaPlayerComponent;
	private JTextArea audioDescription = new JTextArea();
	private Button play = new Button(">");
	private JButton playButton;
	private JButton pauseButton;
	private JButton rewindButton;
	private volatile File temp;
    
	public AudioViewer()  {
		super(new GridLayout());
        if (System.getProperty("os.name").startsWith("Windows")) {
			try {
			URI jarUri = URLUtil.getURL(AudioViewer.class).toURI();
            File nativelib = new File(jarUri).getParentFile();

			System.setProperty("VLC_PLUGIN_PATH",
                new File(nativelib, "\\vlc").getAbsolutePath());
			
			File vlc = new File(System.getProperty("VLC_PLUGIN_PATH"), "\\win32-x86-64");
			//criarVlcjConfig(vlc.getAbsolutePath());
			System.setProperty("jna.library.path", vlc.getAbsolutePath());
			
			} catch (Exception e) {
				LOGGER.error("Error loading VLC native libraries", e);
			}
        }


	}

	@Override
	public String getName() {
		return "Audio"; //$NON-NLS-1$
	}

	@Override
	public boolean isSupportedType(String contentType) {

		return contentType.equals("audio/basic") || contentType.equals("audio/basic") ||
		// contentType.equals("auido/L24")||
		// contentType.equals("audio/mid")||
		// contentType.equals("audio/mid")||
				contentType.equals("audio/mpeg") || contentType.equals("audio/mp4")
				|| contentType.equals("audio/x-aiff") || contentType.equals("audio/x-aiff")
				|| contentType.equals("audio/x-aiff") || contentType.equals("audio/x-mpegurl")
				|| contentType.equals("audio/vnd.rn-realaudio") || contentType.equals("audio/vnd.rn-realaudio")
				|| contentType.equals("audio/ogg") || contentType.equals("audio/vorbis")
				|| contentType.equals("audio/vnd.wave");

	}

	@Override
	public void loadFile(IStreamSource content, Set<String> highlightTerms) {
		if (temp != null) {
			temp.delete();
		}
		if (player != null) {
			player = null;
		}
		play.setLabel(">");
		play.requestFocus();
		audioDescription.setText("");

		// create temp file here to not block EDT
		if (content != null) {
			try (InputStream in = content.getSeekableInputStream()) {
				// put file in temp
				temp = File.createTempFile("IPED", ".audio", null);
				temp.deleteOnExit();
				Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);

				boolean ok = mediaPlayerComponent.media().prepare(temp.getAbsolutePath());
				if (ok) player = mediaPlayerComponent.media();
				else
					LOGGER.info("Error open file");
				iped.data.IItem item = (iped.data.IItem) content;

				for (String mdName : item.getMetadata().names()) {
					if (mdName.contains("audio")
							&& (mdName.contains("dura") || mdName.contains("transc") || mdName.contains("name"))) {
						String mdValue = item.getMetadata().get(mdName);
						audioDescription.append(mdName + ":" + mdValue + "\n\n");
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
				temp = null;
			}
		}
	}
	@SuppressWarnings("unchecked")
	public static void setEnv(String key, String value) throws Exception {
		Map<String, String> env = System.getenv();
		Class<?> cl = env.getClass();
		Field field = cl.getDeclaredField("m");
		field.setAccessible(true);
		Map<String, String> writableEnv = (Map<String, String>) field.get(env);
		writableEnv.put(key, value);
	}

	@Override
	public void init() {
		

		EmbeddedMediaPlayerComponent playerCompo = new EmbeddedMediaPlayerComponent();
		mediaPlayerComponent = playerCompo.mediaPlayer();

		this.getPanel().add(playerCompo);
		this.getPanel().add(play);
		this.getPanel().setLayout(new BorderLayout());
		this.getPanel().add(playerCompo, BorderLayout.CENTER);
		JPanel controlsPane = new JPanel();
        
        Action keyPAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mediaPlayerComponent.controls().play();
            }
        };
        controlsPane.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "play");
        controlsPane.getActionMap().put("play", keyPAction);

		playButton = new JButton("Play");
		controlsPane.add(playButton);
		pauseButton = new JButton("Pause");
		controlsPane.add(pauseButton);
		rewindButton = new JButton("Rewind");
		controlsPane.add(rewindButton);
		JLabel spaceHintLabel = new JLabel("Push spacebar to play");
		controlsPane.add(spaceHintLabel);

		this.getPanel().add(controlsPane, BorderLayout.SOUTH);

		playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediaPlayerComponent.controls().play();
			}
		});
		pauseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediaPlayerComponent.controls().pause();
			}
		});
		rewindButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediaPlayerComponent.controls().skipTime(-14000);
			}
		});
		audioDescription = new JTextArea(3, 1);
		audioDescription.setLineWrap(true);
		audioDescription.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(audioDescription, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		audioDescription.setEditable(false);
		this.getPanel().add(scrollPane);
	}

	@Override
	public void copyScreen(Component comp) {
	}

	@Override
	public void dispose() {
	
		  if (mediaPlayerComponent != null) { 
			  mediaPlayerComponent.controls().stop();
		  }
		 
	}

	@Override
	public void scrollToNextHit(boolean forward) {
	}


}
=======
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.UIManager;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.IconUtil;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;
import iped.viewers.api.AbstractViewer;
import iped.viewers.api.AttachmentSearcher;
import iped.viewers.localization.Messages;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;

public class AudioViewer extends AbstractViewer {
    private static final String transcriptionAttr = ExtraProperties.TRANSCRIPT_ATTR;
    private static final String transcriptionConfidenceAttr = ExtraProperties.CONFIDENCE_ATTR;;
    private static final String audioDurationAttr = ExtraProperties.AUDIO_META_PREFIX + "xmpDM:duration";

    private JFXPanel jfxPanel;
    private HtmlLinkViewer htmlViewer;
    private String playImgBase64 = "";

    public AudioViewer(AttachmentSearcher attachmentSearcher) {
        super(new GridLayout());
        jfxPanel = new JFXPanel();
        htmlViewer = new HtmlLinkViewer(attachmentSearcher);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                StackPane root = new StackPane();
                root.getChildren().add(htmlViewer.htmlViewer);
                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);
            }
        });

        this.getPanel().add(jfxPanel);
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.startsWith("audio/");
    }

    @Override
    public String getName() {
        return "Audio";
    }

    @Override
    public void init() {
        htmlViewer.init();
        try {
            BufferedImage img = ImageIO.read(IconUtil.class.getResource(resPath + "play.png"));
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            ImageIO.write(img, "png", out);
            playImgBase64 = Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
     }

    @Override
    public void dispose() {
        htmlViewer.dispose();
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        loadFile(content, null, terms);
    }

    @Override
    public void loadFile(final IStreamSource content, String contentType, final Set<String> terms) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                WebEngine webEngine = htmlViewer.webEngine;
                webEngine.loadContent(UiUtil.getUIEmptyHtml());

                if (content instanceof IItemReader) {
                    htmlViewer.highlightTerms = terms;
                    String preview = generatePreview((IItemReader) content);
                    try {
                        if (htmlViewer.tmpFile == null) {
                            htmlViewer.tmpFile = File.createTempFile("transcription", ".html");
                            htmlViewer.tmpFile.deleteOnExit();
                        }
                        Files.write(htmlViewer.tmpFile.toPath(), preview.getBytes("UTF-8"),
                                StandardOpenOption.TRUNCATE_EXISTING);
                        webEngine.load(htmlViewer.tmpFile.toURI().toURL().toString());

                    } catch (IOException e) {
                        webEngine.loadContent(preview);
                    }
                }
            }
        });
    }

    private String generatePreview(IItemReader item) {
        String transcription = item.getMetadata().get(transcriptionAttr);

        String strConfidence = null;
        if (transcription != null) {
            String conf = item.getMetadata().get(transcriptionConfidenceAttr);
            if (conf != null && !conf.isBlank()) {
                try {
                    double c = Double.parseDouble(conf);
                    strConfidence = String.format("%.0f%%", c * 100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        String strDuration = null;
        String duration = item.getMetadata().get(audioDurationAttr);
        if (duration != null && !duration.isBlank()) {
            try {
                double d = Double.parseDouble(duration);
                int seconds = (int) d;
                if (seconds > 0) {
                    int h = seconds / 3600;
                    int m = seconds % 3600 / 60;
                    int s = seconds % 60;
                    if (h > 0) {
                        strDuration = String.format("%dh %dm %ds", h, m, s);
                    } else if (m > 0) {
                        strDuration = String.format("%dm %ds", m, s);
                    } else {
                        strDuration = String.format("%ds", s);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String type = item.getType();

        Color color2 = new Color(0xD0D0D0);
        Color color3 = new Color(0xF4F4F4);
        Color background = UIManager.getColor("Viewer.background");
        if (background != null) {
            color3 = UiUtil.mix(background, Color.gray, 0.9);
            color2 = UiUtil.mix(background, Color.gray, 0.7);
        }
        String borderColor = "black";
        Color foreground = UIManager.getColor("Viewer.foreground");
        if (foreground != null && background != null)
            borderColor = UiUtil.getHexRGB(UiUtil.mix(background, foreground, 0.5));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n");
        sb.append(
                "<style>table {border-collapse: collapse; font-size:10pt; font-family: arial, verdana, sans-serif; width:100%; align:center; } ");
        sb.append("table.t {margin-bottom:20px;} td { padding: 2px; } ");
        sb.append("td.s1 {background-color:").append(UiUtil.getHexRGB(color2)).append("; ");
        sb.append("border: 1px solid ").append(borderColor).append("; text-align:left;} ");
        sb.append("td.s2 {background-color:").append(UiUtil.getHexRGB(color3)).append("; ");
        sb.append("border: 1px solid ").append(borderColor).append("; text-align:left;}\n");
        sb.append("textarea {readonly: readonly; height: 60px; width: 100%; resize: none;}\n");
        sb.append("*:focus {outline: none;}\n");
        sb.append("a.play {\n");
        sb.append("  display: inline-block;\n");
        sb.append("  vertical-align: top;\n");
        sb.append("  cursor: pointer;\n");
        sb.append("  width: 26px;\n");
        sb.append("  height: 26px;\n");
        sb.append("  margin: 3px;\n");
        sb.append("  background-image: url(\"data:image/png;base64,").append(playImgBase64).append("\");\n");
        sb.append("  background-position: 0px 0px;\n");
        sb.append("  background-size: 26px 52px;\n");
        sb.append("}\n");
        sb.append("a.play:hover {\n");
        sb.append("  background-position: 0px -26px;\n");
        sb.append("}\n");
        sb.append("</style></head>\n");
        sb.append("<body style=\"");

        if (background != null)
            sb.append("background-color:").append(UiUtil.getHexRGB(background)).append(";");
        if (foreground != null)
            sb.append("color:").append(UiUtil.getHexRGB(foreground)).append(";");
        sb.append("\">\n");

        sb.append("<table class=\"t\">");
        sb.append("<tr>");

        sb.append("<td class=\"s1\" width=\"26px\">");
        sb.append("<a class=\"play\" onclick=\"app.open('");
        if (item.getHash() != null) {
            sb.append(BasicProps.HASH).append(':').append(item.getHash());
        }
        sb.append("')\"/>");
        sb.append("</td>");

        sb.append("<td class=\"s1\">");
        sb.append("<b>").append(SimpleHTMLEncoder.htmlEncode(Messages.getString("AudioViewer.TranscriptionTitle"))).append("</b>");
        if (strConfidence != null) {
            sb.append(" [").append(strConfidence).append("]");
        }
        if (strDuration != null || type != null) {
            sb.append("<br>");
            if (strDuration != null) {
                sb.append(strDuration).append(' ');
            }
            if (type != null) {
                sb.append('(').append(type).append(')');
            }
        }
        sb.append("</td></tr>");

        sb.append("<tr>");
        sb.append("<td colspan=\"2\" class=\"s2\">");
        if (transcription == null) {
            sb.append("<font color=\"gray\"> [");
            sb.append(SimpleHTMLEncoder.htmlEncode(Messages.getString("AudioViewer.NoTranscription")));
            sb.append("] </font>");
        } else {
            sb.append(SimpleHTMLEncoder.htmlEncode(transcription));
        }
        sb.append("</td></tr>");

        sb.append("</table>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        htmlViewer.scrollToNextHit(forward);
    }
}
>>>>>>> master
