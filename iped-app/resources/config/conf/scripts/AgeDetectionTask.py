import os
import math
import time
import cv2 as cv
import numpy as np
from java.lang import System
import jep;
def convertTuplesToList(tuples):
    result = []
    for i in tuples:
        result.append(list(i))
    return result

enableProp = 'enableAgeDetection'
class AgeDetectionTask:
    def isEnabled(self):
        return self.enabled
    

    def init(self, mainProps, configFolder):
        
        self.enabled = mainProps.getProperty(enableProp).lower() == 'true'
        
        if jep.JEP_NUMPY_ENABLED!=1:
            self.enabled
            raise "Error JEP does not support numpy"
        
        self.configDir = configFolder.getAbsolutePath()
        
        
        # Setup global parameters
        global face_size
        global face_padding_ratio
        face_size = 227
        face_padding_ratio = 0.0
      
        
        

       
        
        global max_size, age_model, up_sampling
        
        rootfolder= System.getProperty('iped.root')+'/models/'
        
        # Initialize gender detector
        global gender_net
        gender_net = cv.dnn.readNetFromCaffe(rootfolder+'age_gender_net/deploy_gender.prototxt', rootfolder+'age_gender_net/gender_net.caffemodel')
        # Initialize age detector
        global age_net
        age_net = cv.dnn.readNetFromCaffe(rootfolder+'age_gender_net/deploy_age.prototxt', rootfolder+'age_gender_net/age_net.caffemodel')
        
        # Mean values for gender_net and age_net
        global Genders
        Genders = ['Male', 'Female']
        global Ages
        Ages = ['(0-2)', '(4-6)', '(8-12)', '(15-20)', '(25-32)', '(38-43)', '(48-53)', '(60-100)']

        return

    def finish(self):

        return
    
    
    def collectFaces(self,img, face_boxes):
        faces = []
        # Process faces
        for box in face_boxes:
            # Convert box coordinates from resized img_bgr back to original img
            box_orig = [
                int(box[3]),
                int(box[0]),
                int(box[1]),
                int(box[2]),
            ]
            # Extract face box from original frame
            face_bgr = img[
                max(0, box_orig[1]):min(box_orig[3] + 1, height - 1),
                max(0, box_orig[0]):min(box_orig[2] + 1, width - 1),
                :
            ]
            faces.append(face_bgr)
        return faces
    def predictAgeGender(self,faces):
        # Convert faces to N,3,227,227 blob
        blob = cv.dnn.blobFromImages(faces, scalefactor=1.0, size=(227, 227),
                                        mean=(78.4263377603, 87.7689143744, 114.895847746), swapRB=False)
        # Predict gender
        gender_net.setInput(blob)
        genders = gender_net.forward()
        # Predict age
        age_net.setInput(blob)
        ages = age_net.forward()
        #  Construct labels
        convGenders=[]
        convAges=[]
        for (gender, age) in zip(genders, ages):
            convGenders.append(Genders[gender.argmax()])
            convAges.append(Ages[age.argmax()])
        #labels = ['{},{}'.format(Genders[gender.argmax()], Ages[age.argmax()]) for (gender, age) in zip(genders, ages)]
        return convGenders, convAges,genders,ages


    def process(self, item):
        
        # Process video
        mediaType = item.getMediaType().toString()
        if mediaType.startswith('image'):
            img_path = item.getTempFile().getAbsolutePath()
        else:
            return
        
        img=cv.imread(img_path)
        
        global height, width
        height, width = img.shape[0:2]        
        
            
        # Detect faces
        face_boxes = item.getExtraAttribute('face_locations')
        
        if face_boxes==None:
            return;
        
        if ( len(face_boxes) > 0):
            
            # Collect all faces into matrix
            faces = self.collectFaces(img, face_boxes)
        
            # Get age and gender
            gendersL,agesL,genders,ages = self.predictAgeGender(faces)
            
            item.setExtraAttribute("face_ages",agesL)
            item.setExtraAttribute("face_gender",gendersL)
            item.setExtraAttribute("face_ages_weigths",convertTuplesToList(ages))
            
                
            