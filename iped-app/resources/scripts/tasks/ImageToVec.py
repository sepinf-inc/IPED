class ImageToVec:

    # Returns if this task is enabled or not. This could access options read by init() method.
    def isEnabled(self):
        return True

    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        return []

    def init(self, configuration):
        from img2vec_pytorch import Img2Vec
        # Initialize Img2Vec with GPU
        global img2vec
        img2vec = Img2Vec(cuda=False, model="efficientnet_b7")
        return
        
    def finish(self):
        return
        
    def convertJavaByteArray(self, byteArray):
        result =  bytes(b % 256 for b in byteArray)
        return result
    
    def loadRawImage(self, input):
        import io
        from PIL import Image
        img = Image.open(io.BytesIO(input))
        img = img.convert('RGB')
        return img
    
    def process(self, item):
    
        if not item.getMediaType().toString().startswith('image'):
            return
        
        if item.getThumb() is None or len(item.getThumb()) == 0:
            return

        # Read in an image (rgb format)
        #from PIL import Image
        #img = Image.open(item.getTempFile().getAbsolutePath())
        img = self.loadRawImage(self.convertJavaByteArray(item.getThumb()))
        
        # Get a vector from img2vec, returned as a torch FloatTensor
        vec = img2vec.get_vec(img, tensor=False)
        # Or submit a list
        #vectors = img2vec.get_vec(list_of_PIL_images)
        
        #print(vec.shape)
        
        item.setExtraAttribute("imageToVector", vec);