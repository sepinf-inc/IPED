/*
 * Script of Category Specialization based on item properties.
 * Uses javascript language to allow flexibility in definitions.
 */

/* Name of processing task
*/
function getName(){
	return "LeappCategoryTask";
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
	var path = e.getPath().toLowerCase().replace(/\\/g, "/");
	
	var m = e.getMetadata();
    var pluginName = m.get("ALEAPP:PLUGIN");
    
    switch(pluginName){
        case "accounts_ce":
        case "accounts_de":
            e.setCategory("User Accounts");
            break;
        case "accounts_ce_authtokens":
            e.setCategory("Passwords");
            break;
        case "sim_info":
            e.setCategory("SIM Data");
            break;
        case "Cello":
            e.setCategory("GDrive File Entries");
            break;
        case "roles":
            e.setCategory("AppRoles");
            break;
        case "frosting":
            e.setCategory("Update information");
            break;
        case "FacebookMessenger":
            if(mime.contains("contacts")){
                e.setCategory("Contacts");
            }
            if(mime.contains("chats")){
                e.setCategory("Instant Messages");
            }
            break;
        case "settingsSecure":
            var name = m.get("ALEAPP:Name");
            var value = m.get("ALEAPP:Value");
            if((name == "bluetooth_address")||(name == "bluetooth_name")){
                e.setCategory("Bluetooth Devices");
            }
            break;
    }
	
}


