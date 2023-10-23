MATCH
	path = (owner1:DATASOURCE {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(net1:EVIDENCIA {category : 'Redes sem fio'})-->
	(:MAC_ADDRESS)<--
	(net2:EVIDENCIA {category : 'Redes sem fio'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:DATASOURCE {evidenceId : $end})
RETURN path