/*
 * Script de refinamento de categorias com base nas propriedades dos arquivos
 * Utiliza linguagem javascript para permitir flexibilidade nas definições
 */

function getName(){
	return "RefineCategoryTask";
}

function init(confProps, configFolder){}

function finish(){}

/*
 * Função que adiciona categoria ao objeto EvidenceFile "e", segundo regras baseadas nas propriedades.
 * Pode acessar qualquer método da classe EvidenceFile (inclusive categorias já atribuídas com base no tipo):
 *
 *	Retorno: Funções...
 *	String:  e.getName(), e.getExt(), e.getPath(), e.getCategories() (categorias concatenadas com | )
 *	Date:    e.getModDate(), e.getCreationDate(), e.getAccessDate() (podem ser nulos)
 *	Long:    e.getLength()
 *
 * Para adicionar uma categoria: e.addCategory(String)	
 * Para redefinir a categoria:   e.setCategory(String)
 * Para remover a categoria: 	 e.removeCategory(String)
 *
 */
function process(e){

	var categorias = e.getCategories();
	var length = e.getLength();
		
	if(e.getExt().toLowerCase().equals("mts")){
		e.setMediaTypeStr("video/mp2t");
		e.setCategory("Vídeos");
	}
	
	var mime = e.getMediaType().toString();
	if(mime.indexOf("x-ufed-") != -1 && categorias.indexOf("Outros Arquivos") != -1){
		var cat = mime.substring(mime.indexOf("x-ufed-") + 7);
		cat = cat.substring(0, 1).toUpperCase() + cat.substring(1); 
		e.setCategory(cat);
	}
	
	if(mime.equals("application/dita+xml") && e.getName().equals("com.whatsapp_preferences.xml")){
		e.setMediaTypeStr("application/x-whatsapp-user-xml");
		e.setCategory("Contatos");
	}
	
	if(categorias.indexOf("Imagens") > -1){

		if(isFromInternet(e))
			e.setCategory("Imagens Temporárias Internet");

		else if(inSystemFolder(e))
			e.setCategory("Imagens em Pasta de Sistema");
			
		else
			e.setCategory("Outras Imagens");
	}

	else if(categorias.indexOf("Outros Textos") > -1){

		if(isFromInternet(e))
			e.setCategory("Textos Temporários Internet");

		else if(inSystemFolder(e))
			e.setCategory("Textos em Pasta de Sistema");

		else {
			var ext = e.getExt().toLowerCase();
			if (ext.equals("url"))
				e.setCategory("Atalhos para URLs");
		}
			
	}
	
	else if(isFromInternet(e)){
		if(e.getMediaType().toString().equals("application/x-sqlite3"))
			e.setCategory("Histórico de Internet");
	}
    
    else if(categorias.indexOf("Outros Arquivos") > -1){
		var ext = e.getExt().toLowerCase();
		
		if (ext.equals("url"))
			e.setCategory("Atalhos para URLs");
			
		else if (ext.equals("plist")) {
			var path = e.getPath().toLowerCase();
			if (path.indexOf("/safari/") > -1) {
				var nome = e.getName().toLowerCase();
				if (nome.indexOf("history") > -1 || 
	 			   nome.indexOf("downloads") > -1 ||
	  			   nome.indexOf("lastsession") > -1 ||
	   			   nome.indexOf("topsites") > -1 || 
				   nome.indexOf("bookmarks") > -1)
					e.setCategory("Histórico de Internet");
			}
		} else {
			var nome = e.getName().toLowerCase();
			if (e.getPath().toLowerCase().indexOf("/chrome/user data/") > -1) {
				if (nome.indexOf(" session") > -1 || 
	 			   nome.indexOf(" tabs") > -1 ||
	  			   nome.indexOf("visited links") > -1 ||
	   			   nome.indexOf("history") > -1 || 
				   nome.indexOf("journal") > -1 || 
				   nome.indexOf("login data") > -1)
					e.setCategory("Histórico de Internet");
			}
		}
	}
	
	// Usually, conditions that overwrite the category (using setCategory()) 
	// should go before the ones that add other categories (using addCategory()).

	if(length == 0)
		e.addCategory("Tamanho Zero");

	if(inRecycle(e)){
		e.addCategory("Lixeira do Windows");
		if(e.getName().indexOf("$I") == 0)
			e.setMediaTypeStr("application/x-recyclebin");
		else if(e.getName().equals("INFO2"))
			e.setMediaTypeStr("application/x-info2");
	}
	
   /*
    *  Contribuições PCF Sícoli
    */
	
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
		e.addCategory("Backup iPhone");
		
	
	
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
		e.addCategory("Armazenamento na nuvem");
	

	//Programas peer-to-peer
	if ((path.indexOf("/Roaming/Shareaza/Data") !== -1)	||
		(nome.indexOf("Shareaza.db3") !== -1)
		)
		e.addCategory("Peer-to-peer");
	
		
	//Telegram
	if (nome.equals("translit.cache") === true){
		e.addCategory("Telegram");
		e.addCategory("Contatos");
	}
	if ((path.indexOf("ph.telegra.telegraph") !== -1))	{
		e.addCategory("Telegram");	
	}
		

	//Arquivos de certificados digitais (chaves privadas). Idealmente devem ser classificados por assinatura
	//if ((ext.equals("pri"))||(ext.equals("cer"))||(ext.equals("crt"))||(ext.equals("der"))||(ext.equals("key"))||(ext.equals("pem"))||(ext.equals("pvk"))||(ext.equals("pfx"))||(ext.equals("p12"))||(ext.equals("cert"))||(ext.equals("jks")))
	//	e.addCategory("Certificados");

	
	//Arquivos de sistemas da Receita Federal
	if(e.getMediaType().toString().equals("application/irpf")){
		
		if (nome.indexOf("-irpf-") !== -1)
			e.addCategory("Declaracoes e Recibos IRPF");

		else if (nome.indexOf("-dirf-") !== -1)
			e.addCategory("Declaracoes e Recibos DIRF");

		else if ((nome.indexOf("dirf") !== -1)&& (ext.equals("fdb")))
			e.addCategory("Declaracoes e Recibos DIRF");

		else if (nome.indexOf("-dipj-") !== -1)
			e.addCategory("Declaracoes e Recibos DIPJ");
		
		else if (nome.indexOf("-cnpj-") !== -1)
			e.addCategory("Declaracoes e Recibos CNPJ");
		
		else if (nome.indexOf("-dsimples-") !== -1)
			e.addCategory("Declaracoes e Recibos DSIMPLES");
		
		else if (nome.indexOf("-dctfs") !== -1)
			e.addCategory("Declaracoes e Recibos DCTF");

		else if (nome.indexOf("-dctfm") !== -1)
			e.addCategory("Declaracoes e Recibos DCTF");

		else if (nome.indexOf("-perdcomp") !== -1)
			e.addCategory("Declaracoes e Recibos PER DCOMP");
		
		else if (ext.equals("rec")||ext.equals("dec") ||ext.equals("bak")  ||ext.equals("dbk"))
			e.addCategory("Outras Declaracoes e Recibos");	
	}
	
	
	//Arquivos dos programas Conectividade Social da CAIXA e Sistema Empresa de Recolhimento do FGTS e Informações à Previdência Social (SEFIP/GEFIP)
	if(e.getMediaType().toString().equals("application/zip")){
		if (ext.equals("sfp")||(ext.equals("bkp")&& nome.indexOf(".bkp")==16))
			e.addCategory("Arquivos SEFIP_GEFIP");	
		if (ext.equals("cns"))
			e.addCategory("Arquivos Conectividade Social");
	}
	
	if (nome.indexOf("sefip.re") !== -1 || nome.indexOf("sefipcr.re") !== -1)
		e.addCategory("Arquivos SEFIP_GEFIP");
		
	if (nome.indexOf("sfpdb001") !== -1)
		e.addCategory("Base de dados SEFIP");
		
	if (nome.indexOf("sefip.exe") !== -1)
		e.addCategory("Executaveis SEFIP");
		
	if (nome.indexOf("cnsini.exe") !== -1){
		e.addCategory("Executaveis Conectividade Social");
		e.addCategory("Arquivos Conectividade Social");
	}
	
	//Monitorar por falsos positivos	
	/*if ((nome.endsWith(".re") === true)||(nome.equals("hash.txt") === true))
		e.addCategory("Arquivos gerais SEFIP");
	
	if (nome.equals("selo.xml") === true){
		e.addCategory("Arquivos Conectividade Social");
		e.addCategory("Arquivos gerais SEFIP");
	}
	*/
	
	//Arquivos relativos à Guia de Recolhimento Rescisório do FGTS da Caixa Econômica Federal
	if (nome.indexOf("grrf.re") !== -1 || nome.indexOf("grrf.fdb") !== -1)	
		e.addCategory("Arquivos GRRF");	
		
		
	//Documentos em PDF com nomes específicos de saídas padrão de programas da Receita Federal e Ministério do Trabalho
	if(categorias.indexOf("Documentos PDF") !== -1)
	{
		if (nome.indexOf("grf_") !== -1 || nome.indexOf("sefip_") !== -1 || nome.indexOf("gare ") !== -1 || (nome.indexOf("re_") !== -1 && nome.indexOf("re_") < 5))
			e.addCategory("Guias em PDF");
		if (nome.indexOf("-irpf-20") !== -1 || nome.indexOf("-irpf-19") !== -1)
			e.addCategory("Declaracoes e Recibos IRPF");
		if (nome.indexOf("-dirf-20") !== -1 || nome.indexOf("-dirf-19") !== -1)
			e.addCategory("Declaracoes e Recibos DIRF");
		if (nome.indexOf("-dipj-20") !== -1 || nome.indexOf("-dipj-19") !== -1)
			e.addCategory("Declaracoes e Recibos DIPJ");
		if (nome.indexOf("-cnpj-20") !== -1 || nome.indexOf("-cnpj-19") !== -1)
			e.addCategory("Declaracoes e Recibos CNPJ");
		if (nome.indexOf("-irpf-20") !== -1 || nome.indexOf("-irpf-19") !== -1)
			e.addCategory("Declaracoes e Recibos IRPF");
		if (nome.indexOf("-dsimples-20") !== -1 || nome.indexOf("-dsimples-19") !== -1)
			e.addCategory("Declaracoes e Recibos DSIMPLES");
		if (nome.indexOf("-dctfs1") !== -1 || nome.indexOf("-dctfs2") !== -1)
			e.addCategory("Declaracoes e Recibos DCTF");
	}
	if (((path.indexOf("pdcomp") !== -1)||
	(path.indexOf("perdcomp") !== -1))
	&&(path.indexOf("wdpdcomp") == -1)&&(path.indexOf("wpdcomp") == -1))	
	{
		e.addCategory("Declaracoes e Recibos PER DCOMP");	
	}
	
	
	//Arquivos do programa SPED da Receita Federal
	if ((ext.equals("sped"))	||
	((ext.equals("txt"))&&(nome.indexOf("sped-") !== -1))||
	((ext.equals("txt"))&&(nome.indexOf("sped_") !== -1))||
	(path.indexOf("sped/") !== -1)
	)
	{
		e.addCategory("Arquivos programa SPED");
	}
	
	//Arquivos do programa SPED da Receita Federal
	if (
	(path.indexOf("/receitanet") !== -1)
	)
	{
		e.addCategory("Arquivos programa Receitanet");
	}
	
	//Arquivos de programas da Receita Federal
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
		e.addCategory("Arquivos programas RFB");
	}		

}


/*
 *  Função auxiliar
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
 *  Função auxiliar
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
 *  Função auxiliar
 */
function inRecycle(e){
	var path = e.getPath().toLowerCase();
	return 	path.indexOf("$recycle.bin") > -1 || path.indexOf("/recycler/") > -1 || path.indexOf("\\recycler\\") > -1;
}