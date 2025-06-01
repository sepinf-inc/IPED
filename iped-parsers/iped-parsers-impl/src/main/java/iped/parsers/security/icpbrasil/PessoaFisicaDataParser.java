package iped.parsers.security.icpbrasil;

import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses a string containing individual (natural person) data according to a specific format rule. This format is often
 * found in Brazilian ICP-Brasil certificate 'OtherName' fields.
 * 
 * https://ccd.serpro.gov.br/acserprorfb/docs/pcacserprorfba3.pdf
 * 
 */
public class PessoaFisicaDataParser {

    /**
     * Enum representing the fields extracted from the individual's data string.
     */
    public enum PessoaFisicaField {
        /** Birth date in ddmmyyyy format (8 characters) */
        BIRTH_DATE,
        /** CPF (Cadastro de Pessoa Física - Brazilian individual taxpayer registry number) (11 characters) */
        CPF,
        /** PIS/PASEP number (Brazilian social integration program numbers) (11 characters) */
        PIS_PASEP,
        /** RG (Registro Geral - Brazilian ID card) number (15 characters) */
        RG_NUMBER,
        /** RG issuing body and state (e.g., "SSPDF") */
        RG_ISSUER
    }

    // Definition of the length for each field
    private static final int BIRTH_DATE_LEN = 8;
    private static final int CPF_LEN = 11;
    private static final int PIS_PASEP_LEN = 11;
    private static final int RG_NUMBER_LEN = 15;

    public static final int EXPECTED_MIN_TOTAL_LENGTH = BIRTH_DATE_LEN + CPF_LEN + PIS_PASEP_LEN + RG_NUMBER_LEN;

    public static Map<PessoaFisicaField, String> parseDadosPessoaFisica(String dataString) {
        if (dataString == null) {
            throw new IllegalArgumentException("Input string (dataString) cannot be null.");
        }

        if (dataString.length() < EXPECTED_MIN_TOTAL_LENGTH) {
            throw new IllegalArgumentException(
                    "Input string has incorrect length. Min expected: " + EXPECTED_MIN_TOTAL_LENGTH + ", Actual: " + dataString.length());
        }

        Map<PessoaFisicaField, String> resultMap = new EnumMap<>(PessoaFisicaField.class);
        int currentIndex = 0;

        // 1. Birth Date (8 positions)
        String rawBirthDate = dataString.substring(currentIndex, currentIndex + BIRTH_DATE_LEN);
        String day = rawBirthDate.substring(0, 2);
        String month = rawBirthDate.substring(2, 4);
        String year = rawBirthDate.substring(4, 8);
        String formattedBirthDate = day + "/" + month + "/" + year;
        resultMap.put(PessoaFisicaField.BIRTH_DATE, formattedBirthDate);
        currentIndex += BIRTH_DATE_LEN;

        // 2. CPF (11 positions)
        String cpf = dataString.substring(currentIndex, currentIndex + CPF_LEN);
        resultMap.put(PessoaFisicaField.CPF, cpf);
        currentIndex += CPF_LEN;

        // 3. PIS/PASEP (11 positions)
        String pisPasep = dataString.substring(currentIndex, currentIndex + PIS_PASEP_LEN);
        pisPasep = StringUtils.stripStart(pisPasep, "0");
        resultMap.put(PessoaFisicaField.PIS_PASEP, pisPasep);
        currentIndex += PIS_PASEP_LEN;

        // 4. RG Number (15 positions)
        String rgNumber = dataString.substring(currentIndex, currentIndex + RG_NUMBER_LEN);
        rgNumber = StringUtils.stripStart(rgNumber, "0");
        resultMap.put(PessoaFisicaField.RG_NUMBER, rgNumber);
        currentIndex += RG_NUMBER_LEN;

        // 5. RG Issuing Body and State
        String rgIssuingBodyState = dataString.substring(currentIndex);
        if (rgIssuingBodyState.length() == 5) {
            rgIssuingBodyState = rgIssuingBodyState.substring(0, rgIssuingBodyState.length() - 2) + "/"
                    + rgIssuingBodyState.substring(rgIssuingBodyState.length() - 2);
        }
        resultMap.put(PessoaFisicaField.RG_ISSUER, rgIssuingBodyState);

        return resultMap;
    }
}