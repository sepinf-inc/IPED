import sys
sys.stdout.reconfigure(encoding='utf-8')
stdout = sys.stdout
sys.stdout = sys.stderr

import numpy

terminate = 'terminate_process'
model_loaded = 'model_loaded'
library_loaded = 'library_loaded'
finished = 'transcription_finished'
ping = 'ping'

def main():
    modelName = sys.argv[1]
    device = sys.argv[2]
    deviceNum = int(sys.argv[3])
    threads = int(sys.argv[4])
    language = sys.argv[5]
    compute_type = sys.argv[6]
    batch_size = int(sys.argv[7])
    
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
    
    if device != 'gpu':
        cudaCount = 0
        if compute_type == 'float16': # not supported on CPU
            compute_type = 'int8'
    else:
        import GPUtil
        cudaCount = len(GPUtil.getGPUs())
        if cudaCount == 0:
            raise RuntimeError('No GPU device detected!')
    
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
        
        files = line.split(",")
        transcription = []
        logprobs = []
        for file in files:
            transcription.append("")
            logprobs.append([])
        try:
            if whisperx_found:
                result = model.transcribe(files, batch_size=batch_size, language=language,wav=True)
                for segment in result['segments']:
                    idx = segment["audio"]
                    transcription[idx] += segment['text']
                    if 'avg_logprob' in segment:
                        logprobs[idx].append(segment['avg_logprob'])
            else:
                for idx in range(len(files)):
                    segments, info = model.transcribe(audio=files[idx], language=language, beam_size=5, vad_filter=True)
                    for segment in segments:
                        transcription[idx] += segment.text
                        logprobs[idx].append(segment.avg_logprob)

        except Exception as e:
            msg = repr(e).replace('\n', ' ').replace('\r', ' ')
            print(msg, file=stdout, flush=True)
            continue
        
        print(finished, file=stdout, flush=True)
        
        for idx in range(len(files)):
            text = transcription[idx].replace('\n', ' ').replace('\r', ' ')
            
            if len(logprobs[idx]) == 0:
                finalScore = 0
            else:
                finalScore = numpy.mean(numpy.exp(logprobs[idx]))
            print(str(finalScore), file=stdout, flush=True)
            print(text, file=stdout, flush=True)

    return
    
if __name__ == "__main__":
     main()
