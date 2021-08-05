package br.gov.pf.iped.regex.cripto;

public class Bech32AddressValidator {

    private int[] charset_rev;
    private int[] generator;

    public Bech32AddressValidator(int[] charset_rev, int[] generator) {
        this.charset_rev = charset_rev;
        this.generator = generator;
    }

    private int bech32Polymod(int[] values) {
        int chk = 1;
        int top = 0;

        for (int value : values) {
            top = chk >>> 25;
            chk = (chk & 0x1ffffff) << 5 ^ value;
            for (int i = 0; i < 5; i++) {
                if (((top >>> i) & 1) != 0) {
                    chk ^= generator[i];
                }
            }
        }

        return chk;
    }

    private int[] bech32ExpandData(String valueString) {
        char[] value = valueString.toCharArray();
        int hrpSize = valueString.lastIndexOf('1');
        int[] data = new int[value.length + hrpSize];

        data[hrpSize] = 0;
        for (int i = 0; i < hrpSize; i++) {
            data[i] = ((int) value[i]) >> 5;
            data[i + hrpSize + 1] = ((int) value[i]) & 31;
        }
        
        for (int i = 0; i < value.length - hrpSize - 1; i++) {
            data[i + (hrpSize*2 + 1)] = charset_rev[(int) value[i + hrpSize + 1]];
        }

        return data;
    }

    public boolean bech32VerifyChecksum(String value) {
        return bech32Polymod(bech32ExpandData(value)) == 1;
    }

}
