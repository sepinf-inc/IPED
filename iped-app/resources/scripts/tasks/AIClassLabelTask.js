/*
 * Script of AI Category Specialization based on item properties.
 */

var RemoteImageClassifierConfig = Java.type("iped.engine.config.RemoteImageClassifierConfig");
var Arrays = Java.type("java.util.Arrays");

var aiLabelProperty = "ai:label";

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
 * Label item based on classifier results.
 */
function addLabel(e, listLabels, defaultLabel) {
  defaultLabel = (typeof defaultLabel !== "undefined") ? defaultLabel : null;

  // Escolhe a chave com maior score neste nível
  var bestKey = null;
  var bestVal = -Infinity;

  for (var key in listLabels) {
    var val = Number(e.getExtraAttribute(key));
    if (!isNaN(val) && val > bestVal) {
      bestVal = val;
      bestKey = key;
    }
  }

  // Se o melhor valor ultrapassa o threshold, desce só por esse caminho
  if (bestKey !== null && bestVal > categorizationThreshold) {
    var entry = listLabels[bestKey];

    if (typeof entry === 'string') {
      e.setExtraAttribute(aiLabelProperty, entry);
      return 1;
    }

    if (entry && typeof entry === 'object') {
      // Tenta escolher a melhor subclasse
      if (entry.subClasses && typeof entry.subClasses === 'object') {
        var added = addLabel(e, entry.subClasses, entry.name);
        if (added) return 1; // alguma subclasse válida foi adicionada
      }
      // Nenhuma sub passou do threshold: adiciona a classe atual (pai)
      if (entry.name != null) {
        e.setExtraAttribute(aiLabelProperty, entry.name);
        return 1;
      }
    }
  }

  // Ninguém passou do threshold neste nível: usa default (se houver)
  if (defaultLabel !== null) {
    e.setExtraAttribute(aiLabelProperty, defaultLabel);
    return 1;
  }

  return 0;
}

function process(e) {

	var listLabels = {
		"ai:csam":"ChildSexualAbuse",
		"ai:likelyCsam":"LikelyChildSexualAbuse",
		"ai:drawing":{"name":"Drawing","subClasses":{
			"ai:drawingCsam":"ChildSexualAbuseDrawing",
			"ai:drawingPorn":"ExplicitDrawing"}
		},
		"ai:people":"People",
		"ai:porn":"Pornography"
    }
		
	addLabel(e, listLabels);
}
