/*
 * Javascript processing task example. It must be installed in TaskInstaller.xml to be executed.
 * Must be implemented at least methods getName() and process(item).
 * Script tasks can access properties, extracted text and raw content of items. Based on that,
 * it can ignore items, set extra attributes or create bookmarks.
 */

 /* Returns the task name. */
function getName(){
	return "ExampleScriptTask";
}

/* Do some task initialization, like reading options and cofiguration files.
 * This method is executed before starting the processing of items.
 * @Params
 * confProps:    java properties file with general configuration options
 * configFolder: extra configuration folder, where task can place and load its custom configuration file
 */
function init(confProps, configFolder){
	//init code here
}

/* Finish task, maybe cleaning resources. It is executed after processing all items in case.
 * Objects "ipedCase" and "searcher" are shared, so case can be queried for items and bookmarks can be created, for example.
 * TODO: document methods of those objects.
 */
function finish(){
    //Bookmark creation example
    /*
	var query = "tipo:pdf";
	
	//set query into searcher
    searcher.setQuery(query);
    
    //search in case and return item ids
    var ids = searcher.search().getIds();
    
    //create new bookmark and get its id
    var labelId = ipedCase.getMarcadores().newLabel("PDF files");
    
    //set bookmark comment
	ipedCase.getMarcadores().setLabelComment(labelId, "Documents of PDF file format");
	
	//add item ids to created bookmark
    ipedCase.getMarcadores().addLabel(ids, labelId);
    
    //save changes
	ipedCase.getMarcadores().saveState();
	*/ 
}

/*
 * Process object "item" of EvidenceFile class. This function is executed on all case items.
 * It can access any method of EvidenceFile class:
 *
 *	Some Getters:
 *	String:  getName(), getExt(), getTypeExt(), getPath(), getHash(), getMediaType().toString(), getCategories() (categories separated by | )
 *	Date:    getModDate(), getCreationDate(), getAccessDate() (podem ser nulos)
 *  Boolean: isDeleted(), isDuplicate(), isDir(), isRoot(), isCarved(), isSubItem(), isTimedOut(), hasChildren()
 *	Long:    getLength()
 *  Metadata getMetadata()
 *  Object:  getExtraAttribute(String key) (returns an extra attribute)
 *  String:  getParsedTextCache() (returns item extracted text, if this task is placed after ParsingTask)
 *  File:    getTempFile() (returns a temp file with item content)
 *  BufferedInputStream: getBufferedStream() (returns an InputStream with item content)
 *
 *  Some Setters: 
 *           setToIgnore(boolean) (ignores the item and excludes it from processing and case)
 *           setAddToCase(boolean) (inserts or not item in case, after being processed: default true)
 *           addCategory(String), removeCategory(String), setMediaTypeStr(String)
 * 		 	 setExtraAttribute(key, value), setParsedTextCache(String)
 *
 */
function process(item){
	//Ignore item example
	/*
	if(item.getExt().equals("dll"))
	    item.setToIgnore(true);
	*/
	
	//Create attribute example
	/* WARN: searching for text in all items in case will be very slow!
	if(item.getParsedTextCache().toLowerCase().indexOf("maria da silva") != -1)
	    item.setExtraAttribute("containsMaria", "true");
	*/ 
}
