package dpf.sp.gpinf.indexer.config;

import java.util.ArrayList;

public class HashTaskConfig extends EnableTaskProperty {

    public static final String HASH_PROP = "hash";

    private ArrayList<String> algorithms;

    public HashTaskConfig() {
        super(HASH_PROP);
    }

    @Override
    public boolean isEnabled() {
        return !getAlgorithms().isEmpty();
    }

    public ArrayList<String> getAlgorithms() {
        if (algorithms == null) {
            algorithms = new ArrayList<>();
            if (super.getValue() != null && !super.getValue().isEmpty()) {
                for (String algorithm : super.getValue().split(";")) {
                    algorithms.add(algorithm.trim());
                }
            }
        }
        return algorithms;
    }

}
