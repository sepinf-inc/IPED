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
    
    if language == 'detect':
        language = None

    import whisperx
    
    print(library_loaded, file=stdout, flush=True)
    
    import GPUtil
    cudaCount = len(GPUtil.getGPUs())

    print(str(cudaCount), file=stdout, flush=True)

    compute_type = 'int8'
    if cudaCount > 0:
        deviceId = 'cuda'
        compute_type = 'float16'
    else:
        deviceId = 'cpu'
        deviceNum = 0
    
    try:
        model = whisperx.load_model(modelName, device=deviceId, device_index=deviceNum, threads=threads, compute_type=compute_type)

    except Exception as e:
        if deviceId != 'cpu':
            # loading on GPU failed (OOM?), try on CPU
            deviceId = 'cpu'
            compute_type = 'int8'
            model = WhisperModel(model_size_or_path=modelName, device=deviceId, cpu_threads=threads, compute_type=compute_type)
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
            audio = whisperx.load_audio(line)
            result = model.transcribe(audio, batch_size=8, language=language)
            for segment in result['segments']:
                transcription += segment['text']
                if 'avg_logprob' in segment:
                    logprobs.append(segment['avg_logprob'])

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
