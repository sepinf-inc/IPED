package iped.viewers.api;

import java.awt.FileDialog;

/*
 *  Provides and creates some GUI resources to be used by the viewers. *  
 */

public interface GUIProvider {

    FileDialog createFileDialog(String title, int mode);

    IColumnsManager getColumnsManager();

}
