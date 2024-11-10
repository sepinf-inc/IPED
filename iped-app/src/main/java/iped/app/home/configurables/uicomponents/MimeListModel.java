package iped.app.home.configurables.uicomponents;

import java.util.List;
import java.util.function.Predicate;

import iped.app.ui.controls.IPEDFilterableListModel;

public class MimeListModel extends IPEDFilterableListModel<String> {

    public MimeListModel(List<String> list) {
        super(list);
    }

    public MimeListModel(List<String> list, Predicate<String> predicate) {
        super(list, predicate);
    }
}
