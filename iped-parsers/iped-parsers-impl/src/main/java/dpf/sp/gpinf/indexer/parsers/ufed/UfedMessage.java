package dpf.sp.gpinf.indexer.parsers.ufed;

import dpf.mg.udi.gpinf.whatsappextractor.Message;

public class UfedMessage extends Message{
    
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
    
}
