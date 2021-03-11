'''
# External process used by FaceRecognitionTask.py to do the hard work to bypass python GIL and allow multiprocess parallelization.
'''

import face_recognition as fr
import PIL
import numpy as np
import sys

PIL.ImageFile.LOAD_TRUNCATED_IMAGES = True

terminate = 'terminate_process'
imgError = "image_error"
ping = "ping"

detection_model = 'hog'
max_size = 1024

max_files = 2000
processed_files = 0

# Image rotation, when necessary
def rotateImg(img, tiff_orient):
    if tiff_orient == 8 or tiff_orient == 5:
        img = np.rot90(img, 1)
    elif tiff_orient == 3 or tiff_orient == 4:
        img = np.rot90(img, 2)
    elif tiff_orient == 6 or tiff_orient == 7:
        img = np.rot90(img, 3)
    if tiff_orient == 5 or tiff_orient == 7:
        img = np.flipup(img)
    elif tiff_orient == 2 or tiff_orient == 4:
        img = np.fliplr(img)
    return img

'''
Main function of external process which will detect and encode faces.
It is executed out of process to workaroung python GIL bottleneck.
Multiprocessing module does not work with jep-3.9.1.
'''
def main():
    while True:
        global processed_files
        if processed_files >= max_files:
            #print("Restarting face recognition process to clean possible resource leaks", file=sys.stderr)
            break
        
        line = input()
        if line == terminate:
            break
        if line == ping:
            print(ping, flush=True)
            continue
        
        processed_files += 1
        
        tiff_orient = input()
        # library default, double size of image
        upsample = 1
        scale = 1
        try:
            img = PIL.Image.open(line)
            img = img.convert('RGB')
            
            size = img.size
            if max(size[0], size[1]) * 2 > max_size:
                scale = max_size / max(size[0], size[1])
                if size[0] > size[1]:
                    new_size = (max_size, int(size[1] * scale))
                else:
                    new_size = (int(size[0] * scale), max_size)
                    
                img0 = img
                img = img.resize(new_size, resample=PIL.Image.NEAREST)
                upsample = 0
            
        except Exception:
            print(imgError, flush=True)
            continue
        
        img = np.array(img)
        img = rotateImg(img, int(tiff_orient))
                
        face_locations = fr.face_locations(img, number_of_times_to_upsample=upsample, model=detection_model)
        
        num_faces = len(face_locations)
        print(str(num_faces), flush=True)
        if num_faces == 0:
            continue
        
        for i in range(num_faces):
            if scale != 1:
                face_locations[i] = tuple(int(k / scale) for k in face_locations[i])
            print(str(face_locations[i]), flush=True)
        
        if scale != 1:
            img = np.array(img0)
            img = rotateImg(img, int(tiff_orient))
        
        face_encodings = fr.face_encodings(img, face_locations)
        
        for i in range(num_faces):
            for j in range(128):
                print(str(face_encodings[i][j]), flush=True)
    return
    
if __name__ == "__main__":
     main()
    