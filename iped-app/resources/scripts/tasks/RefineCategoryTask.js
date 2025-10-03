/*
 * Script of Category Specialization based on item properties.
 * Uses javascript language to allow flexibility in definitions.
 */

/* Name of processing task
*/
function getName(){
	return "RefineCategoryTask";
}

function getConfigurables() {}

function init(configuration) {}

function finish(){}

/*
 * Changes category of items based on their properties
 *
 */
function process(e){

	var categorias = e.getCategories();
	var length = e.getLength();
	var ext = e.getExt().toLowerCase();
	var mime = e.getMediaType().toString();
	var name = e.getName().toLowerCase();
	var rawPath = e.getPath();
	var path = e.getPath().toLowerCase().replace(/\\/g, "/");
	
	// Workaround for Tika limitation: https://github.com/sepinf-inc/IPED/issues/1793
	if(mime.equals("application/vnd.apple.unknown.13")){
		if(ext.equals("pages")){
			e.setType("pages");
			e.setMediaTypeStr("application/vnd.apple.pages.13");
			e.setCategory("Text Documents");
		} else if(ext.equals("numbers")){
			e.setType("numbers");
			e.setMediaTypeStr("application/vnd.apple.numbers.13");
			e.setCategory("Spreadsheets");
		} else if(ext.equals("key")){
			e.setType("key");
			e.setMediaTypeStr("application/vnd.apple.keynote.13");
			e.setCategory("Presentations");
		}
	}
	
	if(e.getMetadata().get("chromeCache:isChromeCacheEntry")){
		e.addCategory("Chrome Cache");
	}
    cacheUrl = e.getMetadata().get("chromeCache:chromeCacheUrl");
    if(cacheUrl != null && cacheUrl.contains("discord")){
        if(e.getName().startsWith("messages")){
            e.setMediaTypeStr("application/x-discord-chat+json")
        }
        if(e.getName().contains("@me")){
            e.setMediaTypeStr("application/x-discord-account")
        }            
    }
	
	if(/.*(-delta|-flat|-(f|s)[0-9]{3})\.vmdk$/i.test(e.getName())){
	    e.setMediaTypeStr("application/x-vmdk-data");
	}
	
	if("application/x-disk-image".equals(mime) && (ext.equals("dd") || ext.equals("000") || ext.equals("001"))){
	    e.setMediaTypeStr("application/x-raw-image");
	}

	if(ext.toLowerCase().equals("mts")){
		e.setMediaTypeStr("video/mp2t");
		e.setCategory("Videos");
	}
	
	if(mime.indexOf("x-ufed-") != -1 && categorias.indexOf("Other files") != -1){

		var cat;
		rawPathParts = rawPath.split('/');
		anchorIndex = rawPathParts.indexOf("_DecodedData");
		if (anchorIndex !== -1 && anchorIndex < rawPathParts.length - 1) {
			cat = rawPathParts[anchorIndex + 1].replace(/([a-z])([A-Z])/g, '$1 $2'); // split modelType using camel case
		} else {
			cat = mime.substring(mime.indexOf("x-ufed-") + 7);
			cat = cat.substring(0, 1).toUpperCase() + cat.substring(1);
		}
		e.setCategory(cat);
	}

	if(mime.equals("application/x-ufed-attachment")) {
		if (rawPath.indexOf("/InstantMessage/") > -1 || rawPath.indexOf("/Chat/") > -1) {
			e.setCategory("Message Attachments");
		} else if (rawPath.indexOf("/Email/") > -1) {
			e.setCategory("Email Attachments");
		} else if (rawPath.indexOf("/SocialMediaActivity/") > -1) {
			e.setCategory("Social Media Activities");
		} else if (rawPath.indexOf("/Note/") > -1) {
			e.setCategory("Notes");
		} else if (rawPath.indexOf("/CalendarEntry/") > -1) {
			e.setCategory("Calendar");
		} else if (rawPath.indexOf("/Notification/") > -1) {
			e.setCategory("Notifications");
		}
	}

	if(path.indexOf("whatsapp") != -1 && mime.equals("application/dita+xml") &&
		(e.getName().equals("com.whatsapp_preferences.xml") || 
		 e.getName().equals("com.whatsapp_preferences_light.xml") ||
		 e.getName().equals("com.whatsapp.w4b_preferences.xml") || 
		 e.getName().equals("com.whatsapp.w4b_preferences_light.xml") ||
		 e.getName().equals("registration.RegisterPhone.xml") ||
		 e.getName().equals("startup_prefs.xml"))) {
		e.setMediaTypeStr("application/x-whatsapp-user-xml");
		e.setCategory("User Accounts");
	}

	if(mime.equals("application/dita+xml") && e.getName().equals("userconfing.xml")){
		e.setMediaTypeStr("application/x-telegram-user-conf");
		//e.setCategory("Contacts");
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
		
		if (ext.equals("url"))
			e.setCategory("URL links");
			
		else if (ext.equals("plist")) {
			if (path.indexOf("/safari/") > -1) {
				if (name.indexOf("history") > -1 || 
	 			   name.indexOf("downloads") > -1 ||
	  			   name.indexOf("lastsession") > -1 ||
	   			   name.indexOf("topsites") > -1 || 
				   name.indexOf("bookmarks") > -1)
					e.setCategory("Internet History");
			}
		} else {
			if (e.getPath().toLowerCase().indexOf("/chrome/user data/") > -1) {
				if (name.indexOf(" session") > -1 || 
	 			   name.indexOf(" tabs") > -1 ||
	  			   name.indexOf("visited links") > -1 ||
	   			   name.indexOf("history") > -1 || 
				   name.indexOf("journal") > -1)
					e.setCategory("Internet History");
			}
		}
	}

	// Calls sub-categories
	if (mime.equals("application/x-ufed-call")) {
		source = e.getMetadata().get("ufed:Source");
		if (source == null) {
			e.setCategory("Phone Calls");
		} else {
			source = source.toLowerCase();
			if (source.contains("whatsapp")) {
				e.setCategory("WhatsApp Calls");
			} else if (source.contains("facebook")) {
				e.setCategory("Facebook Calls");
			} else if (source.contains("discord")) {
				e.setCategory("Discord Calls");
			} else if (source.contains("threema")) {
				e.setCategory("Threema Calls");
			} else if (source.contains("telegram")) {
				e.setCategory("Telegram Calls");
			} else if (source.contains("signal")) {
				e.setCategory("Signal Calls");
			} else {
			    // New sub-categories may be created from other phone call apps handled by UFED
				e.setCategory("Other Calls");
			}
		}
	}

	// Usually, conditions that overwrite the category (using setCategory()) 
	// should go before the ones that add other categories (using addCategory()).

	if(length == 0)
		e.addCategory("Empty Files");

	if(inRecycle(e)){
		e.addCategory("Windows Recycle");
		if(e.getName().indexOf("$I") == 0)
			e.setMediaTypeStr("application/x-recyclebin");
		else if(e.getName().equals("INFO2"))
			e.setMediaTypeStr("application/x-info2");
	}

	//iPhone backup default folders
	if ((path.indexOf("/application data/apple computer/mobilesync/backup") > -1)||
		(path.indexOf("/appdata/roaming/apple computer/mobilesync/backup") > -1)||
		(path.indexOf("/appdata/roaming/apple computer/mobilesyncbackup") > -1)||
		(path.indexOf("/biblioteca/suporte a aplicativos/mobilesync/backup") > -1)||
		(path.indexOf("/library/application support/mobilesync/backup") > -1) ||
		(path.indexOf("/apple/mobilesync/backup") > -1)
		)
		e.addCategory("iPhone Backup");
		
	if (mime.equals("application/x-ios-backup-manifest-db"))
		e.addCategory("iPhone Backup");
	
	if (mime.equals("application/x-ios-sms-db") ||
		mime.equals("application/x-ios-addressbook-db") ||
		mime.equals("application/x-ios-calllog-db") ||
		mime.equals("application/x-ios8-calllog-db") ||
		mime.equals("application/x-ios-voicemail-db") ||
		mime.equals("application/x-ios-oldnotes-db") ||
		mime.equals("application/x-ios-notes-db") ||
		mime.equals("application/x-ios-photos-db") ||
		mime.equals("application/x-ios-calendar-db") ||
		mime.equals("application/x-ios-locations-db")
		){
		e.addCategory("Databases");
	}
	
	
	//Torchat Install files
	if (path.indexOf("torchat/") !== -1){
		e.addCategory("Torchat");
		e.addCategory("Tor");
	}
		
	//Files related to Tor, TorBrowser e OperaTor
	if ((path.indexOf("torbrowser/") !== -1)||
		(name.equals("tor.exe"))||
		(name.equals("operator.exe"))||
		(name.equals("start-tor-browser.desktop"))||
		(name.equals("tor-resolve.exe"))
		)
		e.addCategory("Tor");
	
	
	//Cloud Storage Software and their default local folders
	if (((path.indexOf("megasync/") !== -1)||
		(path.indexOf("dropbox/") !== -1)||
		(path.indexOf("com.getdropbox") !== -1)||
		(path.indexOf("onedrive/") !== -1)||
		(path.indexOf("onedrive - ") !== -1)||   //onedrive - <OrganizationName> in the case of OneDrive for Business
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
		(name.indexOf("dropbox.exe") !== -1)||
		(name.indexOf("megasync.exe") !== -1)||
		(name.indexOf("onedrive.exe") !== -1)||
		(name.indexOf("skydrive.exe") !== -1)||
		(name.indexOf("googledrivesync.exe") !== -1)||
		(name.indexOf("amazondrive.exe") !== -1)||
		(name.indexOf("amazonclouddrive.exe") !== -1)||
		(name.indexOf("boxsync.exe") !== -1)||
		(name.indexOf("mediafire desktop.exe") !== -1)||
		(name.indexOf("mf_watch.exe") !== -1)||
		(name.indexOf("resilio-sync.exe") !== -1)||
		(name.indexOf("resilio sync.exe") !== -1)||
		(name.indexOf("resiliosync.exe") !== -1)||
		(name.indexOf("resilio sync.app") !== -1)||
		(name.indexOf("resilio-sync_x64.exe") !== -1)||
		(name.indexOf("btsync.exe") !== -1)||
		(name.indexOf("tresorit.exe") !== -1))&&((path.indexOf("/appdata/local") == -1)&&(path.indexOf("/appdata/locallow") == -1)&&(path.indexOf("/appdata/roaming") == -1)&&(path.indexOf("/programdata/") == -1)&&(path.indexOf("/desktop.ini") == -1))
		)
		e.addCategory("Cloud Drives");
	

	//Programas peer-to-peer
	if ((path.indexOf("/roaming/shareaza/data") !== -1)	||
		(name.indexOf("shareaza.db3") !== -1)
		)
		e.addCategory("Shareaza");
	
		
	//Telegram
	if (name.equals("translit.cache") === true){
		e.addCategory("Telegram");
		e.addCategory("Contacts");
	}
	if ((path.indexOf("ph.telegra.telegraph") !== -1))	{
		e.addCategory("Telegram");	
	}
	
	//Categories for Brazilian Software
	
	//Program Files of Federal Taxes Agency
	if(e.getMediaType().toString().equals("application/irpf")){
		
		if (name.indexOf("-irpf-") !== -1)
			e.addCategory("Tax Returns and Receipts IRPF");

		else if (name.indexOf("-dirf-") !== -1)
			e.addCategory("Tax Returns and Receipts DIRF");

		else if ((name.indexOf("dirf") !== -1)&& (ext.equals("fdb")))
			e.addCategory("Tax Returns and Receipts DIRF");

		else if (name.indexOf("-dipj-") !== -1)
			e.addCategory("Tax Returns and Receipts DIPJ");
		
		else if (name.indexOf("-cnpj-") !== -1)
			e.addCategory("Tax Returns and Receipts CNPJ");
		
		else if (name.indexOf("-dsimples-") !== -1)
			e.addCategory("Tax Returns and Receipts DSIMPLES");
		
		else if (name.indexOf("-dctfs") !== -1)
			e.addCategory("Tax Returns and Receipts DCTF");

		else if (name.indexOf("-dctfm") !== -1)
			e.addCategory("Tax Returns and Receipts DCTF");

		else if (name.indexOf("-perdcomp") !== -1)
			e.addCategory("Tax Returns and Receipts PER DCOMP");
		
		else if (ext.equals("rec")||ext.equals("dec") ||ext.equals("bak")  ||ext.equals("dbk"))
			e.addCategory("Other Tax Returns and Receipts");	
	}
	
	
	//Files related to "Conectividade Social da CAIXA", "Sistema Empresa de Recolhimento do FGTS", "Informações à Previdência Social" (SEFIP/GEFIP)
	if(e.getMediaType().toString().equals("application/zip")){
		if (ext.equals("sfp")||(ext.equals("bkp")&& name.indexOf(".bkp")==16))
			e.addCategory("SEFIP_GEFIP Files");	
		if (ext.equals("cns"))
			e.addCategory("Social Connectivity Program Files");
	}
	
	if (name.indexOf("sefip.re") !== -1 || name.indexOf("sefipcr.re") !== -1)
		e.addCategory("SEFIP_GEFIP Files");
		
	if (name.indexOf("sfpdb001") !== -1)
		e.addCategory("SEFIP Databases");
		
	if (name.indexOf("sefip.exe") !== -1)
		e.addCategory("SEFIP Executables");
		
	if (name.indexOf("cnsini.exe") !== -1){
		e.addCategory("Social Connectivity Program Executables");
		e.addCategory("Social Connectivity Program Files");
	}
	
	//Must be tested for false positives...
	/*if ((name.endsWith(".re") === true)||(name.equals("hash.txt") === true))
		e.addCategory("SEFIP Files");
	
	if (name.equals("selo.xml") === true){
		e.addCategory("Social Connectivity Program Files");
		e.addCategory("SEFIP Files");
	}
	*/
	
	//Files related to "Guia de Recolhimento Rescisório do FGTS da Caixa Econômica Federal"
	if (name.indexOf("grrf.re") !== -1 || name.indexOf("grrf.fdb") !== -1)	
		e.addCategory("GRRF Files");	
		
		
	//Specific PDF files with known name patterns created by some softwares
	if(categorias.indexOf("PDF Documents") !== -1)
	{
		if (name.indexOf("grf_") !== -1 || name.indexOf("sefip_") !== -1 || name.indexOf("gare ") !== -1 || (name.indexOf("re_") !== -1 && name.indexOf("re_") < 5))
			e.addCategory("PDF Bills of Exchange");
		if (name.indexOf("-irpf-20") !== -1 || name.indexOf("-irpf-19") !== -1)
			e.addCategory("Tax Returns and Receipts IRPF");
		if (name.indexOf("-dirf-20") !== -1 || name.indexOf("-dirf-19") !== -1)
			e.addCategory("Tax Returns and Receipts DIRF");
		if (name.indexOf("-dipj-20") !== -1 || name.indexOf("-dipj-19") !== -1)
			e.addCategory("Tax Returns and Receipts DIPJ");
		if (name.indexOf("-cnpj-20") !== -1 || name.indexOf("-cnpj-19") !== -1)
			e.addCategory("Tax Returns and Receipts CNPJ");
		if (name.indexOf("-irpf-20") !== -1 || name.indexOf("-irpf-19") !== -1)
			e.addCategory("Tax Returns and Receipts IRPF");
		if (name.indexOf("-dsimples-20") !== -1 || name.indexOf("-dsimples-19") !== -1)
			e.addCategory("Tax Returns and Receipts DSIMPLES");
		if (name.indexOf("-dctfs1") !== -1 || name.indexOf("-dctfs2") !== -1)
			e.addCategory("Tax Returns and Receipts DCTF");
	}
	if (((path.indexOf("pdcomp") !== -1)||
	(path.indexOf("perdcomp") !== -1))
	&&(path.indexOf("wdpdcomp") == -1)&&(path.indexOf("wpdcomp") == -1))	
	{
		e.addCategory("Tax Returns and Receipts PER DCOMP");	
	}
	
	
	//SPED Program Files
	if ((ext.equals("sped"))	||
	((ext.equals("txt"))&&(name.indexOf("sped-") !== -1))||
	((ext.equals("txt"))&&(name.indexOf("sped_") !== -1))||
	(path.indexOf("sped/") !== -1)
	)
	{
		e.addCategory("SPED Program Files");
	}
	
	//Receitanet Program Files
	if (
	(path.indexOf("/receitanet") !== -1)
	)
	{
		e.addCategory("Receitanet Program Files");
	}
	
	//RFB Program Files
	if(
		(path.indexOf("programa rfb/") !== -1)||
		(path.indexOf("programas rfb/") !== -1)||
		(path.indexOf("/irpf20") !== -1)||
		(path.indexOf("/irpf19") !== -1)||
		(path.indexOf("/dirpf20") !== -1)||
		(path.indexOf("/dirpf19") !== -1)||
		(path.indexOf("/dirpf/") !== -1)||
		(
			//((path.indexOf("/arquivos de programas") !== -1)||(path.indexOf("/program files") !== -1))&&
				(
				(path.indexOf("/dirf") !== -1)||
				(path.indexOf("/dipj") !== -1)||
				(path.indexOf("/dctf") !== -1)||
				(path.indexOf("/simplesnacional") !== -1)||
				(path.indexOf("/sedif") !== -1)||
				(path.indexOf("/pgdcnpj") !== -1)||
				(path.indexOf("/sefaz") !== -1)||
				(path.indexOf("/sintegra") !== -1)||
				(path.indexOf("/danfe") !== -1)||
				(path.indexOf("/gdrais") !== -1)||
				(path.indexOf("/sicalcp") !== -1)||
				(path.indexOf("/cagednet") !== -1)||
				(path.indexOf("/dsimples") !== -1)
				)
		)
	)
	{
		e.addCategory("RFB Program Files");
	}
	//emule files
	if(mime.equals("application/x-emule-searches")){
		e.addCategory("Searches");
		e.addCategory("E-Mule");
	}
	if(e.getPath().toLowerCase().contains("mule")){
		if(e.getName().equals("preferences.ini")){
			e.setMediaTypeStr("application/x-emule-preferences-ini");
			e.addCategory("E-Mule");
		}
		if(e.getName().equals("preferences.dat")){
			e.setMediaTypeStr("application/x-emule-preferences-dat");
			e.addCategory("E-Mule");
		}
	}
	
	// Custom Regripper Reports

	if (mime.equals("application/x-windows-registry-report")){
		
		if (name.indexOf("_os") !== -1) {
			e.setCategory("Registry OS Info")
		}

		if (name.indexOf("_installedsoftware") !== -1) {
			e.setCategory("Registry Installed Apps")
		}

		if (name.indexOf("_network") !== -1) {
			e.setCategory("Registry Network Info")
		}

		if (name.indexOf("_storage") !== -1) {
			e.setCategory("Registry Storage Info")
		}

		if (name.indexOf("_devices") !== -1 ||
			name.indexOf("usbdeview") !== -1)
		{
			e.setCategory("Registry Device Info")
		}

		if (name.indexOf("_programexecution") !== -1) {
			e.setCategory("Registry Program Run")
		}

		if (name.indexOf("_autorun") !== -1) {
			e.setCategory("Registry Auto Run")
		}

		if (name.indexOf("_log") !== -1) {
			e.setCategory("Registry Log Info")
		}

		if (name.indexOf("_malware") !== -1) {
			e.setCategory("Registry Malware Info")
		}

		if (name.indexOf("_web") !== -1) {
			e.setCategory("Registry Web Info")
		}

		if (name.indexOf("_users") !== -1 ||
			name.indexOf("_useraccountinfo") !== -1)
		{
			e.setCategory("Registry User Accounts")
		}

		if (name.indexOf("_useractivity") !== -1) {
			e.setCategory("Registry User Activity")
		}
		
		if (name.indexOf("_userfile") !== -1) {
			e.setCategory("Registry User Files")
		}

		if (name.indexOf("_usernetwork") !== -1) {
			e.setCategory("Registry User Network Activity")
		}

		if (name.indexOf("_userconfig") !== -1) {
			e.setCategory("Registry User Config")
		}

		if (name.indexOf("_uservirtual") !== -1) {
			e.setCategory("Registry User Virtualization")
		}

		if (name.indexOf("_usercommunications") !== -1) {
			e.setCategory("Registry User Communication")
		}
			
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
