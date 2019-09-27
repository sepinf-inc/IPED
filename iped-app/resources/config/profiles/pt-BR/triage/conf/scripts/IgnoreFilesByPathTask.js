/*
 * Script de refinamento de categorias com base nas propriedades dos arquivos
 * Utiliza linguagem javascript para permitir flexibilidade nas definições
 */

function getName(){
	return "IgnoreFilesByPathTask";
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

//Verifica o caminho dos itens e, caso estejam na "lista-negra", serão ignorados, isto é, não passarão por criação de temporário, hash, indexação, etc
	var path = e.getPath().toLowerCase();
	
	if(/((\\|\/)vol_vol.|sd..)(\\|\/)(windows|windows\....|winnt|win98|win2000|wutemp|swsetup|Windows Update Setup Files|msdownld\.tmp|intel|AMD|nvidia|nvidia corporation|perflogs|System Volume Information|Recovery|System Recovery|windows\.old|windows\.old\(.\)|Windows10Upgrade|MSOCache|Windows\~BT|Windows\.\~BT$|Library|lib|lib64|Developer|Network|System|sys|bin|dev|sbin|com\.apple\.boot\.S|Perl|boot|bootmgr|Cygwin|Cygwin64|MinGW|python..|SYSTEM\.SAV|SWSETUP|SDK|Config\.msi|WINDOWS\.\~WS|BOOTNXT|bootsect|DRIVERS|DRIVER|EFI|\$Extend|\$GetCurrent|\$SysReset|\$Unalloc|HDDRecovery|Dell|hp|sony|samsung|Preload|\.fseventsd|HFS\+ Private|\.Spotlight-V100|WindowsImages|MSI.....\.tmp|AdwCleaner|\$AV_ASW|(Arquivos de Programas$|Arquivos de Programas \(x86\)|Program Files|Program Files \(x86\))(\\|\/)(7-Zip|Adobe|Avast|Avast Software|Common Files|DVD Maker|Dolby Digital Plus|DIFX|ffdshow|Foxit Software|GBPlugin|Google|HP|InstallShield Installation Information|Integrated Camera|ISO Creator .\..|Intel|Intel Corporation|internet explorer|IrfanView|Java|Lenovo|Lenovo Registration|LibreOffice .|Microsoft Office|Microsoft Skydrive|Microsoft\.NET|MSBuild|MSECache|Mozilla Firefox|Mozilla Maintenance Service|nvidia|nvidia corporation|PDFCreator|Realtek|Reference Assemblies|rempl|Synaptics|ThinkPad|Uninstall Information|VLC|Windows Defender|Windows Defender Advanced Threat Protection|Windows Mail|Windows Media Player|Windows Multimedia Platform|Windows nt|Windows Photo Viewer|Windows Portable Devices|Windows Security|Windows Sidebar|WindowsPowerShell|WindowsApps|Winzip))(\\|\/)/i.test(path))
	{
		e.setToIgnore(true);
	}

	/*Versao "one liner"
	if(/(\\|\/)vol_vol.(\\|\/)(windows|windows\....|winnt|win98|win2000|wutemp|swsetup|Windows Update Setup Files|msdownld\.tmp|intel|AMD|nvidia|nvidia corporation|perflogs|System Volume Information|Recovery|System Recovery|windows\.old|windows\.old\(.\)|Windows10Upgrade|MSOCache|Windows\~BT|Windows\.\~BT$|Library|lib|lib64|Developer|Network|System|sys|bin|dev|sbin|com\.apple\.boot\.S|Perl|boot|bootmgr|Cygwin|Cygwin64|MinGW|python..|SYSTEM\.SAV|SWSETUP|SDK|Config\.msi|WINDOWS\.\~WS|BOOTNXT|bootsect|DRIVERS|DRIVER|EFI|\$Extend|\$GetCurrent|\$SysReset|\$Unalloc|HDDRecovery|Dell|hp|sony|samsung|Preload|\.fseventsd|HFS\+ Private|\.Spotlight-V100|WindowsImages|MSI.....\.tmp|AdwCleaner|\$AV_ASW|(Arquivos de Programas$|Arquivos de Programas \(x86\)|Program Files|Program Files \(x86\))(\\|\/)(7-Zip|Adobe|Avast|Avast Software|Common Files|DVD Maker|Dolby Digital Plus|DIFX|ffdshow|Foxit Software|GBPlugin|Google|HP|InstallShield Installation Information|Integrated Camera|ISO Creator .\..|Intel|Intel Corporation|internet explorer|IrfanView|Java|Lenovo|Lenovo Registration|LibreOffice .|Microsoft Office|Microsoft Skydrive|Microsoft\.NET|MSBuild|MSECache|Mozilla Firefox|Mozilla Maintenance Service|nvidia|nvidia corporation|PDFCreator|Realtek|Reference Assemblies|rempl|Synaptics|ThinkPad|Uninstall Information|VLC|Windows Defender|Windows Defender Advanced Threat Protection|Windows Mail|Windows Media Player|Windows Multimedia Platform|Windows nt|Windows Photo Viewer|Windows Portable Devices|Windows Security|Windows Sidebar|WindowsPowerShell|WindowsApps|Winzip))(\\|\/)/i.test(e.getPath().toLowerCase())) e.setToIgnore(true);
	*/

}
