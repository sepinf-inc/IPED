package iped.parsers.ufed.model;
/**
 * Represents any other model types not explicitly defined
 * (e.g., ContactPhoto, MessageLabel).
 */
public class GenericModel extends BaseModel {

    private static final long serialVersionUID = 3266298225119371138L;

    public GenericModel(String modelType) {
        super(modelType);
    }
}