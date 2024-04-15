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

    from faster_whisper import WhisperModel
    
    print(library_loaded, file=stdout, flush=True)
    
    import GPUtil
    cudaCount = len(GPUtil.getGPUs())

    print(str(cudaCount), file=stdout, flush=True)

    if cudaCount > 0:
        deviceId = 'cuda'
    else:
        deviceId = 'cpu'
        deviceNum = 0
    
    try:
        model = WhisperModel(modelName, device=deviceId, device_index=deviceNum, cpu_threads=threads, compute_type="float16")

    except Exception as e:
        if deviceId != 'cpu':
            # loading on GPU failed (OOM?), try on CPU
            deviceId = 'cpu'
            model = WhisperModel(model_size_or_path=modelName, device=deviceId, cpu_threads=threads, compute_type="float16")
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
        probs = []
        try:
            segments, info = model.transcribe(audio=line, language=language, beam_size=5, word_timestamps=True)
            for segment in segments:
                transcription += segment.text
                words = segment.words
                if words is not None:
                    probs += [word.probability for word in words]

        except Exception as e:
            msg = repr(e).replace('\n', ' ').replace('\r', ' ')
            print(msg, file=stdout, flush=True)
            continue
        
        text = transcription.replace('\n', ' ').replace('\r', ' ')
        
        probs = probs if len(probs) != 0 else [0]
        finalScore = numpy.average(probs)
        
        print(finished, file=stdout, flush=True)
        print(str(finalScore), file=stdout, flush=True)
        print(text, file=stdout, flush=True)

    return
    
if __name__ == "__main__":
     main()
