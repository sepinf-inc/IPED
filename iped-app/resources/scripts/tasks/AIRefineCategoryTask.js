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

  // Escolhe a chave com maior score neste nível
  var bestKey = null;
  var bestVal = -Infinity;

  for (var cat in listcategories) {
    var val = Number(e.getExtraAttribute(cat));
    if (!isNaN(val) && val > bestVal) {
      bestVal = val;
      bestKey = cat;
    }
  }

  // Se o melhor valor ultrapassa o threshold, desce só por esse caminho
  if (bestKey !== null && bestVal > categorizationThreshold) {
    var entry = listcategories[bestKey];

    if (typeof entry === 'string') {
      e.addCategory(entry);
      return 1;
    }

    if (entry && typeof entry === 'object') {
      // Tenta escolher a melhor subcategoria
      if (entry.subCategories && typeof entry.subCategories === 'object') {
        var added = addCategories(e, entry.subCategories, entry.name);
        if (added) return 1; // alguma subcategoria válida foi adicionada
      }
      // Nenhuma sub passou do threshold: adiciona a categoria atual (pai)
      if (entry.name != null) {
        e.addCategory(entry.name);
        return 1;
      }
    }
  }

  // Ninguém passou do threshold neste nível: usa default (se houver)
  if (default_cat !== null) {
    e.addCategory(default_cat);
    return 1;
  }

  return 0;
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
