'''
# Python face recognition feature based on Face Recognition Project (https://pypi.org/project/face-recognition/)
# FaceRecognitionTask.py - By Rui Sant'Ana Junior and Luis Nassif
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#facerecognition
# If enabled, you can search for faces from the analysis interface, check the options menu.
'''

import face_recognition as fr
import os
import face_recognition.api as face_recognition
import cv2
import time
import subprocess
import numpy as np

enabled = False
cache = None
detectTime = 0
featureTime = 0
configDir = None
proc = None
terminate = 'terminate_process'
imgError = "image_error"
ping = "ping"

'''
Main function of external process which will detect and encode faces.
It is executed out of process to workaroung python GIL bottleneck.
Multiprocessing module does not work with jep-3.9.1.
'''
def main():
    while True:
        line = input()
        if line == terminate:
            break
        if line == ping:
            print(ping, flush=True)
            continue
        
        tiff_orient = input()        
        try:
            img = fr.load_image_file(line)
        except:
            print(imgError, flush=True)
            continue
        
        img = rotateImg(img, int(tiff_orient))
                
        face_locations = fr.face_locations(img)
        
        num_faces = len(face_locations)
        print(str(num_faces), flush=True)
        if num_faces == 0:
            continue
        
        for i in range(num_faces):
            print(str(face_locations[i]), flush=True)
        
        face_encodings = fr.face_encodings(img, face_locations)
        
        for i in range(num_faces):
            for j in range(128):
                print(str(face_encodings[i][j]), flush=True)
    return

def isEnabled():
    return enabled

# This method is executed before starting the processing of items.
def init(confProps, configFolder):
    global enabled, configDir, cache
    enabled = confProps.getProperty('enableFaceRecognition').lower() == 'true'
    if not enabled:
        return
    configDir = configFolder.getAbsolutePath()
    cache = caseData.getCaseObject('faces_cache')
    if cache is None:
        from java.util.concurrent import ConcurrentHashMap
        cache = ConcurrentHashMap()
        caseData.putCaseObject('faces_cache', cache)
    return

# Start external process, check if it is alive and ping to test communication
def checkExternalProcess():
    global proc
    for i in range(3):
        if proc is None or proc.poll() is not None:
            proc = subprocess.Popen(['python', os.path.join(configDir, 'scripts', 'FaceRecognitionTask.py')], stdout=subprocess.PIPE, stdin=subprocess.PIPE, universal_newlines=True)
        
        print(ping, file=proc.stdin, flush=True)
        line = proc.stdout.readline().strip()
        if line == ping:
            return True
        else:
            proc.kill()
            proc = None
            
    return False

# It is executed after processing all items in case.
def finish():
    #terminate subprocess
    if proc is not None:
        try:
            print(terminate, file=proc.stdin, flush=True)
            proc.wait(2)
        except:
            proc.kill()
    
    times = caseData.getCaseObject('face_recog_times')
    if times is None:
        times = [0, 0]
    times[0] += detectTime / numThreads
    times[1] += featureTime / numThreads
    caseData.putCaseObject('face_recog_times', times)
    
    if worker.id == numThreads - 1:
        logger.info('Time(s) to detect faces: ' + str(times[0]))
        logger.info('Time(s) to get face features: ' + str(times[1]))

# Needed because tuples cause ClassNotFoundException on java side later
def convertTuplesToList(tuples):
    result = []
    for i in tuples:
        result.append(list(i))
    return result

# Image rotation, when necessary
def rotateImg(img, tiff_orient):
    if tiff_orient != 1 and tiff_orient != 2:
        if tiff_orient == 8 or tiff_orient == 5:
            img = cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)
        elif tiff_orient == 3 or tiff_orient == 4:
            img = cv2.rotate(img, cv2.ROTATE_180)
        elif tiff_orient == 6 or tiff_orient == 7:
            img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)        
    return img

def cacheResults(item, locations, encodings):
    cache.put(item.getHash() + '_locations', locations)
    cache.put(item.getHash() + '_encodings', encodings)

# This function is executed on all case items
def process(item):

    # Only image type items are processed
    if item.getHash() is None or not item.getMediaType().toString().startswith('image'):
        return
    
    #reuse cached results
    face_locations = cache.get(item.getHash() + '_locations')
    face_encodings = cache.get(item.getHash() + '_encodings')
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
    
    
    if checkExternalProcess() == False:
        logger.error("Error pinging external face recognition process")
        return
    
    print(img_path, file=proc.stdin, flush=True)
    print(str(tiff_orient), file=proc.stdin, flush=True)
            
    t1 = time.time()
    #face_locations = fr.face_locations(img)
    
    line = proc.stdout.readline().strip()
    if line == imgError:
        logger.info("Error loading image {} ({} bytes)", item.getPath(), item.getLength())
        cacheResults(item, [], [])
        return
        
    num_faces = int(line)
    if num_faces == 0:
        cacheResults(item, [], [])
        return
    
    face_locations = []
    for i in range(num_faces):
        line = proc.stdout.readline()
        face_locations.append(eval(line))
    #print('locations ' + str(face_locations))
    
    t2 = time.time()
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
    
    global detectTime, featureTime
    detectTime += t2 - t1
    featureTime += t3 - t2
    
    face_locations = convertTuplesToList(face_locations)
    
    item.setExtraAttribute("face_locations", face_locations)
    item.setExtraAttribute("face_encodings", face_encodings)
    
    cacheResults(item, face_locations, face_encodings)
    
    
if __name__ == "__main__":
    main()
    