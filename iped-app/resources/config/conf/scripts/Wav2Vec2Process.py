import sys
stdout = sys.stdout
sys.stdout = sys.stderr

terminate = 'terminate_process'
model_loaded = 'wav2vec2_model_loaded'
finished = 'transcription_finished'
ping = 'ping'

max_files = 100000
processed_files = 0

def main():

    modelName = sys.argv[1]

    from huggingsound import SpeechRecognitionModel
    model = SpeechRecognitionModel(modelName)
    
    print(model_loaded, file=stdout, flush=True)
    
    while True:
        global processed_files
        #if processed_files >= max_files:
        #    break
        
        line = input()

        if line == terminate:
            break
        if line == ping:
            print(ping, file=stdout, flush=True)
            continue
        
        processed_files += 1

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
