/*
 * Javascript processing task example. It must be installed in TaskInstaller.xml to be executed.
 * Must be implemented at least methods getName() and process(item).
 * Script tasks can access properties, extracted text and raw content of items. Based on that,
 * it can ignore items, set extra attributes or create bookmarks.
 */

function getName(){
	return "IgnoreFilesByPathTask";
}

function init(confProps, configFolder){}

function finish(){}

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
function process(e){

    //Verifies the path of items and ignores those in system or irrelevant places
	var path = e.getPath().toLowerCase();
	
	if(/((\\|\/)vol_vol.|sd..)(\\|\/)(windows|windows\....|winnt|win98|win2000|wutemp|swsetup|Windows Update Setup Files|msdownld\.tmp|intel|AMD|nvidia|nvidia corporation|perflogs|System Volume Information|Recovery|System Recovery|windows\.old|windows\.old\(.\)|Windows10Upgrade|MSOCache|Windows\~BT|Windows\.\~BT$|Library|lib|lib64|Developer|Network|System|sys|bin|dev|sbin|com\.apple\.boot\.S|Perl|boot|bootmgr|Cygwin|Cygwin64|MinGW|python..|SYSTEM\.SAV|SWSETUP|SDK|Config\.msi|WINDOWS\.\~WS|BOOTNXT|bootsect|DRIVERS|DRIVER|EFI|\$Extend|\$GetCurrent|\$SysReset|\$Unalloc|HDDRecovery|Dell|hp|sony|samsung|Preload|\.fseventsd|HFS\+ Private|\.Spotlight-V100|WindowsImages|MSI.....\.tmp|AdwCleaner|\$AV_ASW|(Arquivos de Programas$|Arquivos de Programas \(x86\)|Program Files|Program Files \(x86\))(\\|\/)(7-Zip|Adobe|Avast|Avast Software|Common Files|DVD Maker|Dolby Digital Plus|DIFX|ffdshow|Foxit Software|GBPlugin|Google|HP|InstallShield Installation Information|Integrated Camera|ISO Creator .\..|Intel|Intel Corporation|internet explorer|IrfanView|Java|Lenovo|Lenovo Registration|LibreOffice .|Microsoft Office|Microsoft Skydrive|Microsoft\.NET|MSBuild|MSECache|Mozilla Firefox|Mozilla Maintenance Service|nvidia|nvidia corporation|PDFCreator|Realtek|Reference Assemblies|rempl|Synaptics|ThinkPad|Uninstall Information|VLC|Windows Defender|Windows Defender Advanced Threat Protection|Windows Mail|Windows Media Player|Windows Multimedia Platform|Windows nt|Windows Photo Viewer|Windows Portable Devices|Windows Security|Windows Sidebar|WindowsPowerShell|WindowsApps|Winzip))(\\|\/)/i.test(path))
	{
		e.setToIgnore(true);
	}

}
