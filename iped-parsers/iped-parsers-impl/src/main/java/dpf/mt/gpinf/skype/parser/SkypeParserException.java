package dpf.mt.gpinf.skype.parser;

/**
 * Classe que representa erros de processamento do SkypeParser.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeParserException extends Exception {
    private static final long serialVersionUID = 2421011415910851955L;

    public SkypeParserException() {

    }

    public SkypeParserException(Exception e) {
        super(e);
    }

}
