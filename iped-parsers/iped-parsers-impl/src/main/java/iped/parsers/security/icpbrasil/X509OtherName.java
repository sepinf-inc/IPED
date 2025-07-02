package iped.parsers.security.icpbrasil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.DirectoryString;
import org.bouncycastle.asn1.x509.OtherName;
import org.bouncycastle.util.encoders.Hex;

import iped.parsers.security.icpbrasil.PessoaFisicaDataParser.PessoaFisicaField;

public class X509OtherName extends OtherName {

    /**
     * https://www.gov.br/iti/pt-br/central-de-conteudo/doc-icp-04-01-versao-3-3-atribuicao-de-oid-na-icp-brasil-pdf
     */
    private static final ASN1ObjectIdentifier OTHER_NAME_ATTRIBUTES = new ASN1ObjectIdentifier("2.16.76.1");

    public String decodedValue;

    private X509OtherName(OtherName other, String decodedValue) {
        super(other.getTypeID(), other.getValue());
        this.decodedValue = decodedValue;
    }

    public String getDecodedValue() {
        return decodedValue;
    }

    public boolean isICPBrasil() {
        return getTypeID().on(OTHER_NAME_ATTRIBUTES);
    }

    public boolean hasPessoaFisicaData() {
        return StringUtils.equalsAny(getTypeID().toString(), "2.16.76.1.3.1", "2.16.76.1.3.4");
    }

    public Map<PessoaFisicaField, String> getPessoaFisicaData() {
        if (!hasPessoaFisicaData()) {
            throw new IllegalStateException("check hasPessoaFisicaData() before");
        }
        return PessoaFisicaDataParser.parseDadosPessoaFisica(decodedValue);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Nonnull
    public static X509OtherName fromByteArray(byte[] otherNameBytes) {

        ASN1TaggedObject taggedValue = ASN1TaggedObject.getInstance(otherNameBytes);

        if (taggedValue.getTagNo() != 0) {
            throw new IllegalArgumentException(
                    "OtherName value part must be explicitly tagged with context-specific tag 0. Found tag: " + taggedValue.getTagNo());
        }

        OtherName otherName = OtherName.getInstance(taggedValue.getBaseObject());

        if (otherName == null) {
            throw new IllegalArgumentException("taggedValue.getBaseObject() not valid");
        }

        ASN1Encodable valuePrimitive = otherName.getValue();
        String valueString;
        try {
            valueString = DirectoryString.getInstance(valuePrimitive).getString();
        } catch (Exception e) {

            if (valuePrimitive instanceof ASN1String) {
                valueString = ((ASN1String) valuePrimitive).getString();
            } else if (valuePrimitive instanceof DEROctetString) {
                valueString = new String(((DEROctetString) valuePrimitive).getOctets(), StandardCharsets.UTF_8);
            } else {
                try {
                    valueString = Hex.toHexString(valuePrimitive.toASN1Primitive().getEncoded());
                } catch (IOException ioe) {
                    throw new IllegalArgumentException("otherName value not encodable", ioe);
                }
            }
        }

        // value containing only zeros are not defined
        if (StringUtils.containsOnly(valueString, '0')) {
            valueString = null;
        }

        return new X509OtherName(otherName, valueString);
    }

}