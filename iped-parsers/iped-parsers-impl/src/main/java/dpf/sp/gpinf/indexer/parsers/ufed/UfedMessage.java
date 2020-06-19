package dpf.sp.gpinf.indexer.parsers.ufed;

import dpf.mg.udi.gpinf.whatsappextractor.Message;

public class UfedMessage extends Message{
    
    public static final String SYSTEM_MESSAGE = "System Message"; //$NON-NLS-1$
    
    private String transcription;
    private String transcriptConfidence;
    
    public String getTranscription() {
        return transcription;
    }
    
    public void setTranscription(String transcription) {
        this.transcription = transcription;
    }
    
    public String getTranscriptConfidence() {
        return transcriptConfidence;
    }
    
    public void setTranscriptConfidence(String transcriptConfidence) {
        this.transcriptConfidence = transcriptConfidence;
    }
    
    @Override
    public boolean isSystemMessage() {
        return SYSTEM_MESSAGE.equals(this.getRemoteResource());
    }
    
}
