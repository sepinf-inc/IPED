import sys
import numpy
stdout = sys.stdout
sys.stdout = sys.stderr

terminate = 'terminate_process'
model_loaded = 'model_loaded'
library_loaded = 'library_loaded'
finished = 'transcription_finished'
ping = 'ping'

def main():

    modelName = sys.argv[1]
    deviceNum = int(sys.argv[2])
    threads = int(sys.argv[3])
    language = sys.argv[4]
    compute_type = sys.argv[5]
    batch_size = int(sys.argv[6])
    
    if language == 'detect':
        language = None

    try:
        import whisperx
        whisperx_found = True
    except:
        import faster_whisper
        whisperx_found = False
    
    print(library_loaded, file=stdout, flush=True)
    print('whisperx' if whisperx_found else 'faster_whisper', file=stdout, flush=True)
    
    import GPUtil
    cudaCount = len(GPUtil.getGPUs())

    print(str(cudaCount), file=stdout, flush=True)

    if cudaCount > 0:
        deviceId = 'cuda'
    else:
        deviceId = 'cpu'
        deviceNum = 0
    
    try:
        if whisperx_found:
            model = whisperx.load_model(modelName, device=deviceId, device_index=deviceNum, threads=threads, compute_type=compute_type, language=language)
        else:
            model = faster_whisper.WhisperModel(modelName, device=deviceId, device_index=deviceNum, cpu_threads=threads, compute_type=compute_type)
            
    except Exception as e:
        if deviceId != 'cpu':
            # loading on GPU failed (OOM?), try on CPU
            print('FAILED to load model on GPU, OOM? Fallbacking to CPU...', file=sys.stderr)
            deviceId = 'cpu'
            if compute_type == 'float16': # not supported on CPU
                compute_type = 'int8'
            if whisperx_found:
                model = whisperx.load_model(modelName, device=deviceId, device_index=deviceNum, threads=threads, compute_type=compute_type, language=language)
            else:
                model = faster_whisper.WhisperModel(modelName, device=deviceId, cpu_threads=threads, compute_type=compute_type)
        else:
            raise e
    
    print(model_loaded, file=stdout, flush=True)
    print(deviceId, file=stdout, flush=True)
    
    while True:
        
        line = input()

        if line == terminate:
            break
        if line == ping:
            print(ping, file=stdout, flush=True)
            continue

        transcription = ''
        logprobs = []
        try:
            if whisperx_found:
                audio = whisperx.load_audio(line)
                result = model.transcribe(audio, batch_size=batch_size, language=language)
                for segment in result['segments']:
                    transcription += segment['text']
                    if 'avg_logprob' in segment:
                        logprobs.append(segment['avg_logprob'])
            else:
                segments, info = model.transcribe(audio=line, language=language, beam_size=5, vad_filter=True)
                for segment in segments:
                    transcription += segment.text
                    logprobs.append(segment.avg_logprob)

        except Exception as e:
            msg = repr(e).replace('\n', ' ').replace('\r', ' ')
            print(msg, file=stdout, flush=True)
            continue
        
        text = transcription.replace('\n', ' ').replace('\r', ' ')
        
        if len(logprobs) == 0:
            finalScore = 0
        else:
            finalScore = numpy.mean(numpy.exp(logprobs))
        
        print(finished, file=stdout, flush=True)
        print(str(finalScore), file=stdout, flush=True)
        print(text, file=stdout, flush=True)

    return
    
if __name__ == "__main__":
     main()
