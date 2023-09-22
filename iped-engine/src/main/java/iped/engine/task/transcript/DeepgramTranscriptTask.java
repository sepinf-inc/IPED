package iped.engine.task.transcript;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.exception.IPEDException;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DeepgramTranscriptTask extends AbstractTranscriptTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepgramTranscriptTask.class);

    private static final String API_KEY = "DeepgramApiKey";

    private static String apiKey;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!transcriptConfig.isEnabled()) {
            return;
        }

        //Try to recover Deepgram API key from command line params
        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        apiKey = args.getExtraParams().get(API_KEY);

        if (apiKey == null)
        {
            //Check for API key in transcript config
            apiKey = transcriptConfig.getDeepgramKey();

            if(apiKey == null) {
                throw new IPEDException(
                    "[DeepgramAudioTranscript] You must set your Deepgram API key on conf/AudioTranscriptConfig.txt or pass -X" + API_KEY + "=XXX param to enable audio transcription.");
            }
        }
    }

    @Override
    public void finish() throws Exception {
        super.finish();
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) {

        int tries = 0;
        TextAndScore textAndScore = null;

        // Create a URL object with the updated endpoint
        String requestUrl = getRequestUrl();

        while (++tries <= 3) {

            // Create a URL object with the updated endpoint
            try {
                HttpURLConnection con = getConnection(requestUrl);

                // Set request timeout based on file length
                con.setReadTimeout((MIN_TIMEOUT + (int) (transcriptConfig.getTimeoutPerSec() * tmpFile.length() / WAV_BYTES_PER_SEC))*1000);

                // Set the request content type to audio/wav or any other appropriate audio format
                try (OutputStream out = con.getOutputStream()) {
                    // read the file as bytes and write it to the output stream
                    byte[] audioData = Files.readAllBytes(tmpFile.toPath());
                    out.write(audioData);
                }

                // Get the response code
                int responseCode = con.getResponseCode();

                // Read the response
                InputStream responseStream = responseCode >= 400 ? con.getErrorStream() : con.getInputStream();
                ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseStream.read(buffer)) != -1) {
                    responseBytes.write(buffer, 0, bytesRead);
                }
                responseStream.close();

                //Check for success response code
                if(responseCode == 200)
                {
	                // Convert the response to a JSONObject
	                JsonParser parser = new JsonFactory().createParser(responseBytes.toString(StandardCharsets.UTF_8));
	                parser.setCodec(new ObjectMapper());
	                TreeNode tree = parser.readValueAsTree();
	                System.out.println(tree.at("/results/channels/alternatives/transcript"));
	
	                textAndScore = new TextAndScore();
	                textAndScore.text = tree.at("/results/channels/0/alternatives/0/transcript").toString();
	                
	                if(!tree.at("/results/channels/0/alternatives/0/confidence").toString().isEmpty())
	                	textAndScore.score = Double.parseDouble(tree.at("/results/channels/0/alternatives/0/confidence").toString());
                }
                else
                {
                	LOGGER.info("Audio not transcripted: " + responseBytes.toString());
                }

            }
            catch (IOException err)
            {
                LOGGER.warn("Error on audio transcription: " + err);
            }
        }

        return textAndScore;

    }

    private String getRequestUrl() {

        //Build request url using TranscriptConfig params
        String requestUrl = "https://api.deepgram.com/v1/listen?model=enhanced" +
            "&punctuate=" + transcriptConfig.getDeepgramPunctuate() +
            "&diarize=" + transcriptConfig.getDeepgramDiarize() +
            "&smart_format=" + transcriptConfig.getDeepgramSmartFormat();

        //Check for option to autodetect language
        if(transcriptConfig.isDeepgramDetectLanguage())
        {
            requestUrl += "&detect_language=true";
        }
        else
        {
            requestUrl += "&language=" + transcriptConfig.getLanguages().get(0);
        }
        return requestUrl;
    }

    private static HttpURLConnection getConnection(String requestUrl) throws IOException {
        URL Requrl = new URL(requestUrl);

        // Create a new HTTP connection
        HttpURLConnection con = (HttpURLConnection) Requrl.openConnection();

        // Set the request method to POST
        con.setRequestMethod("POST");

        // Set request headers
        con.setRequestProperty("accept", "application/json");
        con.setRequestProperty("Authorization", "Token " + apiKey);

        // Enable output and input
        con.setDoOutput(true);
        con.setDoInput(true);
        return con;
    }

}
