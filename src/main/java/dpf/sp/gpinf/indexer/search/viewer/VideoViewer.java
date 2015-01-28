/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
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
package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.GridLayout;
import java.io.File;
import java.util.Set;

import javax.print.attribute.standard.Media;

public class VideoViewer extends AbstractViewer {

	StackPane root;
	MediaView mediaView;
	MediaPlayer mediaPlayer;

	public VideoViewer() {
		super(new GridLayout());
	}

	@Override
	public String getName() {
		return "Video";
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.startsWith("video") || contentType.startsWith("audio");
	}

	@Override
	public void init() {
		final JFXPanel jfxPanel = new JFXPanel();

		PlatformImpl.startup(new Runnable() {
			@Override
			public void run() {

				root = new StackPane();
				Scene scene = new Scene(root);
				jfxPanel.setScene(scene);
			}
		});

		this.getPanel().add(jfxPanel);

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void loadFile(final File file, Set<String> highlightTerms) {

		PlatformImpl.runLater(new Runnable() {
			@Override
			public void run() {

				if (mediaPlayer != null) {
					mediaPlayer.stop();
					root.getChildren().remove(mediaView);
				}

				if (file != null) {
					Media media = new Media(file.toURI().toString());
					mediaPlayer = new MediaPlayer(media);
					mediaPlayer.setAutoPlay(true);

					mediaView = new MediaView(mediaPlayer);
					root.getChildren().add(mediaView);
				}

			}
		});

	}

	@Override
	public void scrollToNextHit(boolean forward) {
		// TODO Auto-generated method stub

	}

}
