package iped.engine.search;

import java.util.List;

/** Identifies incompatible face embedding spaces, including cases predating model tags. */
public final class FaceRecognitionModels {

    public static final String DLIB = "dlib";
    public static final int LEGACY_DIMENSIONS = 128;
    public static final int MODERN_DIMENSIONS = 512;
    public static final List<String> MODERN_MODELS = List.of("auraface", "buffalo_l", "buffalo_s");

    private FaceRecognitionModels() {
    }

    public static String resolve(String model, int dimensions) {
        if ((model == null || DLIB.equals(model)) && dimensions == LEGACY_DIMENSIONS) {
            return DLIB;
        }
        if (model != null && MODERN_MODELS.contains(model) && dimensions == MODERN_DIMENSIONS) {
            return model;
        }
        return null;
    }

    public static String vectorField(String model, int dimensions) {
        String resolved = resolve(model, dimensions);
        if (resolved == null) {
            return null;
        }
        return "face_encodings_" + resolved + "_" + dimensions;
    }

    public static String elasticVectorField(String model, int dimensions) {
        String resolved = resolve(model, dimensions);
        if (resolved == null) {
            return null;
        }
        return DLIB.equals(resolved) ? "face_encoding" : "face_encoding_" + resolved;
    }
}
