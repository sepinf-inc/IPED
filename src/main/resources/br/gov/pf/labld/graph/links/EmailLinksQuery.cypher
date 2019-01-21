MATCH
	path = (owner1:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(message1:EVIDENCIA {subType : 'x-ufed-email'})-->
	(email:EMAIL)<--
	(message2:EVIDENCIA {subType : 'x-ufed-email'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX message1:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX message2:EVIDENCIA(subType)
RETURN path