import os
import math
import time
import cv2 as cv
import numpy as np
from java.lang import System
import jep;
import tensorflow as tf
from SSRNET_model import SSR_net_general, SSR_net
from scipy.misc.common import face
def convertTuplesToList(tuples):
    result = []
    for i in tuples:
        result.append(list(i))
    return result

enableProp = 'enableAgeDetection'

class Predictor_6:
    
    def __init__(self,rootfolder):
        
        # Initialize gender detector
        self.gender_net = cv.dnn.readNetFromCaffe(rootfolder+'age_gender_net/deploy_gender.prototxt', rootfolder+'age_gender_net/gender_net.caffemodel')
        # Initialize age detector
        self.age_net = cv.dnn.readNetFromCaffe(rootfolder+'age_gender_net/deploy_age.prototxt', rootfolder+'age_gender_net/age_net.caffemodel')
        #size of face
        self.face_size = 227
        self.padding = 0.0
        
        self.Genders = ['Male', 'Female']
        self.Ages = ['(0-2)', '(4-6)', '(8-12)', '(15-20)', '(25-32)', '(38-43)', '(48-53)', '(60-100)']
        
    def predict(self,faces):
        # Convert faces to N,3,227,227 blob
        blob = cv.dnn.blobFromImages(faces, scalefactor=1.0, size=(self.face_size, self.face_size),
                                        mean=(78.4263377603, 87.7689143744, 114.895847746), swapRB=False)
        # Predict gender
        self.gender_net.setInput(blob)
        genders = self.gender_net.forward()
        # Predict age
        self.age_net.setInput(blob)
        ages = self.age_net.forward()
        #  Construct labels
        convGenders=[]
        convAges=[]
        for (gender, age) in zip(genders, ages):
            convGenders.append(self.Genders[gender.argmax()])
            convAges.append(self.Ages[age.argmax()])
        #labels = ['{},{}'.format(Genders[gender.argmax()], Ages[age.argmax()]) for (gender, age) in zip(genders, ages)]
        return convGenders, convAges,genders,ages


class Predictor_4:
    def __init__(self,rootfolder):
        # Setup global parameters
        self.face_size = 64
        self.padding = 0.10
        # Default parameters for SSR-Net
        self.stage_num = [3, 3, 3]
        self.lambda_local = 1
        self.lambda_d = 1
        # Initialize gender net
        self.gender_net = SSR_net_general(self.face_size, self.stage_num, self.lambda_local, self.lambda_d)()
        self.gender_net.load_weights(rootfolder+'ssrnet_gender_3_3_3_64_1.0_1.0.h5')
        # Initialize age net
        self.age_net = SSR_net(self.face_size, self.stage_num, self.lambda_local, self.lambda_d)()
        self.age_net.load_weights(rootfolder+'ssrnet_age_3_3_3_64_1.0_1.0.h5')
    def predict(self,faces):
        blob = np.empty((len(faces), self.face_size, self.face_size, 3))
        for i, face_bgr in enumerate(faces):
            blob[i, :, :, :] = cv.resize(face_bgr, (64, 64))
            blob[i, :, :, :] = cv.normalize(blob[i, :, :, :], None, alpha=0, beta=255, norm_type=cv.NORM_MINMAX)
        # Predict gender and age
        genders = self.gender_net.predict(blob)
        ages = self.age_net.predict(blob)
        gendersL=[]
        agesL=[]
        #  Construct labels
        for (gender, age) in zip(genders, ages):
            gendersL.append('Male' if (gender >= 0.5) else 'Female')
            agesL=int(age)
        return gendersL, agesL, genders, ages

class Predictor_9:

    def __init__(self,rootfolder):
        
        weight_file = rootfolder+"EfficientNetB3_224_weights.11-3.44.hdf5"
        self.padding=0.4
        self.face_size=224
        
        self.model=tf.keras.models.load_model(weight_file)
        self.Genders = ['Male', 'Female']
       
        
    def predict(self,input_faces):
        
        faces = np.empty((len(input_faces), self.face_size, self.face_size, 3))
        
        for i in range(len(faces)):
            faces[i]= cv.resize(input_faces[i], (self.face_size, self.face_size))
        
        
        # Predict gender and age
        results = self.model.predict(faces)
        
        convGenders, convAges,genders,ages=[],[],[],[]
        age_class = np.arange(0, 101).reshape(101, 1)
        for gender,age in zip(results[0],results[1]):
            convGenders.append(self.Genders[gender.argmax()])
            #convAges.append(int(age.dot(age_class)))
            convAges.append(age.argmax())
            genders.append(gender)
            ages.append(age)
        
        return convGenders, convAges,genders,ages

        
        


class AgeDetectionTask:
    
    model=None
    def isEnabled(self):
        return self.enabled
    

    def init(self, mainProps, configFolder):
        
        
        self.enabled = mainProps.getProperty(enableProp).lower() == 'true'
        
        if jep.JEP_NUMPY_ENABLED!=1:
            self.enabled
            raise "Error JEP does not support numpy"
        
        self.configDir = configFolder.getAbsolutePath()
        
        
               
        
        rootfolder= System.getProperty('iped.root')+'/models/'
        if self.model==None:
            self.model=Predictor_4(rootfolder)
        
        self.padding=self.model.padding
        
        return

    def finish(self):

        return
    
    
    def collectFaces(self,img, face_boxes):
        faces = []
        height = img.shape[0]
        width = img.shape[1]
        # Process faces
        for box in face_boxes:
            # Convert box coordinates from resized img_bgr back to original img
            box_orig = [
                int(box[3]),
                int(box[0]),
                int(box[1]),
                int(box[2]),
            ]
            
            padding_h = int(math.floor(0.5 + (box[3] - box[1]) * self.padding))
            padding_w = int(math.floor(0.5 + (box[2] - box[0]) * self.padding))
            box[0],box[1]=max(0, box[0] - padding_w), max(0, box[1] - padding_h)
            box[2], box[3] = min(box[2] + padding_w, width - 1), min(box[3] +padding_h, height - 1)
            
            # Extract face box from original frame
            face_bgr = img[
                max(0, box_orig[1]):min(box_orig[3] + 1, height - 1),
                max(0, box_orig[0]):min(box_orig[2] + 1, width - 1),
                :
            ]
            faces.append(face_bgr)
        return faces
    

    def process(self, item):
        
        # Process video
        mediaType = item.getMediaType().toString()
        if mediaType.startswith('image'):
            img_path = item.getTempFile().getAbsolutePath()
        elif mediaType.startswith('video'):
            img_path = item.getViewFile().getAbsolutePath()
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
            gendersL,agesL,genders,ages = self.model.predict(faces)
            
            item.setExtraAttribute("face_ages",agesL)
            item.setExtraAttribute("face_gender",gendersL)
            item.setExtraAttribute("face_ages_weigths",convertTuplesToList(ages))
            
                
            