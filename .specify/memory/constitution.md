<!--
SYNC IMPACT REPORT
==================
Version change: (template não-preenchido) → 1.0.0
Justificativa do bump: Ratificação inicial da constituição a partir do template
placeholder, com cinco princípios derivados do CLAUDE.md raiz.

Princípios definidos (novos):
  I.   Estabilidade da API Pública (NÃO-NEGOCIÁVEL)
  II.  Extensão Modular em vez de Modificação
  III. Configuração antes de Código
  IV.  Integridade Forense e Determinismo
  V.   Disciplina de Concorrência e Isolamento de Processo

Seções adicionadas:
  - Restrições de Build, Ferramentas e Distribuição
  - Fluxo de Desenvolvimento e Gates de Qualidade
  - Governance

Seções removidas: (nenhuma — arquivo anterior era 100% placeholder)

Templates / artefatos verificados:
  - .specify/templates/plan-template.md   — ✅ alinhado (o bloco
    "Constitution Check" é resolvido por feature; gates derivam destes
    cinco princípios)
  - .specify/templates/spec-template.md   — ✅ alinhado (nenhuma seção
    obrigatória adicional exigida pela constituição)
  - .specify/templates/tasks-template.md  — ✅ alinhado (fases Setup /
    Foundational / User Stories cobrem extensão por nova task, parser,
    viewer, carver e Configurable)
  - CLAUDE.md (raiz e por módulo)         — ✅ continua sendo a referência
    operacional autoritativa; a constituição aponta para ele em vez de
    duplicar conteúdo

Follow-up TODOs: (nenhum)
-->

# IPED — Constituição do Projeto

> Documento normativo para o fork do **IPED — Indexador e Processador de
> Evidências Digitais**. Define os princípios não-negociáveis que regem
> todas as alterações neste repositório, sejam feitas por humanos ou por
> agentes de IA. Esta constituição **supera** quaisquer outras práticas
> implícitas; em caso de conflito com guias secundários, prevalece este
> documento. O `CLAUDE.md` da raiz e dos módulos é a referência
> operacional (o "como"); esta constituição estabelece o "o quê" e o
> "porquê".

## Core Principles

### I. Estabilidade da API Pública (NÃO-NEGOCIÁVEL)

O módulo `iped-api` e os contratos públicos consumidos por plugins,
forks downstream e casos antigos **NÃO PODEM** sofrer remoções ou
renomeações silenciosas. Em particular:

- Interfaces, classes públicas, enums e constantes em `iped-api` **NÃO
  PODEM** ter métodos removidos ou renomeados sem ciclo de deprecação
  documentado em `ReleaseNotes.txt`.
- Strings literais que viram chave de campo Lucene
  (`BasicProps`, `IndexItem`) são **imutáveis**: alterá-las quebra
  busca em casos antigos.
- `AppAnalyzer` é congelado pelo mesmo motivo (compatibilidade de
  índices).
- A biblioteca `iped-ahocorasick` é tratada como dependência fechada;
  modificações exigem revisão explícita e justificativa em PR.

**Justificativa**: o IPED processa casos forenses que sobrevivem por
anos; quebrar a leitura desses casos compromete a cadeia de custódia
e o valor probatório da evidência.

### II. Extensão Modular em vez de Modificação

Novas funcionalidades **DEVEM** ser adicionadas como artefatos novos
dentro do módulo apropriado, em vez de alterar comportamento existente:

- Suporte a novo formato → novo parser em `iped-parsers/iped-parsers-impl`.
- Novo visualizador → novo `AbstractViewer` em `iped-viewers/iped-viewers-impl`.
- Novo formato a recuperar → novo `Carver` em `iped-carvers/iped-carvers-impl`
  + entrada em `CarverConfig.xml`.
- Nova etapa do pipeline → nova `AbstractTask` em `iped-engine` + entrada
  em `TaskInstaller.xml`.
- Nova fonte de dados → novo `DataSourceReader` em `iped-engine`.
- Novo metadado → nova propriedade em `ExtraProperties`, **não**
  reaproveitar chaves existentes.

Modificações em componentes núcleo (`Manager`, `Worker`,
`ProcessingQueues`, `IndexWriter`, `AppAnalyzer`) **DEVEM** ser
justificadas explicitamente no PR e revisadas quanto a invariantes
de concorrência e compatibilidade de índice.

**Justificativa**: o pipeline é uma chain-of-responsibility com
dezenas de tasks compostas via configuração; alterações cirúrgicas
preservam reprodutibilidade entre versões e perfis (`forensic`, `pedo`,
`triage`, `fastmode`, `blind`).

### III. Configuração antes de Código

Todo comportamento ajustável pelo perito **DEVE** ser exposto via o
padrão `iped.configuration.Configurable<T>` (em `iped-api`) e editável
em `iped-app/resources/config/` ou em um profile, **não** hardcoded:

- Thresholds, timeouts, caminhos, flags de habilitação, listas de
  categorias e similares **DEVEM** residir em
  `IPEDConfig.txt`, `TaskInstaller.xml`, `CarverConfig.xml`,
  `CustomSignatures.xml`, `RegexConfig.txt`, `HashDBLookupConfig.txt`,
  `CategoriesConfig.json` ou no arquivo `.properties`/`.xml`/`.json` do
  Configurable correspondente.
- Profiles (`profiles/{forensic,pedo,triage,fastmode,blind}`) **DEVEM**
  ser usados para variações de pipeline, sem ramificações
  condicionais no código.
- Mensagens visíveis ao usuário **DEVEM** ser internacionalizadas em
  `iped-app/resources/localization/` (PT-BR e EN no mínimo). Strings
  hardcoded só são aceitas em logs internos.

**Justificativa**: o IPED é distribuído com vários profiles e operado
por equipes que customizam pipeline e thresholds por tipo de caso;
prender comportamento em código torna o produto inutilizável fora do
contexto original.

### IV. Integridade Forense e Determinismo

A evidência **NÃO PODE** ser corrompida por ambiguidade de plataforma,
encoding ou logging desestruturado:

- Charset **SEMPRE** explícito. UTF-8 é o default; ISO-8859-1 só
  para nomes legados de NTFS. Construtores `new String(byte[])`,
  `Reader`/`Writer` sem charset, `String.getBytes()` sem argumento e
  similares **NÃO PODEM** ser introduzidos.
- Logging via SLF4J + Log4j 2 (configurado por `Log4j2Configuration*.xml`).
  `System.out` e `System.err` **NÃO PODEM** ser usados em código de
  produção (CLI bootstrap é a única exceção controlada).
- Datas em código **DEVEM** usar `java.time` com zona explícita; nunca
  depender do default da JVM.
- Acesso ao Sleuthkit **DEVE** ser feito via `SleuthkitClient` —
  chamadas diretas a `SleuthkitJNI` são proibidas.
- Hashes, ordenação de itens e geração de IDs **DEVEM** ser
  determinísticos para o mesmo conjunto de entrada e configuração.

**Justificativa**: a saída do IPED é prova em processo judicial;
não-determinismo, dados truncados por encoding incorreto ou logs
perdidos por `System.out` comprometem a cadeia de custódia.

### V. Disciplina de Concorrência e Isolamento de Processo

O modelo de execução do engine tem invariantes rígidas que **DEVEM**
ser preservadas:

- Cada `Worker` executa em sua própria thread; cada `AbstractTask`
  tem **uma instância por worker** — campos de instância podem ser
  usados sem locks; estado global **DEVE** ir em `caseData.objectMap`
  com limpeza em `finish()`.
- Subitens **DEVEM** ser gerados via `EmbeddedDocumentExtractor.parseEmbedded(...)`
  (parsers) ou `IItem.createChildItem()` + `worker.processNewItem(item)`
  (tasks/carvers). Criar `IItem` manualmente fora desses caminhos
  é proibido.
- Threading de UI:
  - Swing → `SwingUtilities.invokeLater` ou já estar na EDT.
  - JavaFX → `Platform.runLater`.
- Componentes propensos a crash (Sleuthkit, parsing de containers
  arriscados, UI principal) **DEVEM** rodar out-of-process via os
  patterns existentes (`SleuthkitClient`/`SleuthkitServer`,
  `ParsingProcess`, `Bootstrap` lançando JVM filha).

**Justificativa**: o IPED processa centenas de milhões de itens por
caso e 400 GB/h; quebrar o modelo de workers introduz contenção que
mata a vazão, e perder isolamento de processo derruba o caso inteiro
quando uma DLL nativa quebra em um único arquivo malformado.

## Restrições de Build, Ferramentas e Distribuição

- **Java 11 com JavaFX** (Liberica/BellSoft Full JDK).
  `maven.compiler.source/target = 11`. Não introduzir APIs de Java
  posteriores; não introduzir dependência de JavaFX além do que já
  está embarcado via `JFXPanel`.
- **Build**: Maven 3.6+ multi-módulo a partir do `pom.xml` raiz
  (versão atual `4.4.0-SNAPSHOT`). Submódulos herdam a versão; não
  fixar versões divergentes em filhos.
- **Encoding fonte**: UTF-8.
- **Branch padrão**: `master` (instável, dev). Releases em tags.
- **Ferramentas externas** (Sleuthkit, ImageMagick, Tesseract,
  LibreOffice, MPlayer, RegRipper, libpff, libesedb, evtxexport,
  rifiuti2, GraphViz, JRE embarcado) são distribuídas em `tools/`,
  `jre/` e `plugins/` do release; **não** assumir disponibilidade no
  PATH do sistema.
- **Licenciamento**: toda dependência nova **DEVE** ser registrada em
  `ThirdParty.txt` e ter sua licença anexada em `licenses/`.
- **CI** (`.github/workflows/maven.yml`) é a referência para o ambiente
  Linux mínimo; mudanças que exijam novas dependências nativas
  **DEVEM** atualizar o workflow no mesmo PR.

## Fluxo de Desenvolvimento e Gates de Qualidade

1. **Antes de editar**: ler o `CLAUDE.md` do módulo afetado; usar
   `Grep` para localizar implementações e consumidores de qualquer
   símbolo público que será tocado.
2. **Durante a edição**: respeitar os cinco princípios acima.
   Javadocs e comentários em PT-BR existentes **DEVEM** ter o idioma
   preservado ao editar; novos comentários seguem a convenção do
   arquivo em que entram.
3. **Antes de commit**:
   - `mvn -pl <módulo> -am install` no módulo afetado (e em
     `iped-app` se a mudança puder impactar o release).
   - `mvn test` no módulo se houver cobertura relevante.
   - Atualizar o `CLAUDE.md` do módulo **se** contratos, dependências
     ou padrões mudaram.
4. **Pull Request**: descrever impacto em compatibilidade de índice
   (Princípio I), em pipeline (Princípio II) e em concorrência
   (Princípio V) quando aplicável. Mudanças em
   `iped-api`, `Manager`, `Worker`, `ProcessingQueues`, `IndexWriter`,
   `AppAnalyzer` ou `iped-ahocorasick` exigem revisão explícita e
   justificativa.
5. **Spec Kit**: features grandes seguem o fluxo
   `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` →
   `/speckit-tasks` → `/speckit-implement`. O bloco
   "Constitution Check" do `plan-template.md` é resolvido a partir
   desta constituição: cada princípio é um gate.

## Governance

- Esta constituição **supera** quaisquer práticas tácitas ou
  preferências individuais. Em caso de conflito com `CLAUDE.md`
  (raiz ou módulo), a constituição prevalece para o "o quê"; o
  `CLAUDE.md` permanece autoritativo para o "como" operacional
  (paths, comandos, convenções de estilo).
- **Emendas**: alterações nesta constituição **DEVEM** ser feitas
  via PR dedicado contendo (a) o diff do arquivo
  `.specify/memory/constitution.md`, (b) o **Sync Impact Report**
  atualizado no cabeçalho HTML, (c) atualização dos templates
  dependentes em `.specify/templates/` quando necessário, e (d)
  bump da versão semântica conforme regra abaixo.
- **Versionamento da constituição** (SemVer):
  - **MAJOR**: remoção ou redefinição incompatível de um princípio
    ou regra de governance.
  - **MINOR**: adição de princípio, seção ou expansão material de
    orientação existente.
  - **PATCH**: clarificações, correções de redação, refinamentos
    não-semânticos.
- **Revisão de conformidade**: revisores de PR **DEVEM** verificar
  conformidade com os cinco princípios antes de aprovar. Qualquer
  desvio justificado deve ser registrado no PR (não na constituição).
- **Guidance file de runtime**: o `CLAUDE.md` da raiz e os
  `CLAUDE.md` de cada módulo fornecem orientação operacional para
  agentes de IA e desenvolvedores. Esta constituição os referencia;
  não os substitui.

**Version**: 1.0.0 | **Ratified**: 2026-05-19 | **Last Amended**: 2026-05-19
