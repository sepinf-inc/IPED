package dpf.sp.gpinf.indexer.process.task.regex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.process.task.regex.RegexTask.Regex;

public class RegexValidation {
    
    /* Variáveis para verificação de números */
    private static final int[] pesoCpf = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] pesoCnpj = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] pesoPisPasep = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] pesoBoleto = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 
                                            9, 8, 7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 
                                            5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 
                                            9, 8, 7, 6, 5, 4, 3, 2};
    
    private static final List<String> codigoPais = Arrays.asList("AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI",
            "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV",
            "AW", "AX", "AY", "AZ", "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI",
            "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV",
            "BW", "BX", "BY", "BZ", "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI",
            "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV",
            "CW", "CX", "CY", "CZ", "DA", "DB", "DC", "DD", "DE", "DF", "DG", "DH", "DI",
            "DJ", "DK", "DL", "DM", "DN", "DO", "DP", "DQ", "DR", "DS", "DT", "DU", "DV",
            "DW", "DX", "DY", "DZ", "EA", "EB", "EC", "ED", "EE", "EF", "EG", "EH", "EI",
            "EJ", "EK", "EL", "EM", "EN", "EO", "EP", "EQ", "ER", "ES", "ET", "EU", "EV",
            "EW", "EX", "EY", "EZ", "FA", "FB", "FC", "FD", "FE", "FF", "FG", "FH", "FI",
            "FJ", "FK", "FL", "FM", "FN", "FO", "FP", "FQ", "FR", "FS", "FT", "FU", "FV",
            "FW", "FX", "FY", "FZ", "GA", "GB", "GC", "GD", "GE", "GF", "GG", "GH", "GI",
            "GJ", "GK", "GL", "GM", "GN", "GO", "GP", "GQ", "GR", "GS", "GT", "GU", "GV",
            "GW", "GX", "GY", "GZ", "HA", "HB", "HC", "HD", "HE", "HF", "HG", "HH", "HI",
            "HJ", "HK", "HL", "HM", "HN", "HO", "HP", "HQ", "HR", "HS", "HT", "HU", "HV",
            "HW", "HX", "HY", "HZ", "IA", "IB", "IC", "ID", "IE", "IF", "IG", "IH", "II",
            "IJ", "IK", "IL", "IM", "IN", "IO", "IP", "IQ", "IR", "IS", "IT", "IU", "IV",
            "IW", "IX", "IY", "IZ", "JA", "JB", "JC", "JD", "JE", "JF", "JG", "JH", "JI",
            "JJ", "JK", "JL", "JM", "JN", "JO", "JP", "JQ", "JR", "JS", "JT", "JU", "JV",
            "JW", "JX", "JY", "JZ", "KA", "KB", "KC", "KD", "KE", "KF", "KG", "KH", "KI",
            "KJ", "KK", "KL", "KM", "KN", "KO", "KP", "KQ", "KR", "KS", "KT", "KU", "KV",
            "KW", "KX", "KY", "KZ", "LA", "LB", "LC", "LD", "LE", "LF", "LG", "LH", "LI",
            "LJ", "LK", "LL", "LM", "LN", "LO", "LP", "LQ", "LR", "LS", "LT", "LU", "LV",
            "LW", "LX", "LY", "LZ", "MA", "MB", "MC", "MD", "ME", "MF", "MG", "MH", "MI",
            "MJ", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU", "MV",
            "MW", "MX", "MY", "MZ", "NA", "NB", "NC", "ND", "NE", "NF", "NG", "NH", "NI",
            "NJ", "NK", "NL", "NM", "NN", "NO", "NP", "NQ", "NR", "NS", "NT", "NU", "NV",
            "NW", "NX", "NY", "NZ", "OA", "OB", "OC", "OD", "OE", "OF", "OG", "OH", "OI",
            "OJ", "OK", "OL", "OM", "ON", "OO", "OP", "OQ", "OR", "OS", "OT", "OU", "OV",
            "OW", "OX", "OY", "OZ", "PA", "PB", "PC", "PD", "PE", "PF", "PG", "PH", "PI",
            "PJ", "PK", "PL", "PM", "PN", "PO", "PP", "PQ", "PR", "PS", "PT", "PU", "PV",
            "PW", "PX", "PY", "PZ", "QA", "QB", "QC", "QD", "QE", "QF", "QG", "QH", "QI",
            "QJ", "QK", "QL", "QM", "QN", "QO", "QP", "QQ", "QR", "QS", "QT", "QU", "QV",
            "QW", "QX", "QY", "QZ", "RA", "RB", "RC", "RD", "RE", "RF", "RG", "RH", "RI",
            "RJ", "RK", "RL", "RM", "RN", "RO", "RP", "RQ", "RR", "RS", "RT", "RU", "RV",
            "RW", "RX", "RY", "RZ", "SA", "SB", "SC", "SD", "SE", "SF", "SG", "SH", "SI",
            "SJ", "SK", "SL", "SM", "SN", "SO", "SP", "SQ", "SR", "SS", "ST", "SU", "SV",
            "SW", "SX", "SY", "SZ", "TA", "TB", "TC", "TD", "TE", "TF", "TG", "TH", "TI",
            "TJ", "TK", "TL", "TM", "TN", "TO", "TP", "TQ", "TR", "TS", "TT", "TU", "TV",
            "TW", "TX", "TY", "TZ", "UA", "UB", "UC", "UD", "UE", "UF", "UG", "UH", "UI",
            "UJ", "UK", "UL", "UM", "UN", "UO", "UP", "UQ", "UR", "US", "UT", "UU", "UV",
            "UW", "UX", "UY", "UZ", "VA", "VB", "VC", "VD", "VE", "VF", "VG", "VH", "VI",
            "VJ", "VK", "VL", "VM", "VN", "VO", "VP", "VQ", "VR", "VS", "VT", "VU", "VV",
            "VW", "VX", "VY", "VZ", "WA", "WB", "WC", "WD", "WE", "WF", "WG", "WH", "WI",
            "WJ", "WK", "WL", "WM", "WN", "WO", "WP", "WQ", "WR", "WS", "WT", "WU", "WV",
            "WW", "WX", "WY", "WZ", "XA", "XB", "XC", "XD", "XE", "XF", "XG", "XH", "XI",
            "XJ", "XK", "XL", "XM", "XN", "XO", "XP", "XQ", "XR", "XS", "XT", "XU", "XV",
            "XW", "XX", "XY", "XZ", "YA", "YB", "YC", "YD", "YE", "YF", "YG", "YH", "YI",
            "YJ", "YK", "YL", "YM", "YN", "YO", "YP", "YQ", "YR", "YS", "YT", "YU", "YV",
            "YW", "YX", "YY", "YZ", "ZA", "ZB", "ZC", "ZD", "ZE", "ZF", "ZG", "ZH", "ZI",
            "ZJ", "ZK", "ZL", "ZM", "ZN", "ZO", "ZP", "ZQ", "ZR", "ZS", "ZT", "ZU", "ZV",
            "ZW", "ZX", "ZY", "ZZ");
    
    static boolean checkVerificationCode(Regex regex, String hit){
        
        if(regex.name.equals("CPF"))
            return verifyCpf(hit);
        else if(regex.name.equals("CNPJ"))
            return verifyCnpj(hit);
        else if(regex.name.equals("TITULO_ELEITOR"))
            return verifyTituloEleitor(hit);
        else if(regex.name.equals("CNH"))
            return verifyCnh(hit);
        else if(regex.name.equals("PISPASEP"))
            return verifyPisPasep(hit);
        else if(regex.name.equals("CARTAO_CREDITO"))
            return verifyCreditCard(hit);
        else if(regex.name.equals("BOLETO"))
            return verifyBoleto(hit);
        else if(regex.name.equals("IBAN"))
            return verifyIban(hit);
        else if(regex.name.equals("SWIFT"))
            return verifySwift(hit);
        
        return true;
    }
    
    private static boolean verifyCreditCard(String cartao) {
        cartao = cartao.replaceAll("[ \\.\\-]", "");
        if(isCharsEqual(cartao))
            return false;
        int soma = 0;
        boolean par = false;
        // Calcula primeiro digito verificador
        for (int indice = cartao.length() - 1; indice >= 0; indice--) {
            int n = Integer.parseInt(cartao.substring(indice, indice + 1));
            if (par) {
                n *= 2;
                if (n > 9)
                    n = (n % 10) + 1;
            }
            soma += n;
            par = !par;
        }
        return (soma % 10 == 0);
    }
    
    private static boolean verifyCpf(String cpf){
        cpf = cpf.replaceAll("[\\.\\-\\s]", "");
        if(isCharsEqual(cpf.substring(0, cpf.length() - 2)))
            return false;
        int digit1 = calcDigito(cpf.substring(0,9), pesoCpf);
        int digit2 = calcDigito(cpf.substring(0,9) + digit1, pesoCpf);
        return cpf.substring(9).equals("" + digit1 + digit2);
    }
    
    private static final boolean isCharsEqual(final String str){
        for(char c : str.toCharArray())
            if(c != str.charAt(0))
                return false;
        return true;
    }
    
    private static boolean verifyCnpj(String cnpj) {
        cnpj = cnpj.replaceAll("[\\.\\-/\\s]", "");
        if(isCharsEqual(cnpj.substring(0, cnpj.length() - 2)))
            return false;
        int digito1 = calcDigito(cnpj.substring(0,12), pesoCnpj);
        int digito2 = calcDigito(cnpj.substring(0,12) + digito1, pesoCnpj);
        return cnpj.substring(12).equals("" + digito1 + digito2);
    }
    
    private static boolean verifyPisPasep(String pisPasep) {
        pisPasep = pisPasep.replaceAll("[\\.\\-\\s]", "");
        if(isCharsEqual(pisPasep.substring(0, pisPasep.length() - 1)))
            return false;
        Integer digito = calcDigito(pisPasep.substring(0,10), pesoPisPasep);
        return pisPasep.substring(10).equals(digito.toString());
    }
    
    private static boolean verifySwift(String swift) {
        if (codigoPais.contains(swift.substring(4, 6)))
            return true;
        else
            return false;
    }
    
    private static boolean verifyIban(String iban) {
        if (codigoPais.contains(iban.substring(0, 2))) {
            String ibanNum = ibanCleaner(iban.substring(4) + iban.substring(0, 4));
            BigInteger bigInt = new BigInteger(ibanNum);
            return bigInt.remainder(new BigInteger("97")).intValue() == 1;
        }
        return false;
    }
    
    private static String ibanCleaner(String iban) {
        for (int i = 65; i <= 90; i++) {
            int replaceWith = i - 55;
            String replace = Character.toString((char)i);
            iban = iban.replace(replace, Integer.toString(replaceWith));
        }
        return iban;
    }
    
    private static boolean verifyCnh(String cnh) {
        
        cnh = cnh.replaceAll("[\\.\\-\\s]", "");
        if(isCharsEqual(cnh.substring(0, cnh.length() - 2)))
            return false;
        
        int soma = 0;
        int digito1;
        int digito2;
        int incrDigito2 = 0;
        
        // Calcula primeiro digito verificador
        for (int indice = 0, peso = 9, digito; indice < 9; indice++, peso-- ) {
           digito = Integer.parseInt(cnh.substring(indice,indice+1));
           soma += digito*peso;
        }
        
        soma = soma % 11;

        if (soma > 9) {
            digito1 = 0;
            incrDigito2 = 2;
        } else
            digito1 = soma;
        
        // Calcula segundo digito verificador
        soma = 0;
        for (int indice = 0, peso = 1, digito; indice < 9; indice++, peso++ ) {
           digito = Integer.parseInt(cnh.substring(indice,indice+1));
           soma += digito*peso;
        }

        soma = soma % 11;

        if (soma > 9)
            digito2 = 0;
        else
            digito2 = soma - incrDigito2;

        return cnh.equals(cnh.substring(0, 9) + digito1 + digito2);
    }
    
    private static boolean verifyTituloEleitor(String titulo) {
        
        if(isCharsEqual(titulo.substring(0, titulo.length() - 2)))
            return false;
        
        int soma = 0;
        int digito1;
        int digito2;
        
        // Calcula primeiro digito verificador
        for (int indice = 0, peso = 2, digito; indice <= 7; indice++, peso++ ) {
           digito = Integer.parseInt(titulo.substring(indice,indice+1));
           soma += digito*peso;
        }

        soma = soma % 11;

        if (soma > 9)
            digito1 = 0;
        else
            digito1 = soma;

        // Calcula segundo digito verificador
        soma = 0;
        for (int indice = 8, peso = 7, digito; indice <= 10; indice++, peso++ ) {
           digito = Integer.parseInt(titulo.substring(indice,indice+1));
           soma += digito*peso;
        }

        soma = soma % 11;

        if (soma > 9)
            digito2 = 0;
        else
            digito2 = soma;

        return titulo.equals(titulo.substring(0, 10) + digito1 + digito2);
    }
    
    private static boolean verifyBoleto(String boleto) {
        
        boleto = boleto.replaceAll("[\\.\\-\\s]", "");
        if(isCharsEqual(boleto))
            return false;
        
        int soma = 0;
        // Valida primeiro dígito verificador
        int digito1 = calcDigitoBoleto(boleto.substring(0,9));
        int digito2 = calcDigitoBoleto(boleto.substring(10,20));
        int digito3 = calcDigitoBoleto(boleto.substring(21,31));
        // Calcula o quarto dígito verificador a partir do código de barra
        // O quarto dígito verificador nunca pode ser 0, nesse caso deve ser 1
        int digito4 = calcDigito(boleto.substring(0,4) +
                                    boleto.substring(33, 47) +
                                    boleto.substring(4,9) +
                                    boleto.substring(10, 11) +
                                    boleto.substring(11, 20) +
                                    boleto.substring(21, 31), pesoBoleto);
        
        return boleto.equalsIgnoreCase(boleto.substring(0, 9) + digito1 +
                                       boleto.substring(10, 20) + digito2 +
                                       boleto.substring(21, 31) + digito3 +
                                       (digito4 == 0 ? 1 : digito4) + boleto.substring(33, 47));
    }
    
    private static int calcDigitoBoleto(String campo) {
        int soma = 0;
        int multiplicador = 2;
        int resultadoParcial;
        int dezenaSuperior;
        
        for (int indice = campo.length()-1, digito; indice >= 0; indice-- ) {
           digito = Integer.parseInt(campo.substring(indice,indice+1));
           resultadoParcial= digito*multiplicador;
           if (resultadoParcial > 9) {
               soma += Integer.parseInt(Integer.toString(resultadoParcial).substring(0, 1)) + 
                       Integer.parseInt(Integer.toString(resultadoParcial).substring(1, 2));
           } else {
               soma += resultadoParcial;
           }
           if (multiplicador == 2) {
               multiplicador = 1;
           } else {
               multiplicador = 2;
           }
        }
        
        soma = 10 - soma % 10;
        return soma > 9 ? 0 : soma;
    }
    
    private static int calcDigito(String numero, int[] peso) {
        int soma = 0;
        for (int indice = numero.length() - 1; indice >= 0; indice-- ) {
           int digito = Integer.parseInt(numero.substring(indice, indice+1));
           soma += digito * peso[peso.length - numero.length() + indice];
        }
        soma = 11 - soma % 11;
        return soma > 9 ? 0 : soma;
    }

}
