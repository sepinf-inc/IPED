MATCH
path =	(owner1:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(phone:TELEFONE)<--
	(call:EVIDENCIA { subType : 'x-ufed-call'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX call:EVIDENCIA(subType)
RETURN path
UNION
MATCH
path=	(owner1:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(call:EVIDENCIA { subType : 'x-ufed-call'})-->
	(phone:TELEFONE)<--
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX call:EVIDENCIA(subType)
RETURN path