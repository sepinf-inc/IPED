package dpf.sp.gpinf.indexer.process.task;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig.Builder;
import com.google.cloud.speech.v1p1beta1.RecognizeRequest;
import com.google.cloud.speech.v1p1beta1.RecognizeResponse;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.tika.mime.MediaType;

import iped3.IItem;

public class GoogleTranscriptTask extends AbstractTask {

	private static final String CREDENTIAL_KEY = "GOOGLE_APPLICATION_CREDENTIALS";
	
	private static final MediaType mp3 = MediaType.audio("mpeg");
	private static final MediaType ogg = MediaType.audio("vorbis");
	private static final MediaType flac = MediaType.audio("x-flac");
	private static final MediaType oggflac = MediaType.audio("x-oggflac");
	private static final MediaType oggopus = MediaType.audio("opus");
	private static final MediaType amr = MediaType.audio("amr");
	private static final MediaType aac = MediaType.audio("x-aac");
	private static final MediaType speex = MediaType.audio("speex");
	private static final MediaType wav = MediaType.audio("vnd.wave");
	
	private SpeechClient speechClient;

	@Override
	public void init(Properties confParams, File confDir) throws Exception {
		speechClient = SpeechClient.create();
	}

	@Override
	public void finish() throws Exception {
		speechClient.close();
	}

	@Override
	protected void process(IItem evidence) throws Exception {

		if (!evidence.getMediaType().getType().equals("audio")) {
			//return;
		}
		if(evidence.isDir()) {
			return;
		}

		try {
			// The language of the supplied audio
			String languageCode = "pt-BR";
			
			List<String> alternativeLangs = Arrays.asList("en-US");

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

			Builder builder = RecognitionConfig.newBuilder().setLanguageCode(languageCode).addAllAlternativeLanguageCodes(alternativeLangs);
			if(!wavOrflac) {
				builder.setEncoding(encoding);
				builder.setSampleRateHertz(sampleRateHertz);
			}
			RecognitionConfig config = builder.build();

			ByteString content;
			try (InputStream is = evidence.getBufferedStream()) {
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
			evidence.setParsedTextCache(text.toString());
			evidence.setExtraAttribute("Google_transcript", text.toString());
			evidence.setExtraAttribute("transcriptConfidence", confidence / i);
			
			System.out.println("GG Transcript of " + evidence.getPath() + ": " + text.toString());

		} catch (Exception e) {
			System.err.println("Failed to transcript " + evidence.getPath() + " cause: " + e);
			e.printStackTrace();
		}

	}

}
