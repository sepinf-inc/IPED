MATCH
	path = (owner1:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA)-->
	(message1:EVIDENCIA {category : 'Emails'})-->
	(email:EMAIL)<--
	(message2:EVIDENCIA {category : 'Emails'})<--
	(ext2:EVIDENCIA)<--
	(owner2:DATASOURCE {evidenceId : $end})
RETURN path
UNION
MATCH
	path = (owner1:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA)-->
	(message1:EVIDENCIA {category : 'Caixas de Emails'})-->
	(email:EMAIL)<--
	(message2:EVIDENCIA {category : 'Caixas de Emails'})<--
	(ext2:EVIDENCIA)<--
	(owner2:DATASOURCE {evidenceId : $end})
RETURN path