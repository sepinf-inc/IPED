MATCH
	path = (:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {category : 'Dados SIM'})-->
	(:TELEFONE)<--
	(msg:EVIDENCIA {category : 'Mensagens SMS'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:DATASOURCE {evidenceId : $end})
RETURN path
UNION
MATCH
	path = (:DATASOURCE {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {category : 'Dados SIM'})-->
	(:TELEFONE)<--
	(msg:EVIDENCIA {category : 'Mensagens SMS'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:DATASOURCE {evidenceId : $start})
RETURN path
UNION
MATCH
	path = (:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {category : 'Dados SIM'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {category : 'Chats'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:DATASOURCE {evidenceId : $end})
RETURN path
UNION
MATCH
	path = (:DATASOURCE {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {category : 'Dados SIM'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {category : 'Chats'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:DATASOURCE {evidenceId : $start})
RETURN path