/**
 * Javascript Regex validator example.
 * All functions must be implemented: getRegexNames, format and validate
 */

/**
 * Returns the regex name this validator will be applied to. Must be equal to the name registered in 'conf/RegexConfig.txt'.
 * @returns String array with at least one regex name to be validated
 */
function getRegexNames() {
	return ["EXAMPLE"];
}

/**
 * Validates the regex match.
 * @param String hit - the regex match.
 * @returns boolean - true if the match is a valid value, false otherwise.
 */
function validate(hit) {
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