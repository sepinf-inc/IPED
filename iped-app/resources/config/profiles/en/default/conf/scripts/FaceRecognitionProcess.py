'''
# External process used by FaceRecognitionTask.py to do the hard work to bypass python GIL and allow multiprocess parallelization.
'''

import face_recognition as fr
import cv2

terminate = 'terminate_process'
imgError = "image_error"
ping = "ping"

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
                
        face_locations = fr.face_locations(img, model="hog")
        
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
    
if __name__ == "__main__":
     main()
    