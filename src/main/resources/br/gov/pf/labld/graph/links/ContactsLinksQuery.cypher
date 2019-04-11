MATCH
path =  (owner1:DATASOURCE {evidenceId : $start})-->
    (ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
    (g:CONTACT_GROUP)<--
    (ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
    (owner2:DATASOURCE {evidenceId : $end})
RETURN path