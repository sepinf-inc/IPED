import sys
stdout = sys.stdout
sys.stdout = sys.stderr

terminate = 'terminate_process'
model_loaded = 'wav2vec2_model_loaded'
huggingsound_loaded = 'huggingsound_loaded'
finished = 'transcription_finished'
ping = 'ping'

def main():

    modelName = sys.argv[1]
    deviceNum = sys.argv[2]

    from huggingsound import SpeechRecognitionModel
    
    print(huggingsound_loaded, file=stdout, flush=True)
    
    import torch
    cudaCount = torch.cuda.device_count()

    print(str(cudaCount), file=stdout, flush=True)

    if cudaCount > 0:
        deviceId = 'cuda:' + deviceNum
    else:
        deviceId = 'cpu'
    
    try:
        model = SpeechRecognitionModel(modelName, device=deviceId)
    except Exception as e:
        if deviceId != 'cpu':
            # loading on GPU failed (OOM?), try on CPU
            deviceId = 'cpu'
            model = SpeechRecognitionModel(modelName, device=deviceId)
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

        paths = [line]
        try:
            transcriptions = model.transcribe(paths)
            
        except Exception as e:
            msg = repr(e).replace('\n', ' ').replace('\r', ' ')
            print(msg, file=stdout, flush=True)
            continue
        
        text = transcriptions[0].get('transcription')
        text = text.replace('\n', ' ').replace('\r', ' ')
        probabilities = transcriptions[0].get('probabilities')
        
        if probabilities is None or len(probabilities) == 0:
            text = ''
            probabilities = [0]
        
        sum = 0
        for p in probabilities:
            sum += p
        
        finalScore = sum / len(probabilities)
        
        print(finished, file=stdout, flush=True)
        print(str(finalScore), file=stdout, flush=True)
        print(text, file=stdout, flush=True)

    return
    
if __name__ == "__main__":
     main()
