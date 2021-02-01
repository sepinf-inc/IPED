# Exemplo de tarefa de processamento python. Deve ser adicionada em TaskInstaller.xml para ser executada.
# Devem ser implementados pelo menos os métodos getName() e process(item).
# A tarefa pode acessar as propriedades, texto e conteúdo bruto dos items. Com base nessas informações,
# pode ignorar o item, criar um atributo extra no item ou gerar bookmarks.
# Necessário instalar o jep (pip install jep==3.8.2) e incluir a jep.dll(.so) no PATH (win) ou LD_LIBRARY_PATH (Linux)
# Habilitar esta tarefa em conf/TaskInstaller.xml

# Retorna o nome da tarefa.
def getName():
	return ("PythonScriptTask")


# Realiza alguma inicialização da tarefa, como acessar opções e arquivos de configuração.
# É executado antes de iniciar o processamento dos itens do caso.
# @Params
# confProps: arquivo java Properties com opções gerais do processamento
# configFolder: diretório de configurações extras, onde a tarefa pode criar um arquivo de configuração próprio
def init(confProps, configFolder):
	#print("init")
	return


# Finaliza a tarefa, podendo limpar recursos. É executado após o término do processamento de todos os itens do caso.
# São disponibilizados os objetos ipedCase e searcher, podendo ser realizadas consultas no caso e criados bookmarks, por exemplo.
# TODO: documentar métodos desses objetos.
def finish():
	
	query = "tipo:doc"
	
	#set query into searcher
	searcher.setQuery(query)
	
	#search in case and return item ids
	ids = searcher.search().getIds()
	
	#create new bookmark and get its id
	labelId = ipedCase.getMarcadores().newLabel("DOC files")
	
	#set bookmark comment
	ipedCase.getMarcadores().setLabelComment(labelId, "Documents of DOC file format")
	
	#add item ids to created bookmark
	ipedCase.getMarcadores().addLabel(ids, labelId)
	
	#save changes
	ipedCase.getMarcadores().saveState()


# Realiza o processamento do objeto "item" da classe EvidenceFile. Esta função é executada sobre todos os itens do caso.
# Pode utilizar qualquer método da classe EvidenceFile:
#
#	Some Getters:
#	String:  getName(), getExt(), getTypeExt(), getPath(), getHash(), getMediaType().toString(), getCategories() (categorias concatenadas com | )
#	Date:    getModDate(), getCreationDate(), getAccessDate() (podem ser nulos)
#  Boolean: isDeleted(), isDuplicate(), isDir(), isRoot(), isCarved(), isSubItem(), isTimedOut(), hasChildren()
#	Long:    getLength()
#  Metadata getMetadata()
#  Object:  getExtraAttribute(String key) (obtém um atributo extra)
#  String:  getParsedTextCache() (obtém o texto extraído do item, caso esta tarefa seja posicionada após ParsingTask)
#  File:    getTempFile() (obtém um arquivo temporário com o conteúdo do item)
#  BufferedInputStream: getBufferedStream() (obtém o conteúdo do item)
#
#  Some Setters: 
#           setToIgnore(boolean) (ignora o item do processamento e não inclui no caso)
#           setAddToCase(boolean) (inclui ou não o item no caso, após ser processado)
#           addCategory(String), removeCategory(String), setMediaTypeStr(String)
# 		 	 setExtraAttribute(key, value), setParsedTextCache(String)
def process(item):
	
	# Ignore item example
	if(".dll" in item.getExt().lower()):
		item.setToIgnore(true)
	
	# Create extra attribute example
	if(".com" in item.getParsedTextCache().lower()):
		item.setExtraAttribute("containsDotCom", "true")