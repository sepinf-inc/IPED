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

    from huggingsound import SpeechRecognitionModel
    
    print(huggingsound_loaded, file=stdout, flush=True)
    
    model = SpeechRecognitionModel(modelName)
    
    print(model_loaded, file=stdout, flush=True)
    
    while True:
        
        line = input()

        if line == terminate:
            break
        if line == ping:
            print(ping, file=stdout, flush=True)
            continue

        paths = [line]        
        transcriptions = model.transcribe(paths)
        
        text = transcriptions[0].get('transcription')
        text = text.replace('\n', ' ').replace('\r', ' ')
        probabilities = transcriptions[0].get('probabilities')
        
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
