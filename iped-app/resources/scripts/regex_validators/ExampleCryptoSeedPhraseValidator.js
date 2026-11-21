/**
 * Javascript Seed Phrase Validator. Tries to ignore false positives using the following rules:
 * 1. if a word has 3 or more occurrences;
 * 2. if there are 3 or more words with at least 2 occurrences each.
 * 
 * Tests have shown this could miss about 0.066% of valid seed phrases with 24 words, what was considered acceptable.
 */

/**
 * Returns the regex name this validator will be applied to. Must be equal to the name registered in 'conf/RegexConfig.txt'.
 * @returns String array with at least one regex name to be validated
 */
function getRegexNames() {
	return ["Example_CRYPTO_POSSIBLE_SEED_PHRASE_EN","Example_CRYPTO_POSSIBLE_SEED_PHRASE_PT"];
}

/**
 * Validates the regex match.
 * @param String hit - the regex match.
 * @returns boolean - true if the match is a valid value, false otherwise.
 */
function validate(hit) {
	var words=hit.split(new RegExp("[ \t\n\r]+"));
	
	var wordMap={};
	var pairs=0;
	for(var i=0;i<words.length;i++){
		var word=words[i].trim();
		var cont=wordMap[word];
		if(cont!=null){
			cont++;
		}else{
			cont=1;
		}
		wordMap[word]=cont;
		if(cont == 2){
			pairs++;
		}else if(cont>=3){
			return false;
		}
	}
	if(pairs>=3){
		return false;
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