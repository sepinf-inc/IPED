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
            e.setCategory("User Accounts");
            break;
    }
	
	
}


