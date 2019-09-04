/**
 * Exemplo de validador javascript.
 * Todas as funções getRegexNames, format e validate devem ser implementadas.
 */


/**
 * Retorna o nome dos regex (em referência ao arquivo 'conf/RegexConfig.txt') ao qual este validator será aplicado.
 * @returns um array contendo um ou mais nomes de regex a serem validados.
 */
function getRegexNames() {
	return ["TEST"];
}

/**
 * Valida a ocorrência do regex.
 * @param hit - a ocorrência do regex.
 * @returns boolean - true se for uma ocorrencia válida, ou false caso contrário.
 */
function validate(hit) {
	return hit.startsWith('a');
}

/**
 * Formata a ocorrência válida.
 * @param hit - a ocorrência do regex.
 * @returns a ocorrência formatada.
 */
function format(hit) {
	return hit.toUpperCase();
}