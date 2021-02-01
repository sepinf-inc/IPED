# Python task script example. It must be installed in TaskInstaller.xml to be executed.
# At least, getName() and process(item) functions should be implemented.
# The script could access any item properties, extracted text, thumbs and raw content.
# The script can ignore items, put them in bookmarks or set extra item properties after external processing.
# You need to install jep (pip install jep==3.8.2) and include jep.dll(.so) in PATH or LD_LIBRARY_PATH (Linux)

# Returns the script task name.
def getName():
	return ("PythonScriptTask")


# Do some task initialization, like reading options, custom config file or model.
# It is executed when application starts.
# @Params
# confProps: java Properties object with main processing options (IPEDConfig.txt)
# configFolder: java File object pointing 'conf' folder in profile folder, where a custom script config file can be created.
def init(confProps, configFolder):
	#print("init")
	return


# Finish task, maybe cleaning resources. It is executed after processing all items in case.
# Objects "ipedCase" and "searcher" are shared, so case can be queried for items and bookmarks can be created, for example.
# TODO: document methods of those objects.
def finish():
	
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


# Process an Item object. This function is executed on all case items.
# It can access any method of Item class:
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
# 		 	 setExtraAttribute(key, value), setParsedTextCache(String)
#
def process(item):
	
	# Ignore item example
	if(".dll" in item.getExt().lower()):
		item.setToIgnore(true)
	
	# Create extra attribute/column example
	if(".com" in item.getParsedTextCache().lower()):
		item.setExtraAttribute("containsDotCom", "true")