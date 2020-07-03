package br.gov.pf.labld.graph;

import java.util.ArrayList;
import java.util.List;

public class ExportLinksQuery {

    private List<List<String>> typesLists;
    private List<Integer> distances;

    public ExportLinksQuery(int num) {
        typesLists = new ArrayList<>(num);
        distances = new ArrayList<>(num - 1);
    }

    public void addTypes(List<String> types, int distance) {
        typesLists.add(types);
        distances.add(distance);
    }

    public void addTypes(List<String> types) {
        typesLists.add(types);
    }

    public int getNumOfLinks() {
        return typesLists.size();
    }

    public List<String> getTypes(int num) {
        return typesLists.get(num);
    }

    public int getDistance(int num) {
        return distances.get(num);
    }

}
