MATCH
path =  (owner1:EVIDENCIA {evidenceId : $start})-->
    (ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
    (con1:EVIDENCIA)-->
    (tel:TELEFONE)<--
    (con2:EVIDENCIA)<--
    (ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
    (owner2:EVIDENCIA {evidenceId : $end})
    USING INDEX ext1:EVIDENCIA(source)
    USING INDEX ext2:EVIDENCIA(source)
WHERE
con1.subType in ['x-ufed-contact', 'x-whatsapp-contactsv2', 'x-whatsapp-contact'] AND
con2.subType in ['x-ufed-contact', 'x-whatsapp-contactsv2', 'x-whatsapp-contact']
RETURN path
UNION
MATCH
path =  (owner1:EVIDENCIA {evidenceId : $start})-->
    (ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
    (con1:EVIDENCIA)-->
    (fb:FACEBOOK)<--
    (con2:EVIDENCIA)<--
    (ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
    (owner2:EVIDENCIA {evidenceId : $end})
    USING INDEX ext1:EVIDENCIA(source)
    USING INDEX ext2:EVIDENCIA(source)
WHERE
con1.subType in ['x-ufed-contact', 'x-ufed-useraccount'] AND
con2.subType in ['x-ufed-contact', 'x-ufed-useraccount']
RETURN path