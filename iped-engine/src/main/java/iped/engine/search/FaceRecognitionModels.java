package iped.engine.search;

import java.util.List;

import iped.properties.ExtraProperties;

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
        return DLIB.equals(resolved) ? ExtraProperties.FACE_ENCODINGS
                : ExtraProperties.FACE_ENCODINGS + "_" + resolved + "_" + dimensions;
    }

    public static String storageField(String model, int dimensions) {
        String field = vectorField(model, dimensions);
        return field != null ? field : ExtraProperties.FACE_ENCODINGS + "_unknown_" + dimensions;
    }

    public static boolean isStorageField(String field) {
        if (ExtraProperties.FACE_ENCODINGS.equals(field)) {
            return true;
        }
        if (!field.startsWith(ExtraProperties.FACE_ENCODINGS + "_")) {
            return false;
        }
        for (String model : MODERN_MODELS) {
            if (storageField(model, MODERN_DIMENSIONS).equals(field)) {
                return true;
            }
        }
        return field.startsWith("face_encodings_unknown_") && field.matches("face_encodings_unknown_[0-9]+");
    }

    public static String elasticVectorField(String model, int dimensions) {
        String resolved = resolve(model, dimensions);
        if (resolved == null) {
            return null;
        }
        return DLIB.equals(resolved) ? "face_encoding" : "face_encoding_" + resolved;
    }
}
