"""
External process used by SpaCyNERecogniser to perform Named Entity Recognition with SpaCy.
Runs out-of-process to avoid Python GIL bottleneck and JVM OOM/memory fragmentation.
"""

import sys
import base64
import json

# Ensure stdout uses UTF-8 and redirect print to stderr by default
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
stdout = sys.stdout
sys.stdout = sys.stderr

SPACY_LOADED = 'spacy_loaded'
MODEL_LOADED = 'model_loaded'
FINISHED = 'ner_finished'
TERMINATE = 'terminate_process'
PING = 'ping'

# Standard entity label mapping to IPED / Tika standard categories
LABEL_MAP = {
    # Common SpaCy / CoNLL labels (Portuguese, Spanish, French, German)
    'PER': 'PERSON',
    'PERSON': 'PERSON',
    'LOC': 'LOCATION',
    'GPE': 'LOCATION',
    'FAC': 'LOCATION',
    'ORG': 'ORGANIZATION',
    'MISC': 'MISCELLANEOUS',
    'NORP': 'MISCELLANEOUS',
    'EVENT': 'MISCELLANEOUS',
    'WORK_OF_ART': 'MISCELLANEOUS',
    'LAW': 'MISCELLANEOUS',
    'LANGUAGE': 'MISCELLANEOUS',
    'PRODUCT': 'MISCELLANEOUS',
    'DATE': 'DATE',
    'TIME': 'TIME',
    'MONEY': 'MONEY',
    'PERCENT': 'PERCENT',
    'QUANTITY': 'MISCELLANEOUS',
}


def main():
    if len(sys.argv) < 2:
        print("Usage: SpaCyProcess.py <lang1:model1,lang2:model2,...>", file=sys.stderr)
        sys.exit(1)

    models_config = sys.argv[1]

    try:
        import spacy
        print(SPACY_LOADED, file=stdout, flush=True)
    except Exception as e:
        msg = f"ERROR_IMPORT: {repr(e).replace(chr(10), ' ')}"
        print(msg, file=stdout, flush=True)
        sys.exit(1)

    loaded_models = {}
    nlp_cache = {}
    for entry in models_config.split(','):
        entry = entry.strip()
        if not entry or ':' not in entry:
            continue
        lang, model_name = entry.split(':', 1)
        lang = lang.strip()
        model_name = model_name.strip()
        try:
            if model_name not in loaded_models:
                loaded_models[model_name] = spacy.load(model_name)
            nlp_cache[lang] = loaded_models[model_name]
        except Exception as e:
            msg = f"ERROR_MODEL: Model '{model_name}' for lang '{lang}' failed to load: {repr(e).replace(chr(10), ' ')}"
            print(msg, file=stdout, flush=True)
            sys.exit(2)

    if not nlp_cache:
        print("ERROR_CONFIG: No valid models were loaded.", file=stdout, flush=True)
        sys.exit(3)

    print(MODEL_LOADED, file=stdout, flush=True)

    while True:
        try:
            line = input()
        except EOFError:
            break

        if not line or line == TERMINATE:
            break

        if line == PING:
            print(PING, file=stdout, flush=True)
            continue

        # Format: RECOGNIZE <lang> <base64_text>
        parts = line.split(" ", 2)
        if len(parts) < 3 or parts[0] != "RECOGNIZE":
            print("{}", file=stdout, flush=True)
            continue

        lang = parts[1]
        b64_str = parts[2]

        nlp = nlp_cache.get(lang)
        if nlp is None:
            nlp = nlp_cache.get("default")
        if nlp is None and nlp_cache:
            nlp = next(iter(nlp_cache.values()))

        if nlp is None:
            print("{}", file=stdout, flush=True)
            continue

        try:
            raw_bytes = base64.b64decode(b64_str)
            text = raw_bytes.decode('utf-8', errors='replace')
            doc = nlp(text)

            result = {}
            for ent in doc.ents:
                cat = LABEL_MAP.get(ent.label_, ent.label_)
                mention = ent.text.strip()
                if not mention:
                    continue
                if cat not in result:
                    result[cat] = []
                if mention not in result[cat]:
                    result[cat].append(mention)

            output = json.dumps(result, ensure_ascii=False)
            print(output, file=stdout, flush=True)
        except Exception as e:
            print(f"Error during recognition: {e}", file=sys.stderr)
            print("{}", file=stdout, flush=True)


if __name__ == '__main__':
    main()
