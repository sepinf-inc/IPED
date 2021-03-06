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

enabled = False

def isEnabled():
    return enabled

# This method is executed before starting the processing of items.
def init(confProps, configFolder):
    global enabled
    enabled = confProps.getProperty('enableFaceRecognition').lower() == 'true'
    return

# It is executed after processing all items in case.
def finish():
    return

def convertTuplesToList(tuples):
    result = []
    for i in tuples:
        result.append(list(i))
    return result

# This function is executed on all case items
def process(item):

    # Only image type items are processed
    if not item.getMediaType().toString().startswith('image'):
        return

    # Load absolute path
    img_path = item.getTempFile().getAbsolutePath()

    try:
        # Load image to find faces in
        img = fr.load_image_file(img_path)
        
    except:
        logger.info("Error loading image {}", item.getPath())
        return

    try:
        # Get tiff:Orientation attribute
        tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
        
    except:
        # If item has no tiff:Orientation attribute
        tiff_orient = 1

    # Image rotation, when necessary
    if tiff_orient != 1 and tiff_orient != 2:
        if tiff_orient == 8 or tiff_orient == 5:
            img = cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)
        elif tiff_orient == 3 or tiff_orient == 4:
            img = cv2.rotate(img, cv2.ROTATE_180)
        elif tiff_orient == 6 or tiff_orient == 7:
            img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
            

    # Find faces in image
    face_locations = fr.face_locations(img)
    face_encodings = fr.face_encodings(img, face_locations)
    
    item.setExtraAttribute("face_locations", convertTuplesToList(face_locations))
    item.setExtraAttribute("face_encodings", face_encodings)
    