MATCH
	path = (:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(msg:EVIDENCIA {subType : 'x-ufed-sms'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX msg:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(msg:EVIDENCIA {subType : 'x-ufed-sms'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $start})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX msg:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $start})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-whatsapp-msg'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-whatsapp-msg'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $start})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat-preview'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat-preview'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $start})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $start})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat-whatsapp'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $end})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path
UNION
MATCH
	path = (:EVIDENCIA {evidenceId : $end})-->
	(ext1:EVIDENCIA{source : 'UfedXmlReader'})-->
	(sim:EVIDENCIA {subType : 'x-ufed-simdata'})-->
	(:TELEFONE)<--
	(chat:EVIDENCIA {subType : 'x-ufed-chat-whatsapp'})<--
	(ext2:EVIDENCIA {source : 'UfedXmlReader'})<--
	(:EVIDENCIA {evidenceId : $start})
	USING INDEX ext1:EVIDENCIA(source)
	USING INDEX sim:EVIDENCIA(subType)
	USING INDEX ext2:EVIDENCIA(source)
	USING INDEX chat:EVIDENCIA(subType)
RETURN path