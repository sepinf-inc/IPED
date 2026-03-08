'''
# Python face recognition feature based on InsightFace (ArcFace + RetinaFace)
# FaceRecognitionTask.py - Originally by Rui Sant'Ana Junior and Luis Nassif, updated for InsightFace
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#facerecognition
# If enabled, you can search for faces from the analysis interface, check the options menu.
'''

import os
import time
import subprocess
import threading, queue
import traceback
import platform

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

# External process script
processScript = 'FaceRecognitionProcess.py'

# Maximum number of face recognition processes to run simultaneously
maxProcesses = None
numCreatedProcs = 0
numCreatedProcsLock = threading.Lock()

# Count of finish() calls — used to terminate the subprocess only on the last call
finishCount = 0
finishLock = threading.Lock()

from java.lang import Runtime, System
ipedRoot = System.getProperty('iped.root')

bin = 'python' if platform.system().lower() == 'windows' else 'python3'

model_name = 'auraface'
max_size = 1024
det_size = 640
min_det_score = 0.5
min_size = 48
model_dir = None

firstInstance = True
processQueue = None
cache = {}

timeLock = threading.Lock()
initLock = threading.Lock()
detectTime = 0
featureTime = 0

def createProcessQueue():
    global processQueue, maxProcesses
    if processQueue is None:
        if maxProcesses is None:
            numThreads = Runtime.getRuntime().availableProcessors()
            # Default to min(4, numThreads/2). Each process loads ~300MB model.
            # 4 processes balances CPU throughput vs memory usage.
            maxProcesses = int(max(1, min(4, numThreads / 2)))
        processQueue = queue.Queue(maxProcesses)

def log_stderr(proc):
    for line in iter(proc.stderr.readline, ''):
        line = line.strip()
        if line:
            logger.info("[FaceRecognitionTask] Process-" + str(proc.pid) + " stderr: " + line)
    proc.stderr.close()

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
            args_list = [bin, os.path.join(ipedRoot, 'scripts', 'tasks', processScript), str(max_size), model_name, str(det_size), str(min_det_score)]
            if model_dir:
                args_list.append(model_dir)
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
            proc.kill()
            proc = None

    raise Exception("Error creating external face recognition process! Check that '" + str(bin) + "' has insightface installed.")

def pingExternalProcess(proc):
    try:
        print(ping, file=proc.stdin, flush=True)
        line = proc.stdout.readline().strip()
        if line == ping:
            return True
    except:
        traceback.print_exc()
    return False

class FaceRecognitionTask:

    enabled = None
    videoSubitems = False

    def isEnabled(self):
        return False if FaceRecognitionTask.enabled is None else FaceRecognitionTask.enabled

    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]

    # This method is executed before starting the processing of items.
    def init(self, configuration):
        # check if face recognition task is enabled
        taskConfig = configuration.getTaskConfigurable(configFile)
        if FaceRecognitionTask.enabled is None:
            FaceRecognitionTask.enabled = taskConfig.isEnabled()

        # Check if required face recognition modules are properly installed
        # This lock avoids multiple error messages from multiple threads
        initLock.acquire()
        try:
            if not FaceRecognitionTask.enabled:
                return

            # insightface, numpy, and cv2 are only needed in the external subprocess, not in this Jep process.
            # We validate later when createExternalProcess() is called; if it fails, items are skipped
            # with an error message pointing to the install instructions.
            pass

        finally:
            initLock.release()

        from iped.engine.config import VideoThumbsConfig
        videoConfig = configuration.findObject(VideoThumbsConfig);
        FaceRecognitionTask.videoSubitems = videoConfig.getVideoThumbsSubitems();

        # check if was called from gui the first time
        global maxProcesses, firstInstance
        # load configuration properties
        extraProps = taskConfig.getConfiguration()
        numProcs = extraProps.getProperty(numFaceRecognitionProcessesProp)
        if firstInstance and numProcs is not None:
            maxProcesses = int(numProcs)
        firstInstance = False

        # configure python path: config property overrides default; on windows default is embedded pythonw
        global bin
        pythonPathVal = extraProps.getProperty(pythonPathProp)
        if pythonPathVal is not None:
            bin = pythonPathVal
        elif platform.system().lower() == 'windows':
            bin = os.path.join(ipedRoot, 'python', 'pythonw')

        maxResolution = extraProps.getProperty(maxResolutionProp)
        global max_size, model_name, det_size, min_det_score, min_size, model_dir
        if maxResolution is not None:
            max_size = int(maxResolution)
        insightFaceModel = extraProps.getProperty(insightFaceModelProp)
        if insightFaceModel is not None:
            model_name = insightFaceModel
        detSizeVal = extraProps.getProperty(detSizeProp)
        if detSizeVal is not None:
            det_size = int(detSizeVal)
        minDetScoreVal = extraProps.getProperty(minDetScoreProp)
        if minDetScoreVal is not None:
            min_det_score = float(minDetScoreVal)
        minSize = extraProps.getProperty(minSizeProp)
        if minSize is not None:
            min_size = int(minSize)
        modelDirVal = extraProps.getProperty(modelDirProp)
        if modelDirVal is not None:
            model_dir = modelDirVal
        elif model_dir is None:
            model_dir = os.path.join(ipedRoot, 'models', 'insightface')

        createProcessQueue()
        return

    # It is executed after processing all items in case.
    def finish(self):
        # Use a counter so that only the LAST worker thread actually terminates the subprocess.
        # With maxProcesses=1 there is only one proc; if an earlier thread removed it from the
        # queue and terminated it, remaining threads blocked at processQueue.get() would deadlock.
        global finishCount
        with finishLock:
            finishCount += 1
            is_last = (finishCount >= numThreads)

        if not is_last:
            return

        if not processQueue.empty():
            proc = processQueue.get(block=True)
            if proc.poll() is None:
                try:
                    print(terminate, file=proc.stdin, flush=True)
                    proc.wait(2)
                except:
                    proc.kill()
            with numCreatedProcsLock:
                global numCreatedProcs
                numCreatedProcs -= 1

        with timeLock:
            global detectTime, featureTime
            if detectTime + featureTime >= 0:
                logger.info('[FaceRecognitionTask] Time(s) to detect faces: ' + str(detectTime / maxProcesses))
                logger.info('[FaceRecognitionTask] Time(s) to get face features: ' + str(featureTime / maxProcesses))
                detectTime = -1
                featureTime = -1

    # Needed because tuples cause ClassNotFoundException on java side later
    def convertTuplesToList(self, tuples):
        result = []
        for i in tuples:
            result.append(list(i))
        return result

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

        # creates process in parallel
        numCreatedProcsLock.acquire()
        global numCreatedProcs
        if numCreatedProcs < maxProcesses:
            numCreatedProcs += 1
            numCreatedProcsLock.release()
            try:
                proc = createExternalProcess()
                processQueue.put(proc, block=True)
            except Exception as e:
                with numCreatedProcsLock:
                    numCreatedProcs -= 1
                logger.error("[FaceRecognitionTask] Subprocess creation failed — face recognition disabled for this item: " + str(e))
                return
        else:
            numCreatedProcsLock.release()

        import queue as _queue
        try:
            proc = processQueue.get(block=True, timeout=300)
        except _queue.Empty:
            logger.error("[FaceRecognitionTask] Timed out waiting for face recognition subprocess after 5 min. Check Python path and insightface installation.")
            return

        try:
            if not pingExternalProcess(proc):
                proc.kill()
                proc = createExternalProcess()

            print(img_path, file=proc.stdin, flush=True)
            if not isVideo:
                print(str(tiff_orient), file=proc.stdin, flush=True)
            else:
                print(video, file=proc.stdin, flush=True)

            t1 = time.time()

            line = proc.stdout.readline().strip()

            if not line:
                status = str(proc.poll())
                logger.warn("[FaceRecognitionTask] Unexpected error from external process while processing {} ({} bytes) exit status=" + status, item.getPath(), item.getLength())
                proc.kill()
                proc = createExternalProcess()
                return

            if line == imgError:
                logger.info("[FaceRecognitionTask] Error loading image {} ({} bytes)", item.getPath(), item.getLength())
                self.cacheResults(hash, [], [], -1)
                return

            num_faces = int(line)

            t2 = time.time()
            with timeLock:
                global detectTime
                detectTime += t2 - t1

            if num_faces == 0:
                item.setExtraAttribute(ExtraProperties.FACE_COUNT, 0)
                self.cacheResults(hash, [], [], 0)
                return

            face_locations = []
            for i in range(num_faces):
                line = proc.stdout.readline()
                face_locations.append(tuple(int(x) for x in line.strip('() \n').split(',')))

            # read all floats as a single space-separated line; use plain list (no numpy needed in Jep context)
            face_encodings = []
            for i in range(num_faces):
                enc_line = proc.stdout.readline()
                face_encodings.append([float(x) for x in enc_line.split()])

            t3 = time.time()
            with timeLock:
                global featureTime
                featureTime += t3 - t2

        finally:
            processQueue.put(proc, block=True)

        face_locations = self.convertTuplesToList(face_locations)
        face_encodings = list(map(javaConverter.toKnnVector, face_encodings))
        face_count = len(face_locations)

        item.setExtraAttribute(ExtraProperties.FACE_LOCATIONS, face_locations)
        item.setExtraAttribute(ExtraProperties.FACE_ENCODINGS, face_encodings)
        item.setExtraAttribute(ExtraProperties.FACE_COUNT, face_count)

        self.cacheResults(hash, face_locations, face_encodings, face_count)
