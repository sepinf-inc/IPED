--['x-ufed-instantmessage', 'x-ufed-chat-whatsapp', 'x-whatsapp-msg', 'x-ufed-sms']

MATCH
	(owner1:EVIDENCIA {evidenceId : $owner1Id})-->
	(ext1:UFED)-->
	(sim1:SIMDATA)-->
	(tel:TELEFONE)
WITH distinct owner1,ext1,sim1,tel
MATCH
	(tel:TELEFONE)<--
	(message2:MESSAGE)<--
	(ext2:UFED)<--
	(owner2:EVIDENCIA {evidenceId : $owner2Id})
RETURN owner1, ext1, sim1, tel, message2, ext2, owner2
UNION
MATCH
	(owner1:EVIDENCIA {evidenceId : $owner2Id})-->
	(ext1:UFED)-->
	(sim1:SIMDATA)-->
	(tel:TELEFONE)
WITH distinct owner1,ext1,sim1,tel
MATCH
	(tel:TELEFONE)<--
	(message2:MESSAGE)<--
	(ext2:UFED)<--
	(owner2:EVIDENCIA {evidenceId : $owner1Id})
RETURN owner1, ext1, sim1, tel, message2, ext2, owner2