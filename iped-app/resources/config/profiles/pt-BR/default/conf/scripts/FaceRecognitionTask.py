'''
# Funcionalidade de reconhecimento facial baseado no projeto Face Recognition (https://pypi.org/project/face-recognition/)
# FaceRecognitionTask.py - By Rui Sant'Ana Junior

#### Resumo
Script em python desenvolvido para funcionar com a ferramenta IPED (Indexador e processador de evidências digitais), cuja
funcionalidade é examinar cada evidência em busca de pessoas/rostos previamente selecionados, criando filtros e um bookmark
para os itens encontrados.

#### Requisitos
* TODOS os testes foram realizados sobre o SO Windows 10, no entanto, nada impede (até que se prove o contrário) que o script
  não funcione sobre outro SO.
* Python3 (todos os testes foram realizados utilizando Python 3.8.7)
* Jep 3.8.2 (pip install jep==3.8.2)
* Cmake (pip install cmake)
* Face_recognition (pip install face-recognition)
* OpenCV (pip install opencv-python)
* ATENÇÃO: É necessário incluir a jep.dll(.so) no PATH (win) ou LD_LIBRARY_PATH (Linux)

#### Como utilizar
* O script deve estar na pasta scripts, dentro do perfil que será utilizado
* A chamada do script deve ser posicionada logo após a "ParsingTask", em TaskInstaller.xml 
* Deve existir a pasta ./knownfaces/ no mesmo nível de onde está localizado o iped.exe
  - é nesta pasta que os arquivos contendo rostos ou pessoas devem ser inseridos para serem identificados nos itens processados.
  Isso deve ser feito antes de executar o iped.
  - durante o processamento um novo atributo, chamado "KnownFace-FirstMatch", é criado e pode ser utilizado como filtro na seção
  de "Metadados" (Grupo: Avançadas e Propriedade: KnownFace-FirstMatch). É uma boa prática, para cada rosto que se deseja localizar,
  criar um arquivo separado com o nome da pessoa, ou alguma outra identificação, de modo a facilitar a posterior análise através
  dos filtros já mencionados.
  - também é criado um bookmark, identificado como "Recognized Faces", contendo todos os itens que tiveram pelo menos um rosto
  identificado. Caso mais de um rosto seja identificado no mesmo item, apenas o primeiro é mostrado na propriedade KnownFace-FirstMatch.
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


# This function is executed on all case items
def process(item):

    # Only image type items are processed
    if not item.getMediaType().toString().startswith('image'):
        return

    # Faces loaded from input folder
    known_names = caseData.getCaseObject('face_model_name')
    known_face_encodings = caseData.getCaseObject('face_model_encoding')

    # Exit if there is no faces in input folder to process
    if len(known_names) == 0:
        return

    try:
        # Get tiff:Orientation attribute
        tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
        
    except:
        # If item has no tiff:Orientation attribute
        tiff_orient = 1

    # Load absolute path
    img_path = item.getTempFile().getAbsolutePath()

    # Load image to find faces in
    img = fr.load_image_file(img_path)

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

