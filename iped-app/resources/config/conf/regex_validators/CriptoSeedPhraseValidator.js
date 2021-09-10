/**
 * Javascript Regex validator example.
 * All functions must be implemented: getRegexNames, format and validate
 */

/**
 * Returns the regex name this validator will be applied to. Must be equal to the name registered in 'conf/RegexConfig.txt'.
 * @returns String array with at least one regex name to be validated
 */
function getRegexNames() {
	return ["CRIPTO_SEED_PHRASE_EN","CRIPTO_SEED_PHRASE_PT"];
}

/**
 * Validates the regex match.
 * @param String hit - the regex match.
 * @returns boolean - true if the match is a valid value, false otherwise.
 */
function validate(hit) {
	var words=hit.split(new RegExp("[ \t\n\r]+"));
	
	var wordMap={};
	for(var i=0;i<words.length;i++){
		var word=words[i].trim();
		var cont=wordMap[word];
		if(cont!=null){
			cont++;
		}else{
			cont=1;
		}
		wordMap[word]=cont;
		if(cont>2){
			return false;
		}
				
	}	
		
	return true;
}

/**
 * Formats the validated match.
 * @param String hit - the regex match.
 * @returns String - the formatted regex match.
 */
function format(hit) {
	return hit;
}