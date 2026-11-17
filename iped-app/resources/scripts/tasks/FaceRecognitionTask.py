'''
# Python face recognition feature based on RetinaFace-R50 (MIT) + AuraFace-v1 (Apache 2.0)
# FaceRecognitionTask.py - Originally by Rui Sant'Ana Junior and Luis Nassif, updated for ONNX-based detection + recognition
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#facerecognition
# If enabled, you can search for faces from the analysis interface, check the options menu.
'''

import os
import time
import subprocess
import threading, queue
import traceback
import platform
import math
import ast

# configuration properties
enableProp = 'enableFaceRecognition'
configFile = 'FaceRecognitionConfig.txt'
numFaceRecognitionProcessesProp = 'numFaceRecognitionProcesses'
maxResolutionProp = 'maxResolution'
insightFaceModelProp = 'insightFaceModel'
detSizeProp = 'detSize'
minDetScoreProp = 'minDetScore'
minSizeProp = 'minSize'
modelDirProp = 'modelDir'
pythonPathProp = 'pythonPath'
executionProviderProp = 'executionProvider'

# External process script
processScript = 'FaceRecognitionProcess.py'

# Maximum number of face recognition processes to run simultaneously
maxProcesses = None
numCreatedProcs = 0
numCreatedProcsLock = threading.Lock()

# Initialized task instances sharing the subprocess pool
activeInstances = 0

from java.lang import Runtime, System
ipedRoot = System.getProperty('iped.root')

bin = 'python' if platform.system().lower() == 'windows' else 'python3'

model_name = 'auraface'
max_size = 1024
det_size = 640
min_det_score = 0.5
min_size = 48
model_dir = None
execution_provider = 'auto'

# Protocol strings (must match FaceRecognitionProcess.py)
terminate = 'terminate_process'
imgError = 'image_error'
ping = 'ping'
video = 'video'

processQueue = None
cache = {}

timeLock = threading.Lock()
initLock = threading.Lock()
responseTime = 0

def createProcessQueue():
    global processQueue, maxProcesses
    if processQueue is None:
        if maxProcesses is None:
            numThreads = Runtime.getRuntime().availableProcessors()
            # Default to min(4, numThreads/2). Each process loads ~300MB model.
            # With GPU (auto-detected by onnxruntime), even 1 process is fast;
            # multiple processes help on CPU-only machines.
            maxProcesses = int(max(1, min(4, numThreads / 2)))
        processQueue = queue.Queue(maxProcesses)

def log_stderr(proc):
    for line in iter(proc.stderr.readline, ''):
        line = line.strip()
        if line:
            logger.info("[FaceRecognitionTask] Process-" + str(proc.pid) + " stderr: " + line)
    proc.stderr.close()


def stopExternalProcess(proc, graceful=False):
    """Reap a worker even when its protocol or pipes are broken."""
    try:
        if proc.poll() is None:
            if graceful:
                try:
                    print(terminate, file=proc.stdin, flush=True)
                    proc.wait(timeout=2)
                except Exception:
                    proc.kill()
            else:
                proc.kill()
        proc.wait(timeout=5)
    except Exception as exc:
        logger.warn('[FaceRecognitionTask] Error stopping subprocess: ' + str(exc))
    finally:
        for stream in (proc.stdin, proc.stdout):
            try:
                stream.close()
            except Exception:
                pass


def discardExternalProcess(proc):
    global numCreatedProcs
    stopExternalProcess(proc)
    with numCreatedProcsLock:
        numCreatedProcs -= 1


def acquireExternalProcess():
    """Reserve a slot or wait, rechecking slots freed by failed workers."""
    global numCreatedProcs
    deadline = time.monotonic() + 300
    while True:
        try:
            return processQueue.get_nowait()
        except queue.Empty:
            pass
        with numCreatedProcsLock:
            create = numCreatedProcs < maxProcesses
            if create:
                numCreatedProcs += 1
        if create:
            try:
                return createExternalProcess()
            except Exception:
                with numCreatedProcsLock:
                    numCreatedProcs -= 1
                raise
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError('Timed out waiting for face recognition subprocess after 5 min')
        try:
            return processQueue.get(timeout=min(1, remaining))
        except queue.Empty:
            continue


def checkDependencies():
    command = [bin, os.path.join(ipedRoot, 'scripts', 'tasks', processScript),
               '--check-dependencies', '--model', model_name,
               '--execution-provider', execution_provider]
    try:
        # This is a noninteractive probe; never inherit the JVM launcher's input pipe.
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, universal_newlines=True,
                                env=buildSubprocessEnv(), timeout=60)
        if result.returncode != 0:
            raise RuntimeError((result.stderr or result.stdout or
                                'dependency check exited with status ' + str(result.returncode)).strip())
    except Exception as exc:
        raise RuntimeError("Face recognition Python setup failed for '" + str(bin) +
                           "'. Configure pythonPath to the intended conda/virtual environment "
                           "and install its face recognition dependencies. " + str(exc)) from exc

# Build subprocess environment: inherit current env but prepend conda/venv Library\bin and Scripts
# so that CUDA/cuDNN DLLs next to the configured python executable are found by onnxruntime.
def buildSubprocessEnv():
    env = os.environ.copy()
    if bin and bin != 'python' and bin != 'python3':
        python_dir = os.path.dirname(os.path.abspath(bin))
        extra_paths = [
            python_dir,
            os.path.join(python_dir, 'Scripts'),
            os.path.join(python_dir, 'Library', 'bin'),
            os.path.join(python_dir, 'Library', 'mingw-w64', 'bin'),
        ]
        existing = env.get('PATH', '')
        env['PATH'] = os.pathsep.join(p for p in extra_paths if os.path.isdir(p)) + os.pathsep + existing
    return env

# Start external process, check if it is alive and ping to test communication
def createExternalProcess():
    proc = None
    env = buildSubprocessEnv()
    logger.info("[FaceRecognitionTask] Starting subprocess: " + str(bin))
    for i in range(3):
        if proc is None or proc.poll() is not None:
            args_list = [bin, os.path.join(ipedRoot, 'scripts', 'tasks', processScript),
                         str(max_size), model_name, str(det_size), str(min_det_score),
                         model_dir or '', execution_provider]
            try:
                proc = subprocess.Popen(args_list,
                                        stdout=subprocess.PIPE, stdin=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True,
                                        env=env)
            except Exception as e:
                logger.error("[FaceRecognitionTask] Failed to launch subprocess (attempt " + str(i+1) + "): " + str(e))
                logger.error("[FaceRecognitionTask] Command: " + str(args_list))
                proc = None
                continue

            # Start logging stderr immediately so model-download/import errors are visible
            from threading import Thread
            t = Thread(target=log_stderr, args=(proc,))
            t.daemon = True
            t.start()

        if pingExternalProcess(proc):
            return proc
        else:
            stopExternalProcess(proc)
            proc = None

    raise Exception("Error creating external face recognition process! Check that '" + str(bin) + "' has the required dependencies (onnxruntime, opencv-python, numpy, pillow).")

def pingExternalProcess(proc):
    try:
        print(ping, file=proc.stdin, flush=True)
        line = proc.stdout.readline().strip()
        if line == ping:
            return True
    except:
        traceback.print_exc()
    return False


def readResponse(proc):
    """Validate the complete response before publishing any item attributes."""
    line = proc.stdout.readline().strip()
    if line == imgError:
        return None
    count = int(line)
    if count < 0 or count > 100000:
        raise ValueError('Invalid face count: ' + line)
    locations = []
    for _ in range(count):
        location = ast.literal_eval(proc.stdout.readline())
        if (not isinstance(location, (list, tuple)) or len(location) != 4 or
                any(type(value) is not int for value in location) or
                location[2] <= location[0] or location[1] <= location[3]):
            raise ValueError('Invalid face bounding box')
        locations.append(list(location))
    encodings = []
    for _ in range(count):
        encoding = [float(value) for value in proc.stdout.readline().split()]
        if len(encoding) != 512 or not all(math.isfinite(value) for value in encoding):
            raise ValueError('Invalid face embedding: expected 512 finite values')
        normSquared = sum(value * value for value in encoding)
        if abs(normSquared - 1) > 0.001:
            raise ValueError('Invalid face embedding: expected unit L2 norm')
        encodings.append(encoding)
    return locations, encodings

class FaceRecognitionTask:

    enabled = None
    videoSubitems = False

    def isEnabled(self):
        return False if FaceRecognitionTask.enabled is None else FaceRecognitionTask.enabled

    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]

    # Initialize shared configuration once for each processing/GUI lifecycle.
    def init(self, configuration):
        global activeInstances, maxProcesses, bin, max_size, model_name
        global det_size, min_det_score, min_size, model_dir, execution_provider
        global processQueue, numCreatedProcs, responseTime
        with initLock:
            if getattr(self, '_initialized', False):
                return
            taskConfig = configuration.getTaskConfigurable(configFile)
            if activeInstances == 0:
                FaceRecognitionTask.enabled = taskConfig.isEnabled()
            if not FaceRecognitionTask.enabled:
                return
            if activeInstances == 0:
                extraProps = taskConfig.getConfiguration()

                def propertyValue(name, default):
                    value = extraProps.getProperty(name)
                    return default if value is None or not str(value).strip() else str(value).strip()

                numThreads = Runtime.getRuntime().availableProcessors()
                maxProcesses = int(propertyValue(numFaceRecognitionProcessesProp,
                                                max(1, min(4, numThreads // 2))))
                max_size = int(propertyValue(maxResolutionProp, 1024))
                model_name = propertyValue(insightFaceModelProp, 'auraface')
                det_size = int(propertyValue(detSizeProp, 640))
                min_det_score = float(propertyValue(minDetScoreProp, 0.5))
                min_size = int(propertyValue(minSizeProp, 48))
                execution_provider = propertyValue(executionProviderProp, 'auto')
                if model_name not in ('auraface', 'buffalo_l', 'buffalo_s'):
                    raise ValueError('Unsupported insightFaceModel: ' + model_name)
                if execution_provider not in ('auto', 'cpu', 'cuda'):
                    raise ValueError('executionProvider must be auto, cpu or cuda')
                if maxProcesses < 1 or max_size < 1 or det_size < 1 or min_size < 1:
                    raise ValueError('Face recognition process count and image sizes must be positive')
                if not math.isfinite(min_det_score) or not 0 <= min_det_score <= 1:
                    raise ValueError('minDetScore must be between 0 and 1')
                defaultPython = (os.path.join(ipedRoot, 'python', 'python')
                                 if platform.system().lower() == 'windows' else 'python3')
                bin = propertyValue(pythonPathProp, defaultPython)
                model_dir = propertyValue(modelDirProp, os.path.join(ipedRoot, 'models', 'insightface'))
                checkDependencies()
                from iped.engine.config import VideoThumbsConfig
                videoConfig = configuration.findObject(VideoThumbsConfig)
                FaceRecognitionTask.videoSubitems = videoConfig.getVideoThumbsSubitems()
                processQueue = None
                numCreatedProcs = 0
                cache.clear()
                responseTime = 0
                createProcessQueue()
            activeInstances += 1
            self._initialized = True

    # The last initialized instance owns shutdown, including a single GUI instance.
    def finish(self):
        global activeInstances, processQueue, numCreatedProcs, maxProcesses
        global responseTime
        with initLock:
            if not getattr(self, '_initialized', False):
                return
            self._initialized = False
            activeInstances -= 1
            if activeInstances:
                return
            while processQueue is not None:
                try:
                    proc = processQueue.get_nowait()
                except queue.Empty:
                    break
                stopExternalProcess(proc, graceful=True)
            with numCreatedProcsLock:
                numCreatedProcs = 0
            with timeLock:
                logger.info('[FaceRecognitionTask] Accumulated subprocess response time(s): ' + str(responseTime))
                responseTime = 0
            processQueue = None
            maxProcesses = None
            cache.clear()
            FaceRecognitionTask.enabled = None
            FaceRecognitionTask.videoSubitems = False

    def cacheResults(self, hash, locations, encodings, count):
        cache[hash + '_locations'] = locations
        cache[hash + '_encodings'] = encodings
        cache[hash + '_count'] = count

    # This function is executed on all case items
    def process(self, item):

        hash = item.getHash()
        # Only image type items are processed
        if hash is None or not item.getExtraAttribute('hasThumb'):
            return

        from iped.properties import ExtraProperties

        # Ignore small images
        try:
            width = int(item.getMetadata().get(ExtraProperties.IMAGE_META_PREFIX + 'Width'))
            height = int(item.getMetadata().get(ExtraProperties.IMAGE_META_PREFIX + 'Height'))
            if width < min_size or height < min_size:
                return
        except:
            pass

        # don't process it again (in the report generation for example)
        face_count = item.getExtraAttribute(ExtraProperties.FACE_COUNT)
        if face_count is not None:
            return

        # reuse cached results
        face_locations = cache.get(hash + '_locations')
        face_encodings = cache.get(hash + '_encodings')
        face_count = cache.get(hash + '_count')
        if face_count is not None:
            if face_count >= 0:
                item.setExtraAttribute(ExtraProperties.FACE_COUNT, face_count)
                item.setExtraAttribute(ExtraProperties.FACE_MODEL, model_name)
                if face_locations is not None and face_encodings is not None:
                    if len(face_locations) > 0 and len(face_encodings) > 0:
                        item.setExtraAttribute(ExtraProperties.FACE_LOCATIONS, face_locations)
                        item.setExtraAttribute(ExtraProperties.FACE_ENCODINGS, face_encodings)
            return

        try:
            # Get tiff:Orientation attribute
            tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
        except:
            # If item has no tiff:Orientation attribute
            tiff_orient = 1

        # Load absolute path
        img_path = None
        mediaType = item.getMediaType().toString()
        if item.getViewFile() is not None and os.path.exists(item.getViewFile().getAbsolutePath()):
            img_path = item.getViewFile().getAbsolutePath()
        elif item.hasPreview():
            from iped.engine.preview import PreviewRepositoryManager
            img_path = PreviewRepositoryManager.get(moduleDir).readPreview(item, True).getFile().getAbsolutePath()

        isVideo = False
        if mediaType.startswith('image'):
            if img_path is not None:
                tiff_orient = 1
            else:
                img_path = item.getTempFile().getAbsolutePath()
        elif mediaType.startswith('video') and not FaceRecognitionTask.videoSubitems:
            if img_path is None:
                return
            isVideo = True
        else:
            return

        proc = None
        try:
            proc = acquireExternalProcess()
            if not pingExternalProcess(proc):
                discardExternalProcess(proc)
                proc = None
                proc = acquireExternalProcess()

            print(img_path, file=proc.stdin, flush=True)
            print(video if isVideo else str(tiff_orient), file=proc.stdin, flush=True)
            t1 = time.time()
            response = readResponse(proc)
            if response is None:
                logger.info('[FaceRecognitionTask] Error loading image ' + str(item.getPath()))
                self.cacheResults(hash, [], [], -1)
                return
            face_locations, face_encodings = response
            face_encodings = list(map(javaConverter.toKnnVector, face_encodings))
            face_count = len(face_locations)
            with timeLock:
                global responseTime
                responseTime += time.time() - t1
        except Exception as exc:
            if proc is not None:
                discardExternalProcess(proc)
                proc = None
            logger.error('[FaceRecognitionTask] Face recognition failed for ' +
                         str(item.getPath()) + ': ' + str(exc))
            return
        finally:
            if proc is not None:
                processQueue.put(proc)

        if face_count:
            item.setExtraAttribute(ExtraProperties.FACE_LOCATIONS, face_locations)
            item.setExtraAttribute(ExtraProperties.FACE_ENCODINGS, face_encodings)
        item.setExtraAttribute(ExtraProperties.FACE_COUNT, face_count)
        item.setExtraAttribute(ExtraProperties.FACE_MODEL, model_name)
        self.cacheResults(hash, face_locations, face_encodings, face_count)
