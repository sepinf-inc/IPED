# Python task script example. It must be installed in TaskInstaller.xml to be executed.
# The script could access any item properties, extracted text, thumbs and raw bytes.
# The script can set extra item properties/columns in process() method, ignore items or create bookmarks in finish().
# You need to install jep (pip install jep) and include jep.dll(.so) in PATH or LD_LIBRARY_PATH (Linux)
# see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules


# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class PythonScriptTask:

    # Returns if this task is enabled or not. This could access options read by init() method.
    def isEnabled(self):
        return True

    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        return []

    # Do some task initialization, like reading options, custom config files or model.
    # It is executed when application starts by each processing thread on its class instance.
    # @Params
    # configuration:    configuration manager by which configurables can be retrieved after populated.
    def init(configuration):
        #print("init")
        return
    
    
    # Finish method run after processing all items in case, e.g. to clean resources.
    # It is executed by each processing thread on its class instance.
    # Objects "ipedCase" and "searcher" are provided, so case can be queried for items and bookmarks can be created, for example.
    # TODO: document methods of those objects.
    def finish(self):
        
        query = "tipo:doc"
        
        #set query into searcher
        searcher.setQuery(query)
        
        #search in case and return item ids
        ids = searcher.search().getIds()
        
        #create new bookmark and get its id
        labelId = ipedCase.getMarcadores().newLabel("DOC files")
        
        #set bookmark comment
        ipedCase.getMarcadores().setLabelComment(labelId, "Documents of DOC file format")
        
        #add item ids to created bookmark
        ipedCase.getMarcadores().addLabel(ids, labelId)
        
        #save changes
        ipedCase.getMarcadores().saveState()
    
    
    # Process an Item object. This method is executed on all case items.
    # It can access any method of Item class and store results as a new extra attribute.
    #
    #  Some Getters:
    #  String:  getName(), getExt(), getTypeExt(), getPath(), getHash(), getMediaType().toString(), getCategories() (categories separated by | )
    #  Date:    getModDate(), getCreationDate(), getAccessDate() (podem ser nulos)
    #  Boolean: isDeleted(), isDuplicate(), isDir(), isRoot(), isCarved(), isSubItem(), isTimedOut(), hasChildren()
    #  Long:    getLength()
    #  Metadata getMetadata()
    #  Object:  getExtraAttribute(String key) (returns an extra attribute)
    #  String:  getParsedTextCache() (returns item extracted text, if this task is placed after ParsingTask)
    #  File:    getTempFile() (returns a temp file with item content)
    #  BufferedInputStream: getBufferedStream() (returns an InputStream with item content)
    #
    #  Some Setters: 
    #           setToIgnore(boolean) (ignores the item and excludes it from processing and case)
    #           setAddToCase(boolean) (inserts or not item in case, after being processed: default true)
    #           addCategory(String), removeCategory(String), setMediaTypeStr(String)
    #              setExtraAttribute(key, value), setParsedTextCache(String)
    #
    def process(self, item):
        
        # Ignore item example
        if item.getExt() is not None and ".dll" in item.getExt().lower():
            item.setToIgnore(True)
        
        # Create extra attribute/column example
        if item.getParsedTextCache() is not None and ".com" in item.getParsedTextCache().lower():
            item.setExtraAttribute("containsDotCom", True)