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

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import ag.ion.bion.officelayer.application.IApplicationAssistant;
import ag.ion.bion.officelayer.application.ILazyApplicationInfo;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.internal.application.ApplicationAssistant;
import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.FileProcessor;
import dpf.sp.gpinf.indexer.util.LOExtractor;

public class CompositeViewerHelper {

	boolean useLO = false;
	String pathLO = System.getProperty("user.home") + "/.indexador/libreoffice4";
	String compressedLO = App.get().codePath + "/../tools/libreoffice.zip";
	String useLOMsg = "Deseja ativar o visualizador de formatos Office?";
	String systemLO = null;
	public static LibreOfficeViewer officeViewer;
	volatile int result = JOptionPane.NO_OPTION;

	private boolean loadJavaFX() {
		boolean javaFX = false;
		String javaVersion = System.getProperty("java.version");
		if (javaVersion.compareTo("1.7") > 0) {
			String minor = javaVersion.substring(javaVersion.indexOf("_") + 1);
			if (!javaVersion.startsWith("1.7") || Integer.valueOf(minor) >= 6) {
				String fxJar = "jfxrt.jar";
				String javaLib = System.getProperty("java.home") + File.separator + "lib";
				if (new File(javaLib + File.separator + "ext" + File.separator + fxJar).exists())
					javaFX = true;
				else
					javaFX = loadJar(new File(javaLib + File.separator + fxJar));
			}
		}
		return javaFX;
	}

	private boolean loadJar(File file) {
		if (!file.exists())
			return false;
		try {
			URL jarUrl = file.toURI().toURL();
			ClassLoader sysloader = ClassLoader.getSystemClassLoader();
			Class<?> sysclass = URLClassLoader.class;
			Class<?>[] parameters = new Class[] { URL.class };
			Method method = sysclass.getDeclaredMethod("addURL", parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { jarUrl });
			System.out.println(jarUrl.toString() + " loaded");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	private boolean loadJarDir(File dir) {
		String[] jarNameList = dir.list();
		if (jarNameList != null)
			for (int i = 0; i < jarNameList.length; i++) {
				File jar = new File(dir, jarNameList[i]);
				if (!loadJar(jar))
					return false;
			}
		return true;
	}

	public void addViewers() {
		new Thread() {
			@Override
			public void run() {

				final boolean javaFX = loadJavaFX();

				try {
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {

							App.get().textViewer = new TextViewer();
							App.get().compositeViewer.addViewer(App.get().textViewer);

							App.get().compositeViewer.addViewer(new ImageViewer());

							if (javaFX) {
								App.get().compositeViewer.addViewer(new HtmlViewer());
								App.get().compositeViewer.addViewer(new EmailViewer());
								App.get().compositeViewer.addViewer(new TikaHtmlViewer());
								//App.get().compositeViewer.addViewer(new VideoViewer());
							} else
								App.get().compositeViewer.addViewer(new NoJavaFXViewer());

							App.get().compositeViewer.addViewer(new IcePDFViewer());
							App.get().compositeViewer.addViewer(new TiffViewer());

						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}

				App.get().compositeViewer.initViewers();

				FileProcessor exibirAjuda = new FileProcessor(-1, false);
				exibirAjuda.execute();

				if (System.getProperty("os.name").startsWith("Linux"))
					try {
						IApplicationAssistant ass = new ApplicationAssistant(App.get().codePath + "/../lib/nativeview");
						ILazyApplicationInfo[] ila = ass.getLocalApplications();
						if (ila.length != 0) {
							systemLO = ila[0].getHome();
							System.out.println("Detected LO " + ila[0].getMajorVersion() + " " + ila[0].getHome());
							loadJarDir(new File(App.get().codePath + "/../lib/libLO" + ila[0].getMajorVersion()));
						}

					} catch (OfficeApplicationException e1) {
						e1.printStackTrace();
					}

				if (systemLO != null || (System.getProperty("os.name").startsWith("Windows") && (new File(pathLO).exists() || new File(compressedLO).exists()))) {

					try {
						SwingUtilities.invokeAndWait(new Runnable() {
							@Override
							public void run() {
								result = JOptionPane.showConfirmDialog(App.get(), useLOMsg, "", JOptionPane.YES_NO_OPTION);
							}
						});
					} catch (Exception e) {
						e.printStackTrace();
					}

					if (result == JOptionPane.YES_OPTION) {
						if (systemLO == null) {
							LOExtractor extractor = new LOExtractor(compressedLO, pathLO);
							useLO = extractor.decompressLO();
							loadJarDir(new File(App.get().codePath + "/libLO4"));
						} else
							useLO = true;
					}

				}

				if (systemLO != null)
					pathLO = systemLO;

				if (useLO) {
					try {
						SwingUtilities.invokeAndWait(new Runnable() {
							@Override
							public void run() {
								officeViewer = new LibreOfficeViewer(App.get().codePath + "/../lib/nativeview", pathLO);
								App.get().compositeViewer.addViewer(officeViewer);
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
						App.get().dialogBar.setVisible(false);
					}
				});

			}
		}.start();
	}

	public static void restartLOAfterLayoutChange() {
		if (officeViewer != null)
			new Thread() {
				@Override
				public void run() {
					officeViewer.restartLO();
					officeViewer.loadFile(officeViewer.lastFile);
				}
			}.start();

	}

	public static void releaseLOFocus() {
		if (officeViewer != null)
			officeViewer.releaseFocus();

	}

}
