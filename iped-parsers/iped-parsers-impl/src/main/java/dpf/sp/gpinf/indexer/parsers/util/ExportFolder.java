package dpf.sp.gpinf.indexer.parsers.util;

public class ExportFolder {
	
	public static String getExportPath() {
		return System.getProperty("iped.exportFolder", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static void setExportPath(String exportPath) {
		System.setProperty("iped.exportFolder", exportPath); //$NON-NLS-1$
	}

}
