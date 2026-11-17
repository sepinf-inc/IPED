# Face recognition setup and validation

The default pipeline uses RetinaFace-R50 detection and AuraFace-v1 recognition.
GPU acceleration is recommended. CPU processing is supported, but has a substantial
throughput cost. Detection counts alone do not measure recognition accuracy.

## Dedicated Python environment

From the source checkout, create a dedicated environment:

```shell
conda env create -f environment.yml
conda activate iped-face
python -c "import sys; print(sys.executable)"
```

For a CPU-only installation use `environment-cpu.yml` and activate `iped-face-cpu`.
Each environment installs exactly one ONNX Runtime package. The GPU environment
includes pinned CUDA/cuDNN runtime libraries; a compatible NVIDIA driver is still
required. ONNX Runtime loads these libraries before creating model sessions.

Set `pythonPath` in the release's `conf/FaceRecognitionConfig.txt` to the absolute
Python executable printed above. On Windows, shell activation does not replace the
bundled Python selected by IPED. The bundled dlib Python does not supply this
pipeline's dependencies. The environment is an external subprocess runtime and does
not need Jep, Java, or Maven installed into it.

Enable `enableFaceRecognition` in `IPEDConfig.txt`, then choose a provider:

```properties
insightFaceModel = auraface
executionProvider = auto
```

- `auto`: prefers CUDA and logs CPU fallback.
- `cpu`: requests only `CPUExecutionProvider`, even with the GPU package installed.
- `cuda`: requires CUDA to load for both detection and recognition; provider failure
  is an error rather than a valid GPU measurement.

Check setup before processing (replace the script location with its release path):

```shell
python iped-app/resources/scripts/tasks/FaceRecognitionProcess.py --check-dependencies --model auraface --execution-provider cuda
```

This checks imports and available providers without downloading models. Actual model
initialization additionally verifies the loaded providers. The optional proof tool
described below records kernel execution, which is stronger evidence than a list of
compiled providers.

Prove execution with both real models (the model directory here contains the two
ONNX files directly):

```shell
python iped-app/resources/scripts/tasks/tests/profile_face_providers.py --model-dir /path/to/models/insightface/models/auraface --execution-provider cpu --output target/face-provider-cpu.json
python iped-app/resources/scripts/tasks/tests/profile_face_providers.py --model-dir /path/to/models/insightface/models/auraface --execution-provider cuda --output target/face-provider-cuda.json
```

Both commands use a synthetic blank, retain no face data, and fail if the expected
kernel provider does not execute. A few CPU graph/shape operations in a CUDA
detector session are normal; CUDA kernels must execute in both neural models.

`buffalo_l` and `buffalo_s` remain optional and require `insightface`; their model
licenses differ from the default stack. Install that package into a separate copy
of the environment if needed. The pinned NumPy 1.x environment supports its older
extension ABI. Additional build tools may be needed to install InsightFace.

## Model preparation and existing cases

The default models download on first use. For an offline system, prepare them on a
connected machine using the release's `scripts/tasks/download_insightface_models.py`
and copy the resulting model directory. Runtime and offline setup share verified,
atomic downloads. The default location is `models/insightface/models/auraface` under
the release directory; `modelDir` selects the root above `models/auraface`.

New face records include `face_model`. AuraFace and the two buffalo models use
different embedding spaces and cannot cross-match, despite all having 512 values.
Existing untagged 128-dimensional dlib vectors retain their original distance,
scoring, and stored-reference searches. New AuraFace query embeddings cannot match
those old dlib vectors. Untagged experimental 512-dimensional vectors require
reprocessing, since their model cannot be inferred reliably.

Lucene keeps the original stored/doc-value encoding fields and separates KNN fields
by model and dimension. Elasticsearch retains the legacy 128-dimensional
`faces.face_encoding` field and adds separate modern model fields. An experimental
index created by an earlier revision of this PR with a 512-dimensional legacy field
must be rebuilt; Elasticsearch cannot change an existing vector field's dimension.

## Reproduce performance measurements

Use the actual production workers with one fixed input directory, including images
without faces. The runner passes each image's EXIF orientation, keeps default
thresholds, warms each worker once, and reports startup separately from processing.
Reports contain content hashes and aggregate results, without photo filenames.

```shell
python iped-app/src/test/python/benchmark_face_recognition.py --images /private/photos --python /path/to/iped-face/python --model-dir /path/to/release/models/insightface --output target/face-benchmark.json --repeats 3 --workers 1 4 --providers cpu cuda
```

For historical comparison, create `environment-dlib-benchmark.yml`, then append
`--dlib-python /path/to/iped-face-dlib-benchmark/python --baseline-ref origin/master`.
The runner extracts the unmodified worker from the pinned upstream commit and
requires a CPU-only dlib build. Add `--include-cnn` to test its CNN detector too.
Unavailable or timed-out configurations are reported as failures, never as partial
successful timing results. `--max-resolution 2048` provides the requested comparison
with a larger legacy scan limit; keep these results separate from defaults.

Memory is the sum of worker RSS. GPU memory is explicitly labeled **system-wide**,
including any unrelated programs; it is not attributed to CPU inference. The image
manifest and model hashes identify the tested bytes, but private photos prevent
independent reproduction of numerical results. Recognition quality requires labeled
identities and is not established by these timing runs.

## Targeted regression tests

```shell
python -m pip install pytest==8.4.2
python -m pytest iped-app/resources/scripts/tasks/tests -q
python -m pytest iped-app/src/test/python/test_benchmark_face_recognition.py -q
```

The tests exercise provider selection/fallback, complete protocol validation,
NumPy scalar serialization, all eight orientations, downloads, worker disposal,
configuration validation, and cache/model metadata. Java tests cover legacy and
modern scoring, mixed-vector indexes, model isolation, and Elasticsearch export.
