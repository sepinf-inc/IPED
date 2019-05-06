package iped3.desktop;

import java.awt.Dialog;
import java.awt.FileDialog;

/*
 *  Provides and creates some GUI resources to be used by the viewers. *  
 */

public interface GUIProvider {
	
	FileDialog createFileDialog(String title, int mode);
	ProgressDialog createProgressDialog(CancelableWorker task, boolean indeterminate, long millisToPopup, Dialog.ModalityType modal);
	ColumnsManager getColumnsManager();

}
