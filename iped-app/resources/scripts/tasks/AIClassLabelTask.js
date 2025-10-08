/*
 * Script of AI Category Specialization based on item properties.
 */

var RemoteImageClassifierConfig = Java.type("iped.engine.config.RemoteImageClassifierConfig");
var Arrays = Java.type("java.util.Arrays");

var aiClassProperty = "ai:class";

/*
 * Name of processing task
*/
function getName() {
	return "AIClassLabelTask";
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
 * Label item class based on classifier results.
 */
function addClasses(e, listClasses, defaultClass) {
  defaultClass = (typeof defaultClass !== "undefined") ? defaultClass : null;

  // Escolhe a chave com maior score neste nível
  var bestKey = null;
  var bestVal = -Infinity;

  for (var key in listClasses) {
    var val = Number(e.getExtraAttribute(key));
    if (!isNaN(val) && val > bestVal) {
      bestVal = val;
      bestKey = key;
    }
  }

  // Se o melhor valor ultrapassa o threshold, desce só por esse caminho
  if (bestKey !== null && bestVal > categorizationThreshold) {
    var entry = listClasses[bestKey];

    if (typeof entry === 'string') {
      e.setExtraAttribute(aiClassProperty, entry);
      return 1;
    }

    if (entry && typeof entry === 'object') {
      // Tenta escolher a melhor subclasse
      if (entry.subClasses && typeof entry.subClasses === 'object') {
        var added = addClasses(e, entry.subClasses, entry.name);
        if (added) return 1; // alguma subclasse válida foi adicionada
      }
      // Nenhuma sub passou do threshold: adiciona a classe atual (pai)
      if (entry.name != null) {
        e.setExtraAttribute(aiClassProperty, entry.name);
        return 1;
      }
    }
  }

  // Ninguém passou do threshold neste nível: usa default (se houver)
  if (defaultClass !== null) {
    e.setExtraAttribute(aiClassProperty, defaultClass);
    return 1;
  }

  return 0;
}

function process(e) {

	var listClasses= {
		"ai:csam":"Child Sexual Abuse",
		"ai:likelyCsam":"Likely Child Sexual Abuse",
		"ai:drawing":{"name":"Drawing","subClasses":{
			"ai:drawingCsam":"Child Sexual Abuse Drawing",
			"ai:drawingPorn":"Explicit Drawing"}
		},
		"ai:people":"People",
		"ai:porn":"Pornography"
    }
		
	addClasses(e, listClasses);
}
