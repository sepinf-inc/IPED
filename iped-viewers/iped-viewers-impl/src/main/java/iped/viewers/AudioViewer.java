package iped.viewers;

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