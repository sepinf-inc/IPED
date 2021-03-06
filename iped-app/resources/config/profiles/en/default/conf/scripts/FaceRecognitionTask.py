'''
# Python face recognition funcionality based on Face Recognition Project (https://pypi.org/project/face-recognition/)
# FaceRecognitionTask.py - By Rui Sant'Ana Junior

#### Summary
Python script developed to be executed within the IPED tool (Digital Evidence Processor and Indexer - translated
from Portuguese), whose functionality is to examine each evidence in search of selected people/faces, creating
filters and a bookmark for founded items.

#### Requirements
* ALL tests were performed on Windows 10, however, nothing prevents (until proven otherwise) that the script does not
  work on another OS.
* Python3 (all tests were performed using Python 3.8.7)
* Jep 3.8.2 (pip install jep == 3.8.2)
* It is necessary to include jep.dll (.so) in the PATH (win) or LD_LIBRARY_PATH (Linux)
* Cmake (pip install cmake)
* Face_recognition (pip install face_recognition)
* OpenCV (pip install opencv-python)

#### How to run
* This script must be in the scripts folder, within the profile that will be used
* The script call must be placed right after "ParsingTask", in TaskInstaller.xml
* The ./knownfaces/ folder must exist on the same level as the iped.exe is located
  - in this folder, files containing faces or people must be inserted in order to be identified in processed items. This
  must be done before running iped.
  - during the processing, a new attribute, called "KnownFace-FirstMatch", is created and can be used as a filter in the
  "Metadata" section (Group: Advanced  and Property: KnownFace-FirstMatch). It is a good practice, for each face you want
  to find, to create a separate file with the person's name, or some other identification, in order to facilitate the later
  analysis through the filters already mentionated.
  - a bookmark is also created, identified as "Recognized Faces", containing all items that had at least one face identified.
  If more than one face is identified in the same item, only the first is included in the KnownFace-FirstMatch property.
'''

import face_recognition as fr
import os
import face_recognition.api as face_recognition
import cv2

# Known faces must be placed in this folder
knownfacefolder = './knownfaces/'

# Auxiliary function
def scan_known_people(known_people_folder):
    known_names = []
    known_face_encodings = []

    for file in image_files_in_folder(known_people_folder):
        basename = os.path.splitext(os.path.basename(file))[0]
        img = face_recognition.load_image_file(file)
        encodings = face_recognition.face_encodings(img)

        if len(encodings) > 1:
            logger.warn("Face found in {} alredy included! Only considering the first face.".format(file))

        if len(encodings) == 0:
            logger.warn("No faces found in {}. Ignoring file.".format(file))
            
        else:
            known_names.append(basename)
            known_face_encodings.append(encodings[0])

    return known_names, known_face_encodings

# Auxiliary function
def image_files_in_folder(folder):
    try:
        return [os.path.join(folder, f) for f in os.listdir(folder)]
    
    except FileNotFoundError:
        logger.error("Folder {} doesnt exist.".format(folder))
        return []
        

# Returns the task name.
def getName():
    return ("FaceRecognitionTask")


# This method is executed before starting the processing of items.
def init(confProps, configFolder):

    if caseData.getCaseObject('face_model_name') is None:
        logger.info("Reading known faces from {}".format(knownfacefolder))

        # Read Known faces
        known_names, known_face_encodings = scan_known_people(knownfacefolder)

        logger.info("Faces found in {} are being considered.".format(known_names))

        caseData.putCaseObject('face_model_name', known_names)
        caseData.putCaseObject('face_model_encoding', known_face_encodings)

    return


# It is executed after processing all items in case.
def finish():

    query = "KnownFace:true"

    #set query into searcher
    searcher.setQuery(query)

    #search in case and return item ids
    ids = searcher.search().getIds()

    if len(ids) > 0:
        #create new bookmark and get its id
        labelId = ipedCase.getMarcadores().newLabel("Recognized Faces")

        #set bookmark comment
        ipedCase.getMarcadores().setLabelComment(labelId, "Person selected for automatic face recognition")

        #add item ids to created bookmark
        ipedCase.getMarcadores().addLabel(ids, labelId)

        #save changes
        ipedCase.getMarcadores().saveState()

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

    try:
        # Get tiff:Orientation attribute
        tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
        
    except:
        # If item has no tiff:Orientation attribute
        tiff_orient = 1

    # Load absolute path
    img_path = item.getTempFile().getAbsolutePath()

    img = None
    try:
        # Load image to find faces in
        img = fr.load_image_file(img_path)
        
    except:
        logger.info("Error loading image {}", item.getPath())
        return

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
    
    # Faces loaded from input folder
    known_names = caseData.getCaseObject('face_model_name')
    known_face_encodings = caseData.getCaseObject('face_model_encoding')
    
    # Exit if there is no faces in input folder to process
    if len(known_names) == 0:
        return

    # Loop through faces in image
    for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
        matches = fr.compare_faces(known_face_encodings, face_encoding)

        name = "Unknown Person"

        # if matches
        if True in matches:
            first_match_index = matches.index(True)
            name = known_names[first_match_index]
            item.setExtraAttribute("KnownFace", "true")
            item.setExtraAttribute("KnownFace-FirstMatch", name)

