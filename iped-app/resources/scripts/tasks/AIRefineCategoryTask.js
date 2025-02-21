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
	config=configurationManager.findObject(RemoteImageClassifierConfig.class)
	categorizationThreshold=config.getCategorizationThreshold()	
}

function finish(){}

/*
 * Changes category of items based on their properties
 *
 */
function process(e){

	listcategories={
		"AI_CSAM":"AI Label: Child Sexual Abuse",
		"AI_LIKELYCSAM":"AI Label: Likely Child Sexual Abuse",
		"AI_Drawing":"AI Label: Drawing",
		"AI_Other":"AI Label: Other",
		"AI_People":"AI Label: People",
		"AI_Porn":"AI Label: Pornography",
		}
	for(cat in listcategories){
		if (e.getExtraAttribute(cat)>categorizationThreshold){
			e.addCategory(listcategories[cat]);
		}		
	}
}

