package iped.viewers.api;

import java.awt.FileDialog;
import java.util.Set;

/*
 *  Provides and creates some GUI resources to be used by the viewers. *  
 */

public interface GUIProvider {

    FileDialog createFileDialog(String title, int mode);

    IColumnsManager getColumnsManager();

    Set<String> getSelectedBookmarks();

    Set<String> getSelectedCategories();

}
