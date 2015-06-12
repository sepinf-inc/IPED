# IPED (site em contrução)

Software forense computacional para exame completo de mídias de armazenamento de dados.

Desenvolvido com foco na velocidade de processamento e num exame detalhado em profundidade sem perder a simplicidade de análise.
Resumo das principais funcionalidades:
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
