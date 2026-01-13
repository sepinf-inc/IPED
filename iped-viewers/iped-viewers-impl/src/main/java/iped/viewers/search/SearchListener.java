package iped.viewers.search;
import java.util.EventListener;

public interface SearchListener extends EventListener {
    void stateChanged(SearchEvent e);
}
