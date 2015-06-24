# IPED (em contrução)

Software forense computacional para exame completo de mídias de armazenamento de dados.
Licenciado sob GNU GPL v3

Desenvolvido em java, com foco na velocidade de processamento e num exame detalhado e em profundidade sem perder a simplicidade de análise. Resumo das principais funcionalidades:
- Decodificação de imagens dd, 001, e01 e iso via Sleuthkit 4.2 e Libewf
- Acesso a arquivos apagados e espaço não alocado (via Sleuthkit)
- Categorização por análise de assinatura e propriedades e filtro por categoria (ver seção Categorização)
- Expansão de containers (ver seção Expansão de Containers)
- Indexação (ver seção Indexação) e pesquisa por palavras-chave no conteúdo e propriedades dos arquivos.
- Data Carving eficiente sobre itens não alocados e alocados (ver seção Data Carving)
- Visualização em árvore dos dados (não implementada para relatórios, atualmente)
- Cálculo de hash e filtro de duplicados
- OCR de imagens e PDFs e detecção de imagens contento textos como digitalizações (metadado OcrCharCount)
- Detecção de documentos cifrados
- Consulta a base de hashes (KFF) para alertar ou ignorar arquivos
- Visualização integrada de dezenas de formatos.
- Visualizador de texto filtrado para qualquer formato.
- Galeria multithread para visualizar miniaturas de dezenas de formatos de imagens (via Image/GraphicsMagick)
- Geração de miniaturas de vídeos (contribuição PCF Wladimir)
- Ordenação por propriedades, como nome, tipo, datas e caminho.
- Marcação, exportação e cópia de propriedades dos arquivos
- Geração de arquivo CSV com as propriedades de todos os itens
- Extração automática de categorias para casos de extração automática de dados
- Extração e reindexação de itens selecionados pela interface de pesquisa após análise do perito
- Geração de relatório HTML (contribuição PCF Wladimir)

## Configuração

O principal arquivo de configuração da ferramenta é o IPEDConfig.txt. Lá pode ser configurado o cálculo de hash, diretório temporário indexTemp (recomenda-se configurar num SSD), indexação do conteúdo dos arquivos (indexFileContents), indexação do espaço não alocado (indexUnallocated), cálculo de assinatura (processFileSignatures), carving (enableCarving), expansão de containers (expandContainers), dentre diversas outras opções comentadas no próprio arquivo. A seguir são detalhadas algumas configurações importantes.

## Categorização
A categorização dos arquivos é realizada principalmente via análise de assinatura pela biblioteca Apache Tika. A biblioteca retorna o mimeType do arquivo, o qual é mapeado para uma categoria conforme configurado no arquivo conf/CategoriesByTypeConfig.txt. Caso deseje definir um novo tipo (mimeType) de arquivo por assinatura, nome ou extensão, adicione a definição no arquivo conf/CustomSignatures.jar/org/apache/tika/mime/custom-mimetypes.xml.

Além disso, a categoria dos arquivos pode ser refinada com base em qualquer propriedade, como caminho, tamanho, datas, deletado, etc. Para isso, utilize o arquivo conf/CategoriesByPropsConfig.txt, o qual utiliza linguagem javascript para permitir flexibilidade nas definições.

## Detecção de Arquivos Criptografados
A partir da versão 3.3 é realizada automaticamente detecção de arquivos criptografados dos seguintes tipos: pdf, office97 (doc, xls, ppt), office2007 (docx, xlsx, pptx), openoffice (odt, ods, odp), zip, rar, 7z e pst. Os arquivos identificados como cifrados são classificados na categoria "Arquivos Criptografados".

## Expansão de Containers
Para a expansão de containers, é utilizada a biblioteca Apache Tika, que fornece suporte para zip, tar, ar, jar, gzip, bzip, bzip2, xz, 7z, cpio, dump, formatos office, rtf e pdf. Além disso, a biblioteca foi extendida e foram implementados parsers para dbx, pst, mbox, eml, rar. Entretanto, atualmente a implementação tem a limitação de não recuperar emails apagados de dentro de dbx e pst.

A expansão de containers fica habilitada por padrão. As categorias a serem expandidas podem ser alteradas no arquivo conf/CategoriesToExpand.txt. A expansão extrai os subitens para a pasta "Exportados" e é recursiva, podendo utilizar espaço considerável. O hash dos arquivos é usado como nomenclatura para economizar espaço.

Para máximo desempenho, recomenda-se configurar uma pasta de saída (-o) do processamento num disco diferente daquele que contém as imagens/dados sendo processados, para minimizar acessos concorrentes de leitura dos dados e escrita dos subitens expandidos.

## Consulta a Base de Hashes (KFF)
Na versão 3.3 foi incluída função de consulta a base de hashes local para alertar ou ignorar arquivos. Podem ser importadas bases NSRL do NIST, a qual é armazenada em formato pré-indexado para consultas. Descompacte a base e importe o diretório via parâmetro -importkff. É Altamente recomendado configurar a base num disco SSD, sob pena de degradar o tempo de processamento, sendo necessário configurar o caminho da base (opção kffDb) no arquivo de configuração. Os arquivos encontrados na base são adicionados a categorias específicas de alerta ou irrelevância, sendo que os ignoráveis podem ser excluídos do caso se habilitado (excludeKffIgnorable). É possível alterar a lista de programas cujos arquivos devem receber o status de alerta no arquivo conf/KFFTaskConfig.txt.

Também há uma função específica para consultar hashes na base de pornografia infantil no LED, bastando configurar o caminho da base no arquivo de configuração principal (ledWkffPath). A vantagem é que a base pode ser atualizada facilmente, sem necessidade de importação. Os arquivos encontrados nessa base são adicionados a categoria de alerta específica.

## Indexação
Antes da indexação com a biblioteca Apache Lucene, é realizada a extração de texto dos arquivos com a biblioteca Apache Tika. Dentre os formatos suportados, podem ser citados: MS Office (doc, docx, xls, xlsx, ppt, pptx e similares), OpenOffice (odt, ods, etc), Apple iWork (key, pages, numbers), PDF, HTML e XML, RTF e TXT, e-mails (RFC822 e Outlook MSG) e metadados de audio (midi, mp3), imagens (bmp, jpg, psd, png, tif, etc) e vídeos (flv, mp4 e derivados e ogg e derivados), dentre outros.

Além disso, foram criados parsers adicionais, como MS Access, xBase, SQLite, além de um extrator de strings brutas ISO-8859-1, UTF-8 e UTF-16 utilizado como fallBackParser com todos os demais tipos de arquivo não suportados pelo TIKA, como binários, desconhecidos, corrompidos e espaço não alocado.

É possível habilitar/desabilitar parsers no arquivo conf/ParserConfig.xml, desabilitar a indexação de binários e desconhecidos (indexUnknownFiles) ou desligar a indexação do espaço não alocado (indexUnallocated), o que pode ser interessante dependendo do volume de dados (como em casos de triagem de dados - SARD), pois deixa o processamento mais rápido, resulta num índice muito menor e não apresenta hits de difícil interpretação em arquivos como pagefile, system restore, espaço não alocado, etc.

A partir da versão 3.3, foi incluído teste de aleatoriedade (entropia) antes de indexar trechos de arquivos desconhecidos ou não alocado, o que melhora bastante a eficiência de indexação desses arquivos. Entretanto, eventualmente podem ser perdidos hits cercados por conteúdo "aleatório".

## OCR
O OCR utiliza o programa Tesseract 3.02 e é executado sobre imagens (jpg, gif, tif, png e bmp) e arquivos PDF com pouco texto, fazendo parte do processo de indexação. É um processamento pesado que utilizada bastante a CPU, podendo demorar alguns segundos por imagem. Por isso fica desabilitado por padrão e pode ser habilitado no arquivo IPEDConfig.txt (enableOCR). Os resultados podem variar bastante, dependendo da qualidade e resolução das imagens, tamanho e tipo das fontes utilizadas, mas tem se mostrado superior ao FTK, que utiliza uma versão antiga do Tesseract sem detecção de rotação e sem dicionário para o português.

O número de caracteres reconhecidos é armazenado no metadado OCRCharCount, permitindo localizar imagens contendo textos, como digitalizações, com pesquisas como ocrcharcount????* a qual retorna imagens com 1000 ou mais caracteres reconhecidos.

## Data Carving
Para ativar o carving, habilite o parâmetro enableCarving no arquivo IPEDConfig.txt. No arquivo conf/CarvingConfig.txt (enableCarving) há opções para incluir ou excluir arquivos do processamento, por exemplo, realizar carving apenas sobre o espaço não alocado e/ou sobre itens alocados (por exemplo, pagefile, thumbs.db, system restore, executáveis, desconhecidos, etc). A configuração padrão é bastante inclusiva, excluindo basicamente os containers com expansão suportada para evitar a recuperação de itens duplicados.

Atualmente estão configuradas assinaturas para os seguintes tipos de arquivo: bmp, emf, gif, png, jpg, html, pdf, office, index.dat, zip e derivados (office 2007, OpenOffice, iWork, xps, etc), eml e seus anexos, podendo ser adicionadas novas assinaturas no arquivo conf/CarvingConfig.txt. Atualmente não são recuperados itens de dentro de volume shadow copies pois o sleuthkit não decodifica esses arquivos corretamente.

O algoritmo de carving utilizado é bastante eficiente e não degrada com o número de assinaturas pesquisadas, levando o mesmo tempo caso buscadas 1 ou 10000 assinaturas. É proporcional apenas ao volume de dados processado e ao número de assinaturas encontradas (e não de pesquisadas), conseguindo a atingir taxas entre 300 a 700 GB/h de carving, geralmente limitado pelo I/O.

## Miniaturas de Vídeos
(contribuição PCF Wladimir)

Na versão 3.4 foi incluída função para extração de cenas de vídeos (enableVideoThumbs), a qual utiliza o software MPlayer. Os parâmetros da extração de cenas, como resolução, número de linhas e colunas, podem ser alterados no arquivo conf/VideoThumbsConfig.txt. As miniaturas de vídeos também são exibidas na galeria de imagens, sendo recomendado "diminuir" o número de itens exibidos na galeria para aumentar o tamanho das cenas dos vídeos, permitindo uma boa visualização.

## Linux
O programa é distribuído com todas as dependências necessárias para execução em ambiente Windows. Para execução em ambiente Linux, é necessário instalar as seguintes dependências:
- LibEwf e Sleuthkit versão 4.2 (baixar do github e compilar atualmente). Não utilize versões anteriores do sleuthkit, pois tinham um bug que fazia a decodificação de algumas imagens demorar horas, além de às vezes reconstruir a árvore de diretórios incorretamente
- Pacote Tesseract versão >= 3.02 e os dicionários português, inglês e OSD (o qual detecta rotação nas imagens). Após comente o parâmetro TesseractPath no arquivo IPEDConfig.txt
- Pacote GraphicsMagick (apenas para análise), para permitir a visualização de dezenas de formatos de imagens não suportadas pelo Java (o qual decodifica apenas bmp, gif, png e jpg). É possível utilizar o ImageMagick (desabilitar param useGM em IPEDConfig.txt), porém o primeiro consegue renderizar imagens JPG parciais, como recuperadas via carving. Nenhum deles renderiza imagens PNG parciais.
- LibreOffice 4 (apenas para análise), para permitir a visualização das dezenas de formatos suportados por essa suite office.
- Oracle Java versão 7.06 ou superior. É possível utilizar o OpenJDK, porém este não inclui o Webkit do JavaFX, ficando desabilitado o visualizador HTML e derivados (EML, emails de PST, etc)
- MPlayer para geração de miniaturas de vídeos. Recomenda-se a versão 4.9.2. Necessário configurar o caminho para o mplayer no arquivo conf/VideoThumbsConfig.txt.

É recomendável desabilitar o swap ou diminuir a tendência do kernel em fazê-lo (swappiness = 10). A maioria das distribuições privilegia o cache de IO e como são lidos mtos gigabytes das imagens, os processos em execução, inclusive o IPED, podem ser paginados para o disco.

No Linux, a execução de processos externos (tesseract e graphicsMagick) pode copiar parte da memória do processo original durante o fork, o que pode causar problemas quando executada frequentemente a partir de processos que ocupem muita memória (IPED). Por isso, recomenda-se limitar a heap do Java ao executar o processamento: java -Xmx3G ou -Xmx6G são o mínimo e máximo recomendados para um computador com 12 processadores.
Utilização

## Processamento
A execução é via linha de comando, o que permite automatizar o processamento de diversos casos ou evidências sequencialmente. Recomenda-se utilizar um java x64, que permite um maior uso de memória, principalmente em computadores com mais de 4 núcleos de processamento.

Uso: java -jar iped.jar -opcao argumento [--opcao_sem_argumento]
- -d: dados diversos (pode ser usado varias vezes): pasta, imagem DD, 001, E01, AFF (apenas linux), ISO, disco físico, ou arquivo *.iped (contendo seleção de itens a exportar e reindexar)
- -dname: nome (opcional) para imagens ou discos físicos adicionados via -d
- -o: pasta de saida da indexacao
- -r: pasta do relatorio do AsAP3 ou FTK3
- -c: nome do caso, apenas para relatorio do FTK3
- -l: arquivo com lista de expressoes a serem exibidas na busca. Expressoes sem ocorrencias sao filtradas
- -ocr: aplica OCR apenas na categoria informada. Pode ser usado varias vezes.
- -log: Especifica um arquivo de log diferente do padrao
- -asap: arquivo .asap (Criminalistica) com informacoes para relatorio HTML
- -importkff: importa diretorio com base de hashes no formato NSRL
- -Xxxx: parâmetros extras de módulos iniciados com -X
- --append: adiciona indexação a um indice ja existente
- --nogui: nao exibe a janela de progresso da indexacao
- --nologfile: imprime as mensagem de log na saida padrao
- --verbose: gera mensagens de log detalhadas, porem diminui desempenho

Exemplo:
java -jar iped.jar -d imagem.dd -o pasta_saída

## Análise
Acessível pelo executável "Ferramenta de Pesquisa.exe" ou pelo arquivo indexador/lib/iped-search-app.jar. Necessário possuir instalado o Java JRE (recomendado java 64bits), sendo necessário o Java 7 update 06 para habilitar o visualizador Html e EML.

A interface de análise dispõe das seguintes funcionalidades:
- Pesquisa indexada no conteúdo e propriedades dos arquivos
- Painel de fragmentos dos arquivos com ocorrências
- Tabela de resultados ordenável por propriedades
- Visualização em árvore dos dados, recursiva ou não
- Atribuição de múltiplos marcadores textuais, exportação e cópia de propriedades dos arquivos, via menu de contexto
- Filtro por categoria, marcador e de duplicados
- Galeria multithread para exibição de miniaturas de dezenas de formatos de imagem (via GraphicsMagick) e miniaturas de vídeos
- Visualizador para dezenas de formatos: html, pdf, eml, emlx, rtf, doc, docx, xls, xlsx, ppt, pptx, odt, ods, odp, wps, wpd, sxw, eps, dbf, csv, tif, emf, wmf, odg, pcx, pbm, svg, pict, vsd, psd, cdr, dxf, etc.
- Visualizador de texto filtrado para qualquer formato

ATENÇÃO, pois sempre são listados na tabela os arquivos resultantes da INTERSEÇÃO da busca por palavras-chave, filtro de categoria, filtro de duplicados e do filtro via árvore de diretórios.

## Extração de Arquivos de Interesse
### Automática
Essa funcionalidade deve ser utilizada com cautela e seu uso indiscriminado não é recomendado. Para realizar extração automática de arquivos por categoria, descomente as categorias de interesse no arquivo conf/CategoriesToExport.txt antes do processamento. Nesse caso, os arquivos são exportados e apenas eles indexados, podendo o resultado do processamento ser enviado para o solicitante da extração de dados, via mídia óptica ou magnética.

### Selecionados pelo Perito
Após atribuir marcadores aos arquivos (opcional), os arquivos de interesse a serem exportados devem ser selecionados (via checkbox) na interface de análise. Essa seleção pode ser salva via função "Salvar marcadores" em um arquivo *.iped, ex: Report.iped. Posteriormente, via terminal, forneça esse arquivo (ou o arquivo padrão indexador/marcadores.iped) como parâmetro para uma nova indexação:

java -jar iped.jar -d Report.iped -o pasta_relatorio

Os arquivos selecionados serão exportados e reindexados para facilitar a revisão dos dados pelo solicitante do exame.

### Relatório HTML
(contribuição PCF Wladimir)

A partir da versão 3.4, no caso de extração de arquivos de interesse (automática ou de selecionados), por padrão é gerado um relatório HTML com propriedades e links para os arquivos extraídos. Nesse relatório é incluída uma galeria de imagens e também miniaturas dos vídeos (caso geradas). Também são geradas versões de visualização para itens com parser apropriado. Alguns parâmetros do relatório podem ser alterados no arquivo conf/HTMLReportConfig.txt

## Consumo de Memória e Performance

Recomenda-se utilizar uma versão x64 do java, que permite um maior uso de memória, quando necessário, pois a heap padrão do java x86 é de apenas 256MB, insuficiente para processar imagens. No caso de problemas de falta de memória, aumente a memória heap do java (-Xms) ou diminua o parâmetro "numthreads" no arquivo conf/AdvancedConfig.txt.

Por padrão (numThreads = default), cada processador lógico executa uma thread de processamento, que geralmente consome 250MB de memória (max de ~500 MB). Ressalta-se que o java 64 bits evita limpar a heap caso haja memória RAM disponível para a JVM e pode ocupar mais memória do que necessário pela aplicação, diminuindo a memória livre do sistema e, consequentemente, o cache de I/O. Portanto recomenda-se utilizar o parâmetro -Xmx para limitar a memória da JVM utilizando a regra citada: num_processadores*512MB. Por exemplo, numa máquina com 12 núcleos: java -Xmx6G -jar iped.jar -d imagem.dd -o pasta_saida.

O processamento completo (assinatura, hash, expansão, indexação e carving) geralmente varia de 100 GB/h a 300 GB/h num computador moderno, quando bem configurado o ambiente de processamento. Quando não habilitado o OCR, geralmente o gargalo é o I/O do disco que contém a imagem sendo processada, por isso é recomendado armazenar as imagens num RAID com múltiplos discos.

Recomenda-se configurar uma pasta de saída (-o) do processamento num disco diferente daquele que contém as imagens sendo processadas, para minimizar acessos concorrentes de leitura dos dados e escrita de subitens expandidos.

Sempre que possível configure o diretório temporário (indexTemp no arquivo IPEDConfig.txt) num disco rápido, diferente daquele que contém os dados, fora do disco de sistema e livre de antivírus, preferencialmente num SSD. Também indique se o indexTemp encontra-se num disco SSD ou não (indexTempOnSSD). Caso indicado, são feitas otimizações que podem diminuir o tempo de processamento para menos da metade: o número de threads de merges do índice é aumentado e, no caso de imagens compactadas E01, são gerados arquivos temporários para todos os itens para evitar múltiplas descompactações dos itens pela LIBEWF, a qual não é multithread e efetua apenas uma descompactação por vez, subaproveitando processadores com vários núcleos.

Também é altamente recomendado armazenar a base KFF num disco SSD, sob pena de degradar o tempo de processamento.

O programa foi otimizado para suportar casos na ordem de 10 milhões de itens com poucos giga de memória, havendo degradação principalmente na ordenação das propriedades atualmente.

## Considerações finais

A precisão dos resultados têm sido considerada satisfatória para atender aos objetivos propostos, mas nunca será 100%, pois há uma infinidade de tipos de arquivos a tratar. Por isso, os resultados da ferramenta podem diferir dos resultados de outras ferramentas forenses, podendo haver diferença tanto para mais quanto para menos. Por exemplo, atualmente há diferenças de configuração que podem resultar num menor número de itens no caso em relação a outras ferramentas, o que pode ser equivocadamente interpretado como uma deficiência do software numa análise superficial. Note que a inclusão de muitos itens inúteis no caso pode dificultar a análise ao invés de ajudar. Abaixo são citadas algumas das diferenças:
- não são incluídos fileSlacks e trechos não alocados são menos fragmentados;
- arquivos de carving claramente corrompidos, menores que 1KB ou maiores que 10MB são ignorados na configuração padrão;
- não são expandidos históricos de Internet nem arquivos JAR (que podem produzir dezenas de milhares de itens .class);

Caso sejam identificadas divergências importantes, entre em contato e relate o problema para que ele possa ser corrigido.
