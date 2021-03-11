'''
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

'''
Maximum number of face recognition processes to run simultaneously. You can set if you are having GPU memory problems.
'''
maxProcesses = None

numCreatedProcs = 0
numCreatedProcsLock = threading.Lock()

terminate = fp.terminate
imgError = fp.imgError
ping = fp.ping

processQueue = None
cache = {}

timeLock = threading.Lock()
detectTime = 0
featureTime = 0

def createProcessQueue(configDir):
    global processQueue, maxProcesses
    if processQueue is None:
        if maxProcesses is None:
            maxProcesses = numThreads
        processQueue = queue.Queue(maxProcesses)

# Start external process, check if it is alive and ping to test communication
def createExternalProcess(configDir):
    proc = None
    for i in range(3):
        if proc is None or proc.poll() is not None:
            proc = subprocess.Popen(['python', os.path.join(configDir, 'scripts', 'FaceRecognitionProcess.py')], stdout=subprocess.PIPE, stdin=subprocess.PIPE, universal_newlines=True)
        
        if pingExternalProcess(proc):
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
        pass
    return False

class FaceRecognitionTask:

    enabled = False
    configDir = None
    
    def isEnabled(self):
        return self.enabled
    
    # This method is executed before starting the processing of items.
    def init(self, confProps, configFolder):
        self.enabled = confProps.getProperty('enableFaceRecognition').lower() == 'true'
        self.configDir = configFolder.getAbsolutePath()
        if not self.enabled:
            return
        createProcessQueue(self.configDir)
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
        
        with timeLock:
            global detectTime, featureTime
            if detectTime + featureTime >= 0:
                logger.info('Time(s) to detect faces: ' + str(detectTime / maxProcesses))
                logger.info('Time(s) to get face features: ' + str(featureTime / maxProcesses))
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
        if hash is None or not item.getExtraAttribute('hasThumb') or not item.getMediaType().toString().startswith('image'):
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
        img_path = item.getTempFile().getAbsolutePath()
    
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
            proc = createExternalProcess(self.configDir)
            processQueue.put(proc, block=True)
        else:
            numCreatedProcsLock.release()
        
        
        try:
            proc = processQueue.get(block=True)
            if not pingExternalProcess(proc):
                proc.kill()
                proc = createExternalProcess(self.configDir)
            
            print(img_path, file=proc.stdin, flush=True)
            print(str(tiff_orient), file=proc.stdin, flush=True)
                    
            t1 = time.time()
            #face_locations = fr.face_locations(img)
            
            line = proc.stdout.readline().strip()
            if line == imgError:
                logger.info("Error loading image {} ({} bytes)", item.getPath(), item.getLength())
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