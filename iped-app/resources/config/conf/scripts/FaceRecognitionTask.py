﻿'''
# Python face recognition feature based on Face Recognition Project (https://pypi.org/project/face-recognition/)
# FaceRecognitionTask.py - By Rui Sant'Ana Junior and Luis Nassif
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#facerecognition
# If enabled, you can search for faces from the analysis interface, check the options menu.
'''

#import face_recognition as fr
import os
import time
import subprocess
import numpy as np
import threading, queue
import FaceRecognitionProcess as fp
import traceback
import platform

# configuration properties
enableProp = 'enableFaceRecognition'
configFile = 'FaceRecognitionConfig.txt'
numFaceRecognitionProcessesProp = 'numFaceRecognitionProcesses'
maxResolutionProp = 'maxResolution'
faceDetectionModelProp = 'faceDetectionModel'
upSamplingProp = 'upSampling'

# External process script
processScript = 'FaceRecognitionProcess.py'

# Maximum number of face recognition processes to run simultaneously
maxProcesses = None
numCreatedProcs = 0
numCreatedProcsLock = threading.Lock()

bin = 'python'
terminate = fp.terminate
imgError = fp.imgError
ping = fp.ping

detection_model = 'hog'
max_size = 1024
up_sampling = 1

firstInstance = True
processQueue = None
cache = {}

timeLock = threading.Lock()
detectTime = 0
featureTime = 0

def createProcessQueue():
    global processQueue, maxProcesses
    if processQueue is None:
        if maxProcesses is None:
            maxProcesses = numThreads
        processQueue = queue.Queue(maxProcesses)

def log_stderr(proc):
    for line in iter(proc.stderr.readline, b''):
        line = line.strip()
        if line:
            logger.info("[FaceRecognitionTask] Process-" + str(proc.pid) + " stderr: " + line)
    proc.stderr.close()

# Start external process, check if it is alive and ping to test communication
def createExternalProcess():
    proc = None
    for i in range(3):
        if proc is None or proc.poll() is not None:
            from java.lang import System
            proc = subprocess.Popen([bin, os.path.join(System.getProperty('iped.root'), 'conf', 'scripts', processScript), str(max_size), detection_model, str(up_sampling)], 
                                    stdout=subprocess.PIPE, stdin=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
        
        if pingExternalProcess(proc):
            from threading import Thread
            t = Thread(target=log_stderr, args=(proc,))
            t.daemon = True
            t.start()
            return proc
        else:
            proc.kill()
            proc = None
            
    raise Exception("Error creating external face recognition process!")

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

    enabled = False
    
    def isEnabled(self):
        return self.enabled
    
    def getConfigurables(self):
        from dpf.sp.gpinf.indexer.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]
    
    # This method is executed before starting the processing of items.
    def init(self, configuration):
        taskConfig = configuration.getTaskConfigurable(configFile)
        self.enabled = taskConfig.isEnabled()
        if not self.enabled:
            return
        
        # check if was called from gui the first time
        global maxProcesses, firstInstance
        # load configuration properties
        extraProps = taskConfig.getConfiguration()
        numProcs = extraProps.getProperty(numFaceRecognitionProcessesProp)
        if firstInstance and numProcs is not None:
            maxProcesses = int(numProcs)
            # hides the terminal on windows gui
            if platform.system().lower() == 'windows':
                global bin
                bin = 'pythonw'
        firstInstance = False
        
        numProcs = extraProps.getProperty(numFaceRecognitionProcessesProp)
        if maxProcesses is None and numProcs is not None:
            maxProcesses = int(numProcs)
        maxResolution = extraProps.getProperty(maxResolutionProp)
        global max_size, detection_model, up_sampling
        if maxResolution is not None:
            max_size = int(maxResolution)
        faceDetectionModel = extraProps.getProperty(faceDetectionModelProp)
        if faceDetectionModel is not None:
            detection_model = faceDetectionModel
        upSampling = extraProps.getProperty(upSamplingProp)
        if upSampling is not None:
            up_sampling = int(upSampling)
        
        createProcessQueue()
        return
            
    # It is executed after processing all items in case.
    def finish(self):
        #terminate subprocess
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
    
    def cacheResults(self, hash, locations, encodings):
        cache[hash + '_locations'] = locations
        cache[hash + '_encodings'] = encodings
    
    # This function is executed on all case items
    def process(self, item):
    
        hash = item.getHash()
        # Only image type items are processed
        if hash is None or not item.getExtraAttribute('hasThumb'):
            return
        
        #reuse cached results
        face_locations = cache.get(hash + '_locations')
        face_encodings = cache.get(hash + '_encodings')
        if face_locations is not None and face_encodings is not None:
            if len(face_locations) > 0 and len(face_encodings) > 0:
                item.setExtraAttribute("face_locations", face_locations)
                item.setExtraAttribute("face_encodings", face_encodings)
                return
    
        # Load absolute path
        isVideo = False
        mediaType = item.getMediaType().toString()
        if mediaType.startswith('image'):
            img_path = item.getTempFile().getAbsolutePath()
        elif mediaType.startswith('video'):
            img_path = item.getViewFile().getAbsolutePath()
            isVideo = True
        else:
            return
    
        try:
            # Get tiff:Orientation attribute
            tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
        except:
            # If item has no tiff:Orientation attribute
            tiff_orient = 1

        
        # creates process in parallel
        numCreatedProcsLock.acquire()
        global numCreatedProcs
        if numCreatedProcs < maxProcesses:
            numCreatedProcs += 1
            numCreatedProcsLock.release()
            proc = createExternalProcess()
            processQueue.put(proc, block=True)
        else:
            numCreatedProcsLock.release()
        
        
        try:
            proc = processQueue.get(block=True)
            if not pingExternalProcess(proc):
                proc.kill()
                proc = createExternalProcess()
            
            print(img_path, file=proc.stdin, flush=True)
            if not isVideo:
                print(str(tiff_orient), file=proc.stdin, flush=True)
            else:
                print(fp.video, file=proc.stdin, flush=True)
                    
            t1 = time.time()
            #face_locations = fr.face_locations(img)
            
            line = proc.stdout.readline().strip()
            if line == imgError:
                logger.info("[FaceRecognitionTask] Error loading image {} ({} bytes)", item.getPath(), item.getLength())
                self.cacheResults(hash, [], [])
                return
                
            num_faces = int(line)
            
            t2 = time.time()
            with timeLock:
                global detectTime
                detectTime += t2 - t1
            
            if num_faces == 0:
                self.cacheResults(hash, [], [])
                return
            
            face_locations = []
            for i in range(num_faces):
                line = proc.stdout.readline()
                face_locations.append(eval(line))
            #print('locations ' + str(face_locations))
            
            #face_encodings = fr.face_encodings(img, face_locations)
            
            face_encodings = []
            for i in range(num_faces):
                list = []
                for j in range(128):
                    line = proc.stdout.readline()
                    list.append(float(line))
                np_array = np.array(list)
                face_encodings.append(np_array)
            
            #print('encodings ' + str(face_encodings))
            
            t3 = time.time()
            with timeLock:
                global featureTime
                featureTime += t3 - t2
        
        finally:
            processQueue.put(proc, block=True)
        
        face_locations = self.convertTuplesToList(face_locations)
        
        item.setExtraAttribute("face_locations", face_locations)
        item.setExtraAttribute("face_encodings", face_encodings)
        
        self.cacheResults(hash, face_locations, face_encodings)