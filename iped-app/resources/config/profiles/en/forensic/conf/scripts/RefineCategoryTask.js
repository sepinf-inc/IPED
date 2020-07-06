/*
 * Script of Category Specialization based on item properties.
 * Uses javascript language to allow flexibility in definitions.
 */

/* Name of processing task
*/
function getName(){
	return "RefineCategoryTask";
}

function init(confProps, configFolder){}

function finish(){}

/*
 * Changes category of items based on their properties
 *
 */
function process(e){

	var categorias = e.getCategories();
	var length = e.getLength();
	var ext = e.getExt().toLowerCase();

	if(length == 0)
		e.addCategory("Empty Files");

	if(e.getExt().toLowerCase().equals("mts")){
		e.setMediaTypeStr("video/mp2t");
		e.setCategory("Videos");
	}
	
	var mime = e.getMediaType().toString();
	if(mime.indexOf("x-ufed-") != -1 && categorias.indexOf("Other files") != -1){
		var cat = mime.substring(mime.indexOf("x-ufed-") + 7);
		cat = cat.substring(0, 1).toUpperCase() + cat.substring(1); 
		e.setCategory(cat);
	}
	
	if(mime.equals("application/dita+xml") && e.getName().equals("com.whatsapp_preferences.xml")){
		e.setMediaTypeStr("application/x-whatsapp-user-xml");
		e.setCategory("Contacts");
	}
	
	if(categorias.indexOf("Images") > -1){

		if(isFromInternet(e))
			e.setCategory("Temporary Internet Images");

		else if(inSystemFolder(e))
			e.setCategory("Images in System Folders");
			
		else
			e.setCategory("Other Images");
	}

	else if(categorias.indexOf("Plain Texts") > -1){

		if(isFromInternet(e))
			e.setCategory("Temporary Internet Texts");

		else if(inSystemFolder(e))
			e.setCategory("Texts in System Folders");

		else if (ext.equals("url"))
			e.setCategory("URL links");

		else
			e.setCategory("Other Texts");
	}
	
	else if(isFromInternet(e)){
		if(e.getMediaType().toString().equals("application/x-sqlite3"))
			e.setCategory("Internet History");
	}
    
    else if(categorias.indexOf("Other files") > -1){
		var ext = e.getExt().toLowerCase();
		
		if (ext.equals("url"))
			e.setCategory("URL links");
			
		else if (ext.equals("plist")) {
			var path = e.getPath().toLowerCase();
			if (path.indexOf("/safari/") > -1) {
				var nome = e.getName().toLowerCase();
				if (nome.indexOf("history") > -1 || 
	 			   nome.indexOf("downloads") > -1 ||
	  			   nome.indexOf("lastsession") > -1 ||
	   			   nome.indexOf("topsites") > -1 || 
				   nome.indexOf("bookmarks") > -1)
					e.setCategory("Internet History");
			}
		} else {
			var nome = e.getName().toLowerCase();
			if (e.getPath().toLowerCase().indexOf("/chrome/user data/") > -1) {
				if (nome.indexOf(" session") > -1 || 
	 			   nome.indexOf(" tabs") > -1 ||
	  			   nome.indexOf("visited links") > -1 ||
	   			   nome.indexOf("history") > -1 || 
				   nome.indexOf("journal") > -1)
					e.setCategory("Internet History");
			}
		}
	}

	if(inRecycle(e)){
		e.addCategory("Windows Recycle");
		if(e.getName().indexOf("$I") == 0)
			e.setMediaTypeStr("application/x-recyclebin");
		else if(e.getName().equals("INFO2"))
			e.setMediaTypeStr("application/x-info2");
	}
		
		
	var nome = e.getName().toLowerCase();
	var ext = e.getExt().toLowerCase();
	var path = e.getPath().toLowerCase();


	//Arquivos das pastas de backup do iPhone. Devem ser processados depois por ferramenta específica, como Physical Analyser
	if ((path.indexOf("/application data/apple computer/mobilesync/backup") > -1)||
		(path.indexOf("/appdata/roaming/apple computer/mobilesync/backup") > -1)||
		(path.indexOf("/appdata/roaming/apple computer/mobilesyncbackup") > -1)||
		(path.indexOf("/biblioteca/suporte a aplicativos/mobilesync/backup") > -1)||
		(path.indexOf("/library/application support/mobilesync/backup") > -1)
		)
		e.addCategory("iPhone Backup");
		
	
	
	//Arquivos de instalação do Torchat
	if (path.indexOf("torchat/") !== -1){
		e.addCategory("Torchat");
		e.addCategory("Tor");
	}
		
	//Arquivos relacionados ao Tor, TorBrowser e OperaTor
	if ((path.indexOf("torbrowser/") !== -1)||
		(nome.equals("tor.exe"))||
		(nome.equals("operator.exe"))||
		(nome.equals("start-tor-browser.desktop"))||
		(nome.equals("tor-resolve.exe"))
		)
		e.addCategory("Tor");
	
	
	//Programas de armazenamento na nuvem e suas pastas default de armazenamento local
	if (((path.indexOf("megasync/") !== -1)||
		(path.indexOf("dropbox/") !== -1)||
		(path.indexOf("com.getdropbox") !== -1)||
		(path.indexOf("onedrive/") !== -1)||
		(path.indexOf("skydrive/") !== -1)||
		(path.indexOf("google drive/") !== -1)||
		(path.indexOf("/my drive/") !== -1)||
		(path.indexOf("amazon drive/") !== -1)||
		(path.indexOf("cloud drive/") !== -1)||
		(path.indexOf("box sync/") !== -1)||
		(path.indexOf("mediafire/") !== -1)||
		(path.indexOf("mediafire desktop/") !== -1)||
		(path.indexOf("bittorrent sync/") !== -1)||
		(path.indexOf("resilio sync/") !== -1)||
		(path.indexOf("tresorit/") !== -1)||
		(path.indexOf("/my tresors/") !== -1)||
		(path.indexOf("\'s tresor/") !== -1)||
		(path.indexOf("/library/application support/icloud/accounts/") !== -1)||
		(path.indexOf("/library/preferences/mobilemeaccounts.plist") !== -1)||		
		(nome.indexOf("dropbox.exe") !== -1)||
		(nome.indexOf("megasync.exe") !== -1)||
		(nome.indexOf("onedrive.exe") !== -1)||
		(nome.indexOf("skydrive.exe") !== -1)||
		(nome.indexOf("googledrivesync.exe") !== -1)||
		(nome.indexOf("amazondrive.exe") !== -1)||
		(nome.indexOf("amazonclouddrive.exe") !== -1)||
		(nome.indexOf("boxsync.exe") !== -1)||
		(nome.indexOf("mediafire desktop.exe") !== -1)||
		(nome.indexOf("mf_watch.exe") !== -1)||
		(nome.indexOf("resilio-sync.exe") !== -1)||
		(nome.indexOf("resilio sync.exe") !== -1)||
		(nome.indexOf("resiliosync.exe") !== -1)||
		(nome.indexOf("resilio sync.app") !== -1)||
		(nome.indexOf("resilio-sync_x64.exe") !== -1)||
		(nome.indexOf("btsync.exe") !== -1)||
		(nome.indexOf("tresorit.exe") !== -1))&&((path.indexOf("/appdata/local") == -1)&&(path.indexOf("/appdata/locallow") == -1)&&(path.indexOf("/appdata/roaming") == -1)&&(path.indexOf("/programdata/") == -1)&&(path.indexOf("/desktop.ini") == -1))
		)
		e.addCategory("Cloud Drives");
	

	//Programas peer-to-peer
	if ((path.indexOf("/Roaming/Shareaza/Data") !== -1)	||
		(nome.indexOf("Shareaza.db3") !== -1)
		)
		e.addCategory("Peer-to-peer");
	
		
	//Telegram
	if (nome.equals("translit.cache") === true){
		e.addCategory("Telegram");
		e.addCategory("Contacts");
	}
	if ((path.indexOf("ph.telegra.telegraph") !== -1))	{
		e.addCategory("Telegram");	
	}
}


/*
 *  Auxiliar function
 */
function isFromInternet(e){
	
	var path = e.getPath();
	
	return 	path.indexOf("Temporary Internet") > -1 || 
		path.indexOf("/Microsoft/Windows/INetCache") > -1 ||
		path.indexOf("/Microsoft/Windows/INetCookies") > -1 ||
		path.indexOf("Chrome/User Data/") > -1 ||
		path.indexOf("Mozilla/Firefox/Profiles") > -1 || 
		path.indexOf("Apple Computer/Safari") > -1 ||
		path.indexOf("/Library/Safari") > -1 ||
		path.indexOf("/Library/Caches/com.apple.Safari/") > -1 ||
		path.indexOf("/Library/Caches/Metadata/Safari") > -1 ||
		path.indexOf("/Library/Cookies/Cookies.binarycookies") > -1 ||
		path.indexOf("/Library/Preferences/com.apple.Safari") > -1 ||
		path.indexOf("chromium/Default/Cache") > -1 ||
		path.indexOf("cache/mozilla/firefox") > -1 || 
		path.indexOf("/Library/Application Support/Firefox/") > -1 || 
		path.indexOf("/Cookies/") > -1;
}


/*
 *  Auxiliar function
 */
function inSystemFolder(e){

	var path = e.getPath().toLowerCase();
	var idx = path.indexOf("/windows/");

	return	(idx > -1 && idx - path.indexOf("/vol_vol") - 8 <= 2) ||
		path.indexOf("arquivos de programas") > -1 ||
		path.indexOf("system volume information") > -1 ||
		path.indexOf("program files") > -1 || path.indexOf("/windows.old") > -1;

}

/*
 *  Auxiliar function
 */
function inRecycle(e){
	var path = e.getPath().toLowerCase();
	return 	path.indexOf("$recycle.bin") > -1 || path.indexOf("/recycler/") > -1 || path.indexOf("\\recycler\\") > -1;
}