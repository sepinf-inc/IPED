package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig.Builder;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

public class GoogleTranscriptTask extends AbstractAudioTranscriptTask {
    
    private static Logger LOGGER = LoggerFactory.getLogger(GoogleTranscriptTask.class);

    //must be set in environment variable
	private static final String CREDENTIAL_KEY = "GOOGLE_APPLICATION_CREDENTIALS";
	
	private static final MediaType mp3 = MediaType.audio("mpeg");
	private static final MediaType ogg = MediaType.audio("vorbis");
	private static final MediaType flac = MediaType.audio("x-flac");
	private static final MediaType oggflac = MediaType.audio("x-oggflac");
	private static final MediaType oggopus = MediaType.audio("opus");
	private static final MediaType amr = MediaType.audio("amr");
	private static final MediaType aac = MediaType.audio("x-aac");
	private static final MediaType speex = MediaType.audio("speex");
	
	private SpeechClient speechClient;

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
	    
	    super.init(confParams, confDir);
	    
		speechClient = SpeechClient.create();
	}

	@Override
	public void finish() throws Exception {
	    super.finish();
		speechClient.close();
	}

	@Override
	protected TextAndScore transcribeWav(File tmpFile) throws Exception {

	    TextAndScore textAndScore = null;
		try {
			// The language of the supplied audio
			String languageCode = languages.get(0);
			
			List<String> alternativeLangs = languages.subList(1, languages.size());

			Builder builder = RecognitionConfig.newBuilder()
			        .setLanguageCode(languageCode)
			        .addAllAlternativeLanguageCodes(alternativeLangs);
			
			/*
			// Sample rate in Hertz of the audio data sent
			int sampleRateHertz = 48000;

			// Encoding of audio data sent. This sample sets this explicitly.
			// This field is optional for FLAC and WAV audio formats.
			RecognitionConfig.AudioEncoding encoding = RecognitionConfig.AudioEncoding.MP3;
			
			boolean wavOrflac = false;
			if(evidence.getMediaType().equals(oggopus)) {
				//does not work...
				//encoding = RecognitionConfig.AudioEncoding.OGG_OPUS;
				
			}else if(evidence.getMediaType().equals(flac)) {
				encoding = RecognitionConfig.AudioEncoding.FLAC;
				wavOrflac = true;
			}else if(evidence.getMediaType().equals(wav)) {
				encoding = RecognitionConfig.AudioEncoding.LINEAR16;
				wavOrflac = true;
			}
			
			if(!wavOrflac) {
				builder.setEncoding(encoding);
				builder.setSampleRateHertz(sampleRateHertz);
			}
			*/
			
			RecognitionConfig config = builder.build();

			ByteString content;
			try (InputStream is = Files.newInputStream(tmpFile.toPath())) {
				content = ByteString.readFrom(is);
			}
			RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(content).build();

			LongRunningRecognizeRequest request =
		            LongRunningRecognizeRequest.newBuilder().setConfig(config).setAudio(audio).build();

			OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future =
		            speechClient.longRunningRecognizeAsync(request);
			
			LongRunningRecognizeResponse response = future.get();

			StringBuilder text = new StringBuilder();
			float confidence = 0;
			int i = 0;
			for (SpeechRecognitionResult result : response.getResultsList()) {
				// First alternative is the most probable result
				SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
				confidence += alternative.getConfidence();
				i++;
				text.append(alternative.getTranscript()).append(" ");
			}
			if(i == 0) i = 1;
			
			textAndScore = new TextAndScore();
			textAndScore.text = text.toString();
			textAndScore.score = confidence / i;
			
			LOGGER.debug("GG Transcript of {} : {}", evidence.getPath(), text.toString());

		} catch (Exception e) {
		    LOGGER.error("Failed to transcript {} : {}", evidence.getPath(), e);
		    LOGGER.warn("", e);
		}
		
		return textAndScore;

	}

}
