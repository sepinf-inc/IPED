MATCH
	path = (owner1:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA {source : 'UfedXmlReader'})-->
	(net1:EVIDENCIA {subType : 'x-ufed-wirelessnetwork'})-->
	(:MAC_ADDRESS)<--
	(net2:EVIDENCIA {subType : 'x-ufed-wirelessnetwork'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(owner2:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX net1:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX net2:EVIDENCIA(subType)
RETURN path