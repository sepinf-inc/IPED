/*
 * Script of AI Category Specialization based on item properties.
 * Uses javascript language to allow flexibility in definitions.
 */

var RemoteImageClassifierConfig = Java.type("iped.engine.config.RemoteImageClassifierConfig");
var Arrays = Java.type("java.util.Arrays");

/*
 * Name of processing task
*/
function getName() {
	return "AIRefineCategoryTask";
}

function getConfigurables() {
	return Arrays.asList(new RemoteImageClassifierConfig());
}

var categorizationThreshold; // Declare the variable explicitly at the top of the script
function init(configurationManager) {
	var config = configurationManager.findObject(RemoteImageClassifierConfig.class);
	categorizationThreshold = config.getCategorizationThreshold();
}

function finish() {}

/*
 * Changes category of items based on their properties
 */
function addCategories(e, listcategories, default_cat) {
    default_cat = (typeof default_cat !== "undefined") ? default_cat : null;
	var tot = 0;
	for (var cat in listcategories) {
		if (e.getExtraAttribute(cat) > categorizationThreshold) {
			if (typeof listcategories[cat] === 'string') {
				e.addCategory(listcategories[cat]);	 // Add category if it's a string
				tot++;
			} else {
				tot += addCategories(e, listcategories[cat]["subCategories"], listcategories[cat]["name"]);
			}
		}
	}
	if (tot === 0 && default_cat !== null) {
		e.addCategory(default_cat);
	}
}

function process(e) {

	var listcategories= {
		"AI_CSAM":"AI Label - Child Sexual Abuse",
		"AI_LIKELYCSAM":"AI Label - Likely Child Sexual Abuse",
		"AI_Drawing":{"name":"AI Label - Drawing","subCategories":{
			"AI_Drawing_CSAM":"AI Label - Child Sexual Abuse Drawing",
			"AI_Drawing_Porn":"AI Label - Explicit Drawing"}
		},
		"AI_People":"AI Label - People",
		"AI_Porn":"AI Label - Pornography"
		}
		
	addCategories(e, listcategories);
}
