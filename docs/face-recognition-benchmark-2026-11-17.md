# Face recognition validation - 2026-11-17

This report supersedes the earlier informal PR comparisons. The personal-photo
corpus has no identity labels: detection counts and numerical agreement do not
establish recognition accuracy or the usefulness of additional detections.

## Setup and method

- Intel Core Ultra 9 185H, 16 cores/22 logical processors, 32 GB nominal RAM;
  RTX 4070 Laptop GPU, 8188 MiB, driver 596.08; Windows 11, AC power.
- All 377 originals, including images without faces; EXIF orientation supplied
  correctly, with undefined orientation 0 treated as 1. The content manifest is
  `065a838f5e7faf59a4e49093cc97559d3ee1b68189a27ab35a2c71a14bed4fb9`.
- Defaults: `maxResolution=1024`, `detSize=640`, `minDetScore=0.5`; two ORT
  intra-op threads and one inter-op thread per session. One warmup per worker;
  startup reported separately.
- Worker wall time includes decoding, preprocessing, inference and IPC. It
  excludes downloads, Java indexing, the JVM cache and similarity-search latency.
  Models were cached and verified. RSS is summed across workers; GPU memory is
  sampled **system-wide**, not attributed to CPU inference. Memory units are MiB.
- Python 3.10.22, NumPy 1.26.4, OpenCV 4.11.0.86, Pillow 11.3.0, ORT 1.22.0.
  Final CUDA measurements use cuDNN 9.10.2.21 and FP32 (`use_tf32=0`).
- dlib comparison uses the unmodified production worker from upstream commit
  `8978445ae72fc263ab68748d3e991c946db394e0`, dlib 19.24.8 CPU/AVX build,
  face-recognition 1.3.0 and models 0.3.0. The runner rejects CUDA-enabled dlib.

[Setup and reproduction commands](face-recognition.md) and
[aggregate raw measurements, versions and source fingerprints](face-recognition-benchmark-2026-11-17.json)
are provided. Photos, filenames, individual image hashes, boxes and vectors are
excluded from publication.

## Measurements

| Configuration | Workers | Runs | Output faces | Median wall | Peak worker RSS | Peak system GPU |
|---|---:|---:|---:|---:|---:|---:|
| dlib HOG CPU | 1 | 3 | 437 | 170.8 s | 398 | 0 |
| dlib HOG CPU | 4 | 3 | 437 | 53.4 s | 1237 | 0 |
| AuraFace CPU, initial sweep | 1 | 3 | 1383 | 333.9 s | 901 | 0 |
| AuraFace CPU, initial sweep | 4 | 3 | 1383 | 131.8 s | 3283 | 0 |
| AuraFace CUDA, final FP32 | 1 | 3 | 1383 | 67.2 s | 1192 | 1697 |
| AuraFace CUDA, final FP32 | 4 | 3 | 1383 | 26.3 s | 4484 | 5245 |

Every completed configuration had zero image, box or embedding-validation errors.
AuraFace returned 65 no-face images; default HOG returned 152. These are model
outputs, not ground truth. CPU reference timings preceded the CUDA-only precision
and runtime refinements; their source hashes and the original GPU measurements
remain in the JSON.

**CPU variability matters.** A final full-corpus CPU validation with four workers
took **210.1 s**, still returning 1383 valid embeddings and recording zero GPU
memory. An interleaved old/current-worker A/B then used the same 32 images and
environment:

| Pair | Earlier worker | Current worker |
|---|---:|---:|
| 1 | 12.68 s | 13.35 s |
| 2 | 15.66 s | 14.31 s |
| 3 | 16.33 s | 16.08 s |

All six A/B runs returned 97 faces and identical count digests, with CPU-only
providers and no errors. Both revisions slowed with run order; the check did not
reproduce a fixed 60% code regression. A thermal/scheduling cause was not
established. The CPU throughput cost remains material; avoid extrapolating one
laptop ratio to unspecified workstation/server CPUs.

Additional requested comparisons:

- **HOG at 2048:** four workers, one complete pass, **113.4 s**, **786** faces,
  1324 MiB worker RSS and zero GPU memory. Keep this tuned single pass separate
  from default settings.
- **CNN on CPU:** one and four workers did not complete under the configured
  600-second configuration/120-second response limits. Failures are recorded;
  partial results are not throughput measurements. dlib CNN GPU was not tested.

## Provider correctness and the reported GPUs

The [maintainer's comment](https://github.com/sepinf-inc/IPED/pull/2823#issuecomment-6949807062)
specifies P620 workstations (2 GB) and ASPEED server graphics (16 MB).

- **No-CUDA server path:** a CPU-only ORT installation in `auto` selected only
  CPU providers. Real profiling recorded 139 detector and 308 recognizer CPU
  kernel events, with no CUDA kernels. Explicit CPU runs also recorded zero GPU
  memory. This verifies backend selection on the local CPU, not server speed.
- **P620 software compatibility:** NVIDIA lists P620 as compute capability 6.1.
  cuDNN 9.28 excludes it, so the environment now pins **9.10.2.21**, which documents
  Pascal/6.1 support with CUDA 12.x. The ORT 1.22 Windows build includes SM 6.1.
  [NVIDIA GPU list](https://developer.nvidia.com/cuda/gpus/legacy),
  [cuDNN matrix](https://docs.nvidia.com/deeplearning/cudnn/backend/v9.10.2/reference/support-matrix.html),
  [ORT build](https://github.com/microsoft/onnxruntime/blob/v1.22.0/tools/ci_build/github/azure-pipelines/stages/py-gpu-packaging-stage.yml).
- **2 GiB resource proxy:** one worker shared a 1536 MiB CUDA arena across both
  models. All 377 images completed with 1383 embeddings and **1175 MiB observed
  peak GPU memory**, from a zero baseline. Profiling confirmed CUDA execution in
  both models. Arena limits exclude some allocations, sampling can miss brief
  peaks, and WDDM residency/paging differs from a physical 2 GB card.
  [ORT memory-limit documentation](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html#gpu_mem_limit).
- **Recovery under real allocation failures:** 32 MiB and 512 MiB probes caused
  initialization and inference failures respectively. Automatic mode switched
  both models to CPU once, processed all 16 images/62 faces, and retained CPU.
  Explicit CUDA remains strict. Start with **one worker** on a 2 GB GPU; four
  workers exceeded that memory budget in the measured configuration.
- **Numerical agreement:** TF32 initially produced maximum embedding cosine
  difference 0.0010435. Diagnostics traced most drift to detector landmarks and
  alignment. With TF32 disabled, 16 images/62 faces had identical boxes and maximum
  cosine difference **0.00002620**, below the unchanged 0.0001 gate. This checks
  numerical consistency, not identity recognition.

The resource proxy ran on an RTX 4070. It does not establish P620 speed or actual
card compatibility. Actual P620 hardware, driver and display-memory constraints
still require testing. ASPEED systems use CPU processing.

## Validation and limits

- **92 Python tests, 15 Java tests**, and a Java 11/Maven package build across all
  17 modules. Serialization tests also ran with NumPy 2.
- Packaged IPED CLI processed three photos with orientations 1, 6 and 8 through
  two CUDA workers, exited **0**, and reaped both workers. A read-only verifier
  found **33 persisted, normalized 512-dimensional embeddings**, correct model
  tags, matching boxes/counts and model-specific KNN fields. Norm range:
  0.99999994-1.00000008.
- Legacy tests reopen and merge an original 128-dimensional Lucene index after
  modern and legacy appends, including item loading/import and model isolation.
- Elasticsearch append mapping updates were checked with the actual HTTP client
  against a local protocol stub, including schema incompatibility and rejected
  acknowledgments. A live OpenSearch deployment and interactive GUI were not
  tested.
- The cached local bundled Jep Python needed NumPy 1.26.4 restored for existing
  IPED tasks. Model inference uses the dedicated Conda environment.
- The extra detections still need labeled recognition evaluation. Model-card
  accuracies and embedding dimensions do not establish case-level false-match
  counts. Existing multi-face AND semantics were not redesigned.
