package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ag.ion.bion.officelayer.application.IOfficeApplication;
import iped.io.IStreamSource;
import iped.io.URLUtil;
import iped.viewers.api.AbstractViewer;
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil;
import uk.co.caprica.vlcj.player.base.MediaApi;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

public class AudioViewer extends AbstractViewer {

	@SuppressWarnings("unused")
	private static Logger LOGGER = LoggerFactory.getLogger(AudioViewer.class);
	private MediaApi player;
	private EmbeddedMediaPlayer mediaPlayerComponent;
	private JTextArea audioDescription = new JTextArea();
	// private ProgressBar progressBar = new ProgressBar(0d);
	private Button play = new Button(">");
	private JButton playButton;
	private JButton pauseButton;
	private JButton rewindButton;
	private volatile File temp;
    public static void criarVlcjConfig(String pathVlc) {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            System.err.println("Erro: variável de ambiente APPDATA não encontrada.");
            return;
        }

        File vlcjDir = new File(appData, "vlcj");
        if (!vlcjDir.exists()) {
            boolean criado = vlcjDir.mkdirs();
            if (!criado) {
                System.err.println("Erro ao criar diretório: " + vlcjDir.getAbsolutePath());
                return;
            }
        }

        File configFile = new File(vlcjDir, "vlcj.config");
        if (configFile.exists()) {
            System.out.println("Arquivo vlcj.config já existe em: " + configFile.getAbsolutePath());
            return;
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("nativeDirectory=" + pathVlc + System.lineSeparator());
            System.out.println("Arquivo vlcj.config criado com sucesso em: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao criar arquivo vlcj.config: " + e.getMessage());
        }
    }
	public AudioViewer()  {
		super(new GridLayout());
        if (System.getProperty("os.name").startsWith("Windows")) {
			try {
			URI jarUri = URLUtil.getURL(AudioViewer.class).toURI();
            File nativelib = new File(jarUri).getParentFile();

			System.setProperty("VLC_PLUGIN_PATH",
                new File(nativelib, "\\vlc").getAbsolutePath());
			
				//File pluginsvlc = new File(nativelib, "nativelibs/vlc/plugins");
			//setEnv("VLC_PLUGIN_PATH", pluginsvlc.getAbsolutePath());
			File vlc = new File(System.getProperty("VLC_PLUGIN_PATH"), "\\win32-x86-64");
			criarVlcjConfig(vlc.getAbsolutePath());
			System.setProperty("jna.library.path", vlc.getAbsolutePath());
			
			//File libvlc = new File(nativelib, "nativelibs/vlc/libvlc.dll");
			//System.load(libvlc.getAbsolutePath());

			/*System.setProperty("java.library.path", System.getProperty("java.library.path") + ";" + vlc.getAbsolutePath());
			Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
        	fieldSysPath.setAccessible(true);
        	fieldSysPath.set(null, null);*/
			//File libvlc = new File(nativelib, "nativelibs/vlc/libvlc.dll");
            //File libvlccore = new File(nativelib, "nativelibs/vlc/libvlccore.dll");
            //File axvlc = new File(nativelib, "nativelibs/vlc/axvlc.dll");
            //File npvlc = new File(nativelib, "nativelibs/vlc/npvlc.dll");
			//File pluginsvlc = new File(nativelib, "nativelibs/vlc/plugins");
			//System.load(pluginsvlc.getAbsolutePath());
            //System.load(npvlc.getAbsolutePath());
            //System.load(axvlc.getAbsolutePath());
            //System.load(libvlccore.getAbsolutePath());
            //System.load(libvlc.getAbsolutePath());
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
		// progressBar.setProgress(0d);

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
				/*
				 * HeadlessMediaPlayer mediaPlayer =
				 * mediaPlayerFactory.newHeadlessMediaPlayer();
				 * mediaPlayer.playMedia("path/to/your/file.ogg"); mediaPlayer.
				 */

				/*
				 * Media mediaFile = new Media(temp.toURI().toString()); player = new
				 * MediaPlayer(mediaFile); player.stop();
				 * player.currentTimeProperty().addListener((observable, oldValue, newValue) ->
				 * { double progress = newValue.toMillis() /
				 * player.getTotalDuration().toMillis(); progressBar.setProgress(progress); });
				 */
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
		//skipButton = new JButton("Skip");
		//controlsPane.add(skipButton);
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
		/*skipButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediaPlayerComponent.mediaPlayer().controls().skipTime(4000);
			}
		});*/
		audioDescription = new JTextArea(3, 1);
		audioDescription.setLineWrap(true);
		audioDescription.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(audioDescription, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		audioDescription.setEditable(false);
		this.getPanel().add(scrollPane);
		// mediaPlayerFactory = new MediaPlayerFactory();
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