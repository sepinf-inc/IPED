# IPED-API

Este projeto visa definir uma API para acesso aos recursos e dados do IPED, além
de facilitar a implementação de novos recursos sem a necessidade de conhecer o
funcionamento interno da ferramenta.

Esse projeto é um *brainstorming* da API e, portanto, 
deve definir **apenas interfaces** (e caso necessário enums, constantes e classes abstratas).

Não deve ser realizada nenhuma implementação nesse momento, até porque,
para algumas dessas interfaces, já existem implementações concorrentes em vários projetos.

É mais provável, portanto, que as implementações existentes tenham que se adaptar
às interfaces do que uma nova implementação ser feita.

> Atenção: atualizem livremente as interfaces, mas deixem comentários para os casos de uso menos óbvios.

> Atenção: funcionalidades futuras ou aproveitadas de outros projetos (ex.: AsAP) podem ser propostas

Os principais recursos que a API deve prover/facilitar são:
* acesso às informações do caso (ex.: localização das fontes de dados)
* acesso aos dados e metadados das fontes de dados (ex.: texto extraído, miniatura da imagem, etc.)
* controle da aplicação
 * executar um script/processamento adicional
 * realizar buscas textuais
* gerenciar bookmarks
* geração de relatório
* acesso a configurações
* adição de novos recursos ("plugins")
 * assinaturas de arquivos
 * parsers (ex.: como registrar, configurar, ativar/desativar um parser)
 * criação de subitens na árvore de
 * visualizações
 