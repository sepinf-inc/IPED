/*
 * Javascript processing task example. It must be installed in TaskInstaller.xml to be executed.
 * Must be implemented at least methods getName() and process(item).
 * Script tasks can access properties, extracted text and raw content of items. Based on that,
 * it can ignore items, set extra attributes or create bookmarks.
 */

 /* Returns the task name. */
function getName(){
	return "FilterItems";
}

/*
 * Returns
            an optional list of configurable objects that can load/save parameters from/to config files. 
 */
function getConfigurables(){
	return null;
}

/* Do some task initialization, like reading options and cofiguration params.
 * This method is executed before starting the processing of items.
 * It is executed by each processing thread.
 * @Params
 * configuration:    configuration manager by which configurables could be retrieved after populated.
 */
function init(configuration){
	//init code here
}

/* Finish task, maybe cleaning resources. It is executed after processing all items in case.
 * It is executed by each processing thread.
 * Objects "ipedCase" and "searcher" are shared, so case can be queried for items and bookmarks can be created, for example.
 * TODO: document methods of those objects.
 */
function finish(){
}

/*
 * Process object "item" of EvidenceFile class. This function is executed on all case items.
 * It can access any method of EvidenceFile class:
 *
 *	Some Getters:
 *	String:  getName(), getExt(), getType(), getPath(), getHash(), getMediaType().toString(), getCategories() (categories separated by | )
 *	Date:    getModDate(), getCreationDate(), getAccessDate() (podem ser nulos)
 *  Boolean: isDeleted(), isDir(), isRoot(), isCarved(), isSubItem(), isTimedOut(), hasChildren()
 *	Long:    getLength()
 *  Metadata getMetadata()
 *  Object:  getExtraAttribute(String key) (returns an extra attribute)
 *  String:  getParsedTextCache() (returns item extracted text, if this task is placed after ParsingTask)
 *  File:    getTempFile() (returns a temp file with item content)
 *  BufferedInputStream: getBufferedInputStream() (returns an InputStream with item content)
 *
 *  Some Setters: 
 *           setToIgnore(boolean) (ignores the item and excludes it from processing and case)
 *           setAddToCase(boolean) (inserts or not item in case, after being processed: default true)
 *           addCategory(String), removeCategory(String), setMediaTypeStr(String)
 * 		 	 setExtraAttribute(key, value), setParsedTextCache(String)
 *
 */
function process(item){
    //use set to Ignore to filter this item from following processing tasks
	if(item.getExt().equals("dll")){
        item.setToIgnore(true);
    }
}
