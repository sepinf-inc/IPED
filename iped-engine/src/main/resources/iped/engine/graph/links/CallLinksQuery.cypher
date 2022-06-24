MATCH
path = (owner1:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {category : 'Dados SIM'})-->
	(phone:TELEFONE)<--
	(call:EVIDENCIA {category : 'Chamadas'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:DATASOURCE {evidenceId : $end})
RETURN path
UNION
MATCH
path = (owner1:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(call:EVIDENCIA {category : 'Chamadas'})-->
	(phone:TELEFONE)<--
	(sim:EVIDENCIA {category : 'Dados SIM'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:DATASOURCE {evidenceId : $end})
RETURN path