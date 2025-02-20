/*
 * Script of Category Specialization based on item properties.
 * Uses javascript language to allow flexibility in definitions.
 */

/* Name of processing task
*/

var RemoteImageClassifierConfig=Java.type("iped.engine.config.RemoteImageClassifierConfig")
var Arrays=Java.type("java.util.Arrays")

function getName(){
	return "AIRefineCategoryTask";
}


function getConfigurables() {
	
	return Arrays.asList(new RemoteImageClassifierConfig());
}

function init(configurationManager) {
	var config=configurationManager.findObject(RemoteImageClassifierConfig.class)
	categorizationThreshold=config.getCategorizationThreshold()	
}

function finish(){}

/*
 * Changes category of items based on their properties
 *
 */
function process(e){

	listcategories={
		"ASI":"AI Label: Child Sexual Abuse",
		"ASI_SUSP":"AI Label: Likely Child Sexual Abuse",
		"Desenhos":"AI Label: Drawing",
		"Outros":"AI Label: Other",
		"Pessoas":"AI Label: People",
		"Porn":"AI Label: Pornography",
		}
	for(cat in listcategories){
		if (e.getExtraAttribute(cat)>0.5){
			e.addCategory(listcategories[cat]);
		}		
	}
}

