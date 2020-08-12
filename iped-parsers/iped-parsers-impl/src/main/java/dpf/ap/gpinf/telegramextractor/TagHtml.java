package dpf.ap.gpinf.telegramextractor;

import java.util.ArrayList;
import java.util.HashMap;

public class TagHtml {
    private String tagname;
    private ArrayList<Object> inner;

    private HashMap<String, String> atributes;

    public TagHtml(String tagname) {
        this.tagname = tagname;
        inner = new ArrayList<>();
        atributes = new HashMap<>();
    }

    public void setAtribute(String atribute, String value) {
        atributes.put(atribute, value);
    }

    public String getAtribute(String atribute) {
        return atributes.get(atribute);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(tagname);

        for (String atr : atributes.keySet()) {
            sb.append(" " + atr);
            if (atributes.get(atr) != null) {
                sb.append("=");
                sb.append("'" + atributes.get(atr) + "'");
            }
        }
        sb.append(">");
        for (Object subelm : inner) {
            sb.append(subelm.toString());
        }
        sb.append("</");
        sb.append(tagname);
        sb.append(">");
        return sb.toString();

    }

    public ArrayList<Object> getInner() {
        return inner;
    }

    public void setInner(ArrayList<Object> inner) {
        this.inner = inner;
    }

}
