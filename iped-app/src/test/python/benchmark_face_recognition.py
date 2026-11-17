#!/usr/bin/env python3
"""Benchmark production face workers on one private, content-hashed image manifest.

No photos or filenames are written to the JSON report. Run with --help for options.
"""
import argparse
import ast
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import math
import os
from pathlib import Path
import queue
import re
import statistics
import subprocess
import sys
import tempfile
import threading
import time

import psutil
from PIL import Image


ROOT = Path(__file__).resolve().parents[4]
SCRIPT_PATH = "iped-app/resources/scripts/tasks/FaceRecognitionProcess.py"


def image_orientation(image):
    # EXIF 0 is undefined, and occurs in the original corpus. It carries no
    # rotation, just like missing orientation. Reject other malformed values.
    value = str(image.getexif().get(274, 1))
    if value == '0':
        return '1'
    if value not in {str(v) for v in range(1, 9)}:
        raise ValueError('Invalid EXIF orientation')
    return value


class Worker:
    def __init__(self, python, script, arguments, deadline, dlib=False):
        # The baseline is the unmodified upstream script. This wrapper only
        # rejects CUDA-enabled dlib builds, so CPU labels remain trustworthy.
        command = [python, "-u"]
        if dlib:
            wrapper = (
                "import dlib,runpy,sys\n"
                "print('dlib='+dlib.__version__+' DLIB_USE_CUDA='+str(dlib.DLIB_USE_CUDA),file=sys.stderr,flush=True)\n"
                "if dlib.DLIB_USE_CUDA:\n"
                "    raise RuntimeError('CPU-only dlib build required')\n"
                "sys.argv=sys.argv[1:]; runpy.run_path(sys.argv[0],run_name='__main__')"
            )
            command += ["-c", wrapper]
        command += [str(script)] + arguments
        env = os.environ.copy()
        python_dir = Path(python).resolve().parent
        env["PATH"] = os.pathsep.join(str(python_dir / p) for p in
                                      ("", "Scripts", "Library/bin")) + os.pathsep + env.get("PATH", "")
        self.proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, encoding="utf-8",
                                     errors="replace", env=env)
        self.lines = queue.Queue()
        self.stderr = []
        self.provider_ready = threading.Event()
        self.closed = False
        self.deadline = deadline
        self.output_thread = threading.Thread(target=self._read_output, daemon=True)
        self.error_thread = threading.Thread(target=self._read_errors, daemon=True)
        self.output_thread.start()
        self.error_thread.start()
        try:
            self.proc.stdin.write("ping\n")
            self.proc.stdin.flush()
            if self.read() != "ping":
                raise RuntimeError("Unexpected startup response")
            if not self.provider_ready.wait(timeout=max(0, min(5, deadline - time.monotonic()))):
                raise RuntimeError("Worker supplied no provider/build evidence")
            if dlib:
                if not any(line.endswith('DLIB_USE_CUDA=False') for line in self.stderr):
                    raise RuntimeError('CPU-only dlib build evidence missing')
            else:
                evidence = next((line for line in self.stderr if line.startswith('Providers:')), '')
                match = re.fullmatch(r'Providers: det=(\[.*?\]), rec=(\[.*?\])', evidence)
                if match is None:
                    raise RuntimeError('Malformed ONNX provider evidence')
                providers = [ast.literal_eval(value) for value in match.groups()]
                if arguments[-1] == 'cpu' and any(value != ['CPUExecutionProvider'] for value in providers):
                    raise RuntimeError('CPU benchmark unexpectedly activated another provider')
                if arguments[-1] == 'cuda' and any('CUDAExecutionProvider' not in value for value in providers):
                    raise RuntimeError('CUDA benchmark fell back to CPU')
        except Exception:
            self.close()
            raise

    def _read_output(self):
        try:
            for line in self.proc.stdout:
                self.lines.put(line.strip())
        except (OSError, ValueError):
            pass
        finally:
            self.lines.put(None)

    def _read_errors(self):
        try:
            for line in self.proc.stderr:
                # Retain startup evidence, but never persist private paths/errors.
                line = line.strip()
                if line.startswith(("Providers:", "InsightFace providers:", "dlib=")):
                    self.stderr.append(line)
                    self.provider_ready.set()
        except (OSError, ValueError):
            pass

    def read(self):
        remaining = min(120, self.deadline - time.monotonic())
        if remaining <= 0:
            raise TimeoutError("Configuration exceeded its time limit")
        try:
            line = self.lines.get(timeout=remaining)
        except queue.Empty as exc:
            raise TimeoutError("Worker response timed out") from exc
        if line is None:
            evidence = '; '.join(self.stderr)
            raise RuntimeError("Worker exited with code " + str(self.proc.poll()) +
                               (" (" + evidence + ")" if evidence else ''))
        return line

    def image(self, image, legacy=False):
        path, orientation = image
        self.proc.stdin.write(str(path) + "\n" + orientation + "\n")
        self.proc.stdin.flush()
        line = self.read()
        if line == "image_error":
            return {"faces": 0, "image_errors": 1, "invalid_boxes": 0, "invalid_embeddings": 0}
        count = int(line)
        if count < 0 or count > 100000:
            raise ValueError("Invalid face count")
        bad_boxes = bad_embeddings = 0
        for _ in range(count):
            try:
                box = tuple(int(v) for v in self.read().strip("() ").split(","))
                if len(box) != 4 or box[1] <= box[3] or box[2] <= box[0]:
                    bad_boxes += 1
            except ValueError:
                bad_boxes += 1
        for _ in range(count):
            vector = ([float(self.read()) for _ in range(128)] if legacy
                      else [float(v) for v in self.read().split()])
            expected = 128 if legacy else 512
            if len(vector) != expected or not all(math.isfinite(v) for v in vector):
                bad_embeddings += 1
            elif not legacy and abs(sum(v * v for v in vector) - 1) > 1e-3:
                bad_embeddings += 1
        return {"faces": count, "image_errors": 0, "invalid_boxes": bad_boxes,
                "invalid_embeddings": bad_embeddings}

    def close(self):
        if self.closed:
            return
        self.closed = True
        try:
            if self.proc.poll() is None:
                try:
                    self.proc.stdin.write("terminate_process\n")
                    self.proc.stdin.flush()
                    self.proc.wait(timeout=5)
                except (OSError, ValueError, subprocess.TimeoutExpired):
                    self.proc.kill()
            self.proc.wait(timeout=10)
        finally:
            self.output_thread.join(timeout=2)
            self.error_thread.join(timeout=2)
            for stream in (self.proc.stdin, self.proc.stdout, self.proc.stderr):
                try:
                    stream.close()
                except (OSError, ValueError):
                    pass


class Monitor:
    def __init__(self, workers):
        self.workers = workers
        self.stop_event = threading.Event()
        self.rss = []
        self.vram = []
        self.thread = threading.Thread(target=self.sample, daemon=True)

    @staticmethod
    def system_vram():
        try:
            result = subprocess.check_output(
                ["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
                text=True, timeout=5, stderr=subprocess.DEVNULL)
            return sum(float(v) for v in result.strip().splitlines())
        except (OSError, ValueError, subprocess.SubprocessError):
            return None

    def sample(self):
        while not self.stop_event.is_set():
            try:
                self.rss.append(sum(psutil.Process(w.proc.pid).memory_info().rss
                                    for w in self.workers) / 2**20)
            except psutil.Error:
                pass
            vram = self.system_vram()
            if vram is not None:
                self.vram.append(vram)
            self.stop_event.wait(0.5)

    def stop(self):
        self.stop_event.set()
        self.thread.join(timeout=6)


def benchmark(args, images, mode, processes, baseline_script=None, detector="hog"):
    legacy = baseline_script is not None
    name = "dlib_" + detector + "_cpu" if legacy else "auraface_" + mode
    result = {"configuration": name, "workers": processes, "status": "failed"}
    workers = []
    monitor = None
    deadline = time.monotonic() + args.timeout
    print(json.dumps({"starting": name, "workers": processes}), flush=True)
    try:
        initial_vram = Monitor.system_vram()
        start = time.perf_counter()
        for _ in range(processes):
            parameters = ([str(args.max_resolution), detector, "1"] if legacy else
                          [str(args.max_resolution), "auraface", str(args.det_size),
                           str(args.min_det_score), str(args.model_dir), mode])
            workers.append(Worker(args.dlib_python if legacy else args.python,
                                  baseline_script if legacy else ROOT / SCRIPT_PATH,
                                  parameters, deadline, dlib=legacy))
        result["startup_seconds"] = time.perf_counter() - start
        for worker in workers:
            warmup = worker.image(images[0], legacy)
            if any(warmup[key] for key in ('image_errors', 'invalid_boxes', 'invalid_embeddings')):
                raise ValueError('Warmup image produced invalid output')
        monitor = Monitor(workers)
        monitor.thread.start()
        runs = []
        for repeat in range(args.repeats):
            records = [None] * len(images)

            def consume(index):
                for image_index in range(index, len(images), processes):
                    records[image_index] = workers[index].image(images[image_index], legacy)

            start = time.perf_counter()
            pool = ThreadPoolExecutor(max_workers=processes)
            futures = [pool.submit(consume, index) for index in range(processes)]
            try:
                for future in as_completed(futures):
                    future.result()
            except Exception:
                # Unblock every outstanding pipe read before waiting for threads.
                for worker in workers:
                    try:
                        worker.close()
                    except (OSError, subprocess.SubprocessError):
                        # Continue reaping peers even if one process cannot exit.
                        pass
                for future in futures:
                    future.cancel()
                raise
            finally:
                pool.shutdown(wait=True, cancel_futures=True)
            elapsed = time.perf_counter() - start
            row = {"wall_seconds": elapsed, "milliseconds_per_image": elapsed * 1000 / len(images),
                   "images_per_second": len(images) / elapsed,
                   "no_face_images": sum(r["faces"] == 0 and not r["image_errors"] for r in records),
                   "counts_sha256": hashlib.sha256(
                       ",".join(str(r["faces"]) for r in records).encode()).hexdigest()}
            row.update({key: sum(r[key] for r in records)
                        for key in ("faces", "image_errors", "invalid_boxes", "invalid_embeddings")})
            runs.append(row)
            print(json.dumps({"configuration": name, "workers": processes,
                              "repeat": repeat + 1, **row}), flush=True)
        result.update(status="ok", runs=runs,
                      median_wall_seconds=statistics.median(r["wall_seconds"] for r in runs),
                      median_milliseconds_per_image=statistics.median(r["milliseconds_per_image"] for r in runs))
        if any(r[k] for r in runs for k in ("image_errors", "invalid_boxes", "invalid_embeddings")):
            result["status"] = "invalid"
        if len({r["counts_sha256"] for r in runs}) != 1:
            result["status"] = "inconsistent_counts"
    except (RuntimeError, ValueError, TimeoutError, OSError) as exc:
        result["error"] = type(exc).__name__ + ": " + str(exc)
    finally:
        if monitor:
            monitor.stop()
            result["peak_process_rss_mb"] = max(monitor.rss, default=None)
            result["system_vram_baseline_mb"] = initial_vram
            result["peak_system_vram_mb"] = max(monitor.vram, default=None)
        for worker in workers:
            try:
                worker.close()
            except (OSError, subprocess.SubprocessError) as exc:
                result['status'] = 'failed'
                result['error'] = 'Worker cleanup failed: ' + type(exc).__name__
        result["provider_evidence"] = [list(w.stderr) for w in workers]
    print(json.dumps(result), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--python", required=True, help="AuraFace environment Python executable")
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--workers", type=int, nargs="+", default=[1, 4])
    parser.add_argument("--providers", nargs="+", choices=["cpu", "cuda"], default=["cpu", "cuda"])
    parser.add_argument("--max-resolution", type=int, default=1024)
    parser.add_argument("--det-size", type=int, default=640)
    parser.add_argument("--min-det-score", type=float, default=0.5)
    parser.add_argument("--dlib-python", help="Optional separate historical dlib environment")
    parser.add_argument("--baseline-ref", default="origin/master")
    parser.add_argument("--include-cnn", action="store_true")
    parser.add_argument("--timeout", type=float, default=1800, help="Maximum seconds per configuration, all repeats")
    args = parser.parse_args()
    if args.repeats < 1 or min(args.workers) < 1:
        parser.error("Use positive worker counts and repetitions")
    if args.max_resolution < 1 or args.det_size < 1 or not math.isfinite(args.timeout) or args.timeout <= 0:
        parser.error('Use positive resolutions and a finite positive timeout')
    if not math.isfinite(args.min_det_score) or not 0 <= args.min_det_score <= 1:
        parser.error('Detection score must be between 0 and 1')
    if not args.images.is_dir():
        parser.error('The image corpus must be a directory')
    images = []
    hashes = []
    versions = subprocess.check_output([args.python, "-c",
        "import sys,numpy,cv2,PIL,onnxruntime,json;print(json.dumps(dict(python=sys.version,numpy=numpy.__version__,opencv=cv2.__version__,pillow=PIL.__version__,onnxruntime=onnxruntime.__version__)))"], text=True, timeout=60)
    for path in sorted(args.images.iterdir()):
        if not path.is_file() or path.suffix.lower() not in (".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".webp"):
            continue
        try:
            with Image.open(path) as image:
                orientation = image_orientation(image)
        except (OSError, ValueError):
            parser.error('A supported image has unreadable metadata; fix the corpus before benchmarking')
        images.append((path.resolve(), orientation))
        hashes.append(hashlib.sha256(path.read_bytes()).hexdigest())
    if not images:
        parser.error("No supported images found")
    if args.repeats * math.ceil(len(images) / min(args.workers)) + 1 > 2000:
        parser.error("This corpus exceeds the worker's 2000-image lifecycle; reduce repeats")
    report = {"head_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "image_count": len(images), "versions": json.loads(versions),
              "manifest_sha256": hashlib.sha256("\n".join(hashes).encode()).hexdigest(),
              "ordered_image_sha256": hashes,
              "source_sha256": {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
                                for path in (SCRIPT_PATH,
                                             'iped-app/resources/scripts/tasks/FaceRecognitionModelConfig.py')},
              "orientation_counts": {code: sum(i[1] == code for i in images)
                                     for code in sorted({i[1] for i in images})},
              "settings": {"max_resolution": args.max_resolution, "det_size": args.det_size,
                           "min_det_score": args.min_det_score, "repeats": args.repeats,
                           "warmup_images_per_worker": 1},
              "memory_note": "RSS summed across workers; VRAM is system-wide, not attributable to these processes.",
              "results": []}
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    with tempfile.TemporaryDirectory(prefix="iped-dlib-benchmark-") as temp:
        if args.dlib_python:
            report["baseline_sha"] = subprocess.check_output(
                ["git", "rev-parse", args.baseline_ref], cwd=ROOT, text=True).strip()
            baseline = Path(temp) / "FaceRecognitionProcess.py"
            baseline.write_bytes(subprocess.check_output(
                ["git", "show", report["baseline_sha"] + ":" + SCRIPT_PATH], cwd=ROOT))
            for detector in (["hog", "cnn"] if args.include_cnn else ["hog"]):
                for workers in args.workers:
                    report["results"].append(benchmark(args, images, "cpu", workers, baseline, detector))
                    save()
        for provider in args.providers:
            for workers in args.workers:
                report["results"].append(benchmark(args, images, provider, workers))
                save()
    return 0 if all(r["status"] == "ok" for r in report["results"]) else 1


if __name__ == "__main__":
    sys.exit(main())
