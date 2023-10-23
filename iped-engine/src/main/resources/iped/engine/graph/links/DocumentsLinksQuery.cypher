MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Documentos RTF'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Documentos RTF'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Documentos PDF'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Documentos PDF'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Documentos HTML'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Documentos HTML'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Arquivos XML'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Arquivos XML'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Documentos de Texto'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Documentos de Texto'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Planilhas'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Planilhas'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Apresentações'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Apresentações'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Arquivos OLE'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Arquivos OLE'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Outros Documentos'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Outros Documentos'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Bases de dados'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Bases de dados'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path
UNION
MATCH
path =
    (ds1:DATASOURCE{evidenceId : $start})-->
    (ext1:EVIDENCIA)-->
    (doc1:EVIDENCIA {category : 'Outros Textos'})-->
    (n)
    (doc2:EVIDENCIA {category : 'Outros Textos'})<--
    (ext2:EVIDENCIA)<--
    (ds2:DATASOURCE{evidenceId : $end})
RETURN path