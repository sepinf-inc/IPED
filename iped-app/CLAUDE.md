# Módulo `iped-app`

> **Empacotador final + UI Swing principal do IPED.** É aqui que tudo se junta: orquestração de bootstrap (JVM filha + classpath dinâmico), entry points (CLI de processamento e UI de análise), o `JFrame` principal (`App`), visualizações especializadas (graph Neo4j, timeline JFreeChart) e o pipeline Maven que monta o release com JRE, ferramentas externas, modelos AI e configs.

> Mudanças aqui afetam o produto **final** que o usuário roda. UI e ergonomia importam tanto quanto a corretude. Trate como camada de apresentação acoplada ao engine.

## 1. Propósito

- **Empacotar o release** (`target/release/iped-${version}/`) com tudo que o usuário precisa: JAR principal, JARs de dependência, JRE embarcado, ferramentas externas (Sleuthkit, Tesseract, ImageMagick, LibreOffice, MPlayer, RegRipper, libpff/libesedb/evtxexport/sccainfo, GraphViz...), modelos AI (DIE, Yahoo NSFW, Vosk pt/en), Python+JEP, plugins.
- **Definir os 4 entry points** principais:
  - `iped.app.bootstrap.Bootstrap` — disparador da CLI de processamento.
  - `iped.app.bootstrap.BootstrapUI` — disparador da UI de análise de caso pronto.
  - `iped.app.processing.Main` — processo filho do `Bootstrap`; orquestra ingestão + Manager (engine).
  - `iped.app.ui.AppMain` — processo filho do `BootstrapUI`; sobe a UI Swing.
- **Implementar a UI Swing principal** (`App.java`, ~2500 linhas) com tabela de resultados, gallery, árvores (filesystem/categorias/bookmarks/IA), painéis (metadata, filtros, evidência), aba de viewers, busca por imagem/face similar, exportação, relatórios.
- **Hospedar visualizações dockáveis** (DockingFrames): mapa (de `iped-geo`), grafo Neo4j (`graph/`), timeline (`timelinegraph/`), filtros AI, viewers do `iped-viewers`.

Versão `4.4.0-SNAPSHOT`. Java 11 + JavaFX. Empacotamento: `jar`. Main-class no `META-INF/MANIFEST.MF`: `iped.app.bootstrap.Bootstrap`.

## 2. Estrutura

```
iped-app/
├── pom.xml                              # empacotamento + 25+ executions do dependency-plugin
├── resources/                            # tudo é copiado para target/release/
│   ├── config/
│   │   ├── IPEDConfig.txt               # flags de feature do processamento
│   │   ├── LocalConfig.txt              # paths/threads/hashDB locais
│   │   ├── conf/                        # ~40 arquivos de config modular
│   │   └── profiles/{forensic,pedo,triage,fastmode,blind}/
│   ├── scripts/
│   │   ├── tasks/                       # *.js, *.py (PythonScriptTask, CSAMDetectorTask, ...)
│   │   ├── parsers/                     # PythonParserExample.py, RegistryParser plugin
│   │   └── regex_validators/
│   ├── localization/                    # iped-app.properties + locales (pt_BR, es_AR, de_DE, fr_FR, it_IT)
│   ├── plugins/                         # vazio por padrão (drop-in)
│   ├── root/                            # bin/, help/, htmlreport/, iped.exe (launchers)
│   └── checkstyle/                      # apenas para o build, não distribuído
└── src/main/java/iped/app/
    ├── bootstrap/                       # Bootstrap, BootstrapUI
    ├── config/                          # LogConfiguration, XMLResultSetViewerConfiguration
    ├── processing/                      # Main (entry), CmdLineArgsImpl, ui/ProgressFrame, ui/ProgressConsole
    ├── metadata/                        # MetadataSearch, ValueCount, RangeCount, MoneyCount, LookupOrd
    ├── graph/                           # AppGraphAnalytics (Neo4j+Kharon) + renderers/
    ├── timelinegraph/                   # IpedChartsPanel, IpedXYPlot, eixos, swingworkers, cache
    └── ui/                              # App + ~150 classes Swing
        ├── ai/                          # filtros de IA (nudity/CSAM)
        ├── bookmarks/                   # ícones/cell renderers
        ├── columns/                     # ColumnsManager + UI
        ├── controls/                    # CSelButton, CustomButton, CheckBoxTree + table/
        ├── filters/                     # FiltersPanel, FiltererMenu
        ├── filterdecisiontree/          # operadores AND/OR/NOT
        ├── parallelsorter/              # ParallelTableRowSorter
        ├── popups/                      # FieldValuePopupMenu
        ├── splash/                      # SplashScreenManager + StartUpControl(Client)
        ├── themes/                      # ThemeManager + Theme
        ├── utils/                       # UiScale (DPI), PanelsLayout (persist do layout)
        └── viewers/                     # TextViewer, HexSearcherImpl, AttachmentSearcherImpl
```

Total: ~250 arquivos Java.

## 3. Entry points e Bootstrap

### 3.1 `Bootstrap` ([`iped/app/bootstrap/Bootstrap.java`](src/main/java/iped/app/bootstrap/Bootstrap.java))

Lança uma **JVM filha** com classpath e flags montados dinamicamente. Responsabilidades:

1. **Parsing de `-Xms`/`-Xmx`** — valida contra RAM física, clamp a 32500 MB; default = `(factor) × RAM` (factor = 0.25 para `Bootstrap`, 0.5 para `BootstrapUI`).
2. **Detecção de JRE** — embarcado em `${root}/jre/` (Windows) ou `java.home` do sistema. Mostra warning de versão via `Util.getJavaVersionWarn()`.
3. **Carregamento de configs** — `Configuration.getInstance().loadConfigurables(configPath, false)`.
4. **Classpath dinâmico**:
   - `iped.jar` (ou `lib/iped-search-app.jar` quando `iped.ui.report` está setado).
   - `pluginConfig.getTskJarFile()` — JAR do Sleuthkit.
   - `pluginConfig.getPluginFolder() + "/*"` — todos os JARs em `plugins/`.
   - JARs do UNO (LibreOffice) via `LibreOfficeFinder` + `UNOLibFinder.addUNOJars(...)`.
5. **Flags JVM fixas** (sempre passadas):
   ```
   -XX:+IgnoreUnrecognizedVMOptions
   -XX:+HeapDumpOnOutOfMemoryError
   --add-opens=java.base/java.util=ALL-UNNAMED
   --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
   --add-opens=java.base/java.lang=ALL-UNNAMED
   --add-opens=java.base/java.math=ALL-UNNAMED
   --add-opens=java.base/java.net=ALL-UNNAMED
   --add-opens=java.base/java.io=ALL-UNNAMED
   --add-opens=java.base/java.nio=ALL-UNNAMED
   --add-opens=java.base/java.text=ALL-UNNAMED
   --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
   ```
   No Windows: `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` (issue #1719) + `-Djava.net.useSystemProxies=true` (#1446).
6. **Workaround JDWP** (debug) — se a JVM pai está com `agentlib:jdwp ... server=y`, incrementa a porta para evitar conflito (PR #1119).
7. **Redirect de streams** — `redirectStream()` daemon threads copiam stdin/stdout/stderr entre pai e filho. Linhas iniciadas com `IpedSubProcessTempFolder: ` são capturadas para limpeza de temp em shutdown.
8. **Splash** — `configLoaded()` (overridável) chama `new SplashScreenManager().start()`.

**Hooks de subclasse**:
- `isToDecodeArgs()` — `true` em `Bootstrap`, `false` em `BootstrapUI`.
- `getDefaultClassPath(Main iped)` — root da cadeia de classpath.
- `getMainClassName()` — qual classe rodar na JVM filha.
- `getRAMToHeapFactor()` — proporção de heap default.
- `configLoaded()` — hook pós-load de configs (splash, etc.).

### 3.2 `BootstrapUI` ([`iped/app/bootstrap/BootstrapUI.java`](src/main/java/iped/app/bootstrap/BootstrapUI.java))

Subclasse pequena. Diferenças:
- `isToDecodeArgs()` → `false` (sem CLI parsing — apenas abre caso).
- Classpath default = `lib/iped-search-app.jar`.
- `getMainClassName()` → `iped.app.ui.AppMain`.
- Heap factor = `0.5f`.

### 3.3 `Main` ([`iped/app/processing/Main.java`](src/main/java/iped/app/processing/Main.java))

Roda na JVM filha. Orquestra o **processamento de caso**:
1. Parsing CLI via `CmdLineArgsImpl` (JCommander).
2. `Configuration.getInstance().loadConfigurables(...)` — todos `Configurable<?>` do engine + parsers.
3. `LogConfiguration` redireciona logs para `IPED-Processing.log` no caso.
4. `Manager` (engine) é instanciado e executado.
5. UI de progresso:
   - `ProgressFrame` (Swing) — barras, gráfico de throughput, lista de evidências.
   - `ProgressConsole` (texto) quando `--nogui`.
6. Eventos atualizam UI via `UIPropertyListenerProvider` (event bus).

Construtores adicionais para integração com **ASAP** (sistema de gerenciamento de casos da PF): recebem `List<File> reports`, `output`, `configPath`, `logFile`, `keywordList` direto.

Argumentos CLI principais (em `CmdLineArgsImpl`):
- `-d /path/to/source` — fonte de dados (repetível).
- `-o /path/to/case` — pasta de saída.
- `-l keywords.txt` — lista inicial de palavras-chave.
- `-profile {forensic|pedo|triage|fastmode|blind}` — perfil.
- `-nogui` — sem UI Swing.
- `-nologfile` — sem arquivo de log.
- `-ocr {category}` — OCR seletivo.
- `-asap report.xml` — modo ASAP.
- `-tz America/Sao_Paulo` — timezone das datas FAT.
- `-remove evidenceName` / `--append` / `--continue` / `--restart`.

### 3.4 `AppMain` ([`iped/app/ui/AppMain.java`](src/main/java/iped/app/ui/AppMain.java))

Roda na JVM filha de `BootstrapUI`. Sobe a UI:
1. Detecta `casePath`: se o JAR está em `{case}/iped/lib/iped-search-app.jar`, usa o caso parent automaticamente.
2. Suporta `-multicases multicases.txt` (lista de casos).
3. Inicializa logger em `IPED-SearchApp.log`.
4. `UiScale.loadUserSetting()` — escala DPI conforme `LocalConfig`.
5. `Configuration.getInstance().loadConfigurables(...)`.
6. `App.get().init(logConfig, isMultiCase, casesPathFile, processingManager, libDir)`.
7. `UICaseDataLoader.execute()` — carrega caso em `SwingWorker`.
8. Opcionalmente recebe `Manager processingManager` para permitir UI durante processamento (modo "interactive").
9. Se Windows e `AnalysisConfig.getCopyJREToUserHome()`, copia JRE para `~/.iped/jre-${version}/` em thread daemon (evita lock em runs paralelos).

## 4. `App` — JFrame principal

[`iped/app/ui/App.java`](src/main/java/iped/app/ui/App.java) — singleton (`App.get()`), `JFrame`, implementa `WindowListener`, `IMultiSearchResultProvider`, `GUIProvider`.

### Componentes principais

**Tabelas**:
- `resultsTable` (`HitsTable`) — tabela central com `ResultTableModel`.
- `subItemTable`, `parentItemTable`, `duplicatesTable`, `referencesTable`, `referencedByTable` — tabelas auxiliares (subitens, pai, duplicatas por hash, referências cruzadas).
- `gallery` (`GalleryTable`) — visualização em galeria com `GalleryModel`.

**Árvores**:
- `tree` — filesystem (com `TreeListener`, `TreeViewModel`).
- `categoriesTree` (`CategoryTreeModel`, `CategoryTreeListener`).
- `bookmarksTree` (`BookmarksTreeModel`, `BookmarksTreeListener`).
- `aiFiltersTree` (`AIFiltersTreeListener`).

**Painéis**:
- `metadataPanel` — metadados do item selecionado.
- `filtersPanel` (`FiltersPanel`) — filtros salvos e ativos.
- `evidencePanel` — detalhes da evidência.

**Busca/filtros**:
- `QueryComboBox`, `FilterComboBox` — histórico de queries e filtros.
- `FilterManager` — combinação AND/OR/NOT de filtros.
- `SimilarImagesFilterPanel` + `SimilarImagesFilterer`.
- `SimilarFacesFilterPanel` + `SimilarFacesSearchFilterer`.
- `SimilarDocumentFilterer`, `DuplicatesFilterer`.

**Visualização**:
- `ViewerController` — orquestra `MultiViewer` (de `iped-viewers`) + viewers específicos.
- `AppGraphAnalytics` — grafo Neo4j interativo.
- `IpedChartsPanel` — timeline.
- Aba "Map" — fornecida por `MapViewer` (em `iped-geo`).

**Docking**: `dockingControl` (`CControl` da `DockingFrames`) hospeda todos os `DefaultSingleCDockable`s. Layout é persistido via `PanelsLayout` em `ui/utils/`.

### Ciclo de vida típico

```
App.get().init(...)
   ├─ SwingUtilities.invokeAndWait(this::createGUI)
   │   ├─ inicializa JTable/JTree/JPanel
   │   ├─ dockingControl.addDockable(...) para cada aba
   │   ├─ registerKeyBindings()
   │   └─ ThemeManager.applyTheme()
   └─ UICaseDataLoader (SwingWorker)
       ├─ appCase = new IPEDMultiSource(...)
       ├─ resultsTable.setModel(...)
       └─ firePropertyChange("iped", null, appCase)
```

## 5. Visualizações especializadas

### `graph/` — Análise de comunicações (Neo4j)

Renderização interativa via **Kharon** (`com.github.filipesimoes:kharon:0.1.6`).

Classes-chave:
- `AppGraphAnalytics` — componente Swing principal; integra com Neo4j via `GraphService` (engine).
- `GraphModel` — converte nós Neo4j em `Node` da Kharon (sizing, labels, types).
- `GraphSidePanel`, `GraphStatusBar`, `GraphToolBar` — UI auxiliar.
- `GraphVizIpedResolver` — usa GraphViz (em `tools/graphviz/`) para layouts.
- Renderers por tipo de nó em [`graph/renderers/`](src/main/java/iped/app/graph/renderers): `PersonNodeRenderer`, `EmailNodeRenderer`, `PhoneNodeRenderer`, `CarNodeRenderer`, `MoneyNodeRenderer`, etc.
- Ações: `ExpandSelectedAction`, `ConnectSelectedAction`, `FindPathsAction`, `SearchLinksDialog`, `ApplyGraphLayoutAction`, `FilterSelectedEdges`, `ExportImageAction`, `ExportLinksAction`/`ExportLinksWorker`.
- Workers: `AddNodeWorker`, `AddRelationshipWorker`.

### `timelinegraph/` — Timeline

Estende JFreeChart via dependência custom `com.github.sepinf-inc:jfreechartextensions`.

Classes-chave:
- `IpedChartsPanel` — painel com múltiplos plots.
- `IpedChartPanel` — chart individual; `IpedXYPlot`, `IpedCombinedDomainXYPlot`.
- Eixos customizados: `IpedDateAxis`, `IpedHourAxis`, `IpedNumberAxis`.
- Renderers: `IpedStackedXYBarRenderer`.
- Cache: `EventTimestampCache`, `TimelineCache`, [`cache/persistance/PersistedArrayList`](src/main/java/iped/app/timelinegraph/cache/persistance) — armazenamento em disco para datasets grandes.
- Interação: `IpedTimelineMouseWheelHandler`.
- Subpacotes `datasets/`, `dialog/`, `popups/`, `swingworkers/`.

### `metadata/` — Agregações para filtros

Estatísticas sobre valores únicos por campo (counts, ranges, money).
- `MetadataSearch`, `MetadataSearchable` (marker).
- `ValueCount`, `SingleValueCount`, `RangeCount`, `MoneyCount`.
- `ValueCountQueryFilter`, `LookupOrd`.

## 6. Build e empacotamento

[`iped-app/pom.xml`](pom.xml) é o pom **mais complexo** do projeto. Estratégia: depender de **artefatos zip** (publicados em `https://gitlab.com/iped-project/iped-maven` e jitpack) que contêm binários nativos, e usar `maven-dependency-plugin` para descompactar em `target/release/`.

### Dependências Maven (jar) diretas

```xml
<dependency> <groupId>iped</groupId> <artifactId>iped-engine</artifactId> </dependency>
<dependency> <groupId>iped</groupId> <artifactId>iped-geo</artifactId>    </dependency>
<dependency> <groupId>com.beust</groupId>           <artifactId>jcommander</artifactId>            <version>1.72</version>   </dependency>
<dependency> <groupId>org.dockingframes</groupId>   <artifactId>docking-frames-common</artifactId> <version>1.1.2</version>  </dependency>
<dependency> <groupId>com.github.filipesimoes</groupId> <artifactId>kharon</artifactId>            <version>0.1.6</version>  </dependency>
<dependency> <groupId>com.toedter</groupId>          <artifactId>jcalendar</artifactId>             <version>1.4</version>    </dependency>
<dependency> <groupId>com.github.sepinf-inc</groupId><artifactId>jfreechartextensions</artifactId> <version>5d6b903...</version></dependency>
```

### Artefatos descompactados (`maven-dependency-plugin` executions)

| Execution ID | Artefato | Versão | Destino |
|---|---|---|---|
| `unpack-jre` | `java:jre` | `11.0.13` | `${release.dir}` |
| `unpack-python` | `org.python:python-jep-dlib` | `3.9.12-4.0.3-19.23.1-2` | `${release.dir}` |
| `unpack-esedbexport` | `libyal:libesedb` | `20151213.1` | `${tools.dir}` |
| `unpack-pffexport` | `libyal:libpff` | `20131028` | `${tools.dir}` |
| `unpack-msiecfexport` | `libyal:libmsiecf` | `20160421.1` | `${tools.dir}` |
| `unpack-agdbinfo` | `libyal:libagdb` | `20181111.1` | `${tools.dir}` |
| `unpack-evtexport` | `libyal:evtexport` | `20180317.1` | `${tools.dir}` |
| `unpack-evtxexport` | `libyal:evtxexport` | `20170122.1` | `${tools.dir}` |
| `unpack-sccainfo` | `libyal:sccainfo` | `20240427` | `${tools.dir}` |
| `unpack-rifiuti` | `abelcheung:rifiuti2` | `0.7.0` | `${tools.dir}` |
| `unpack-imagemagick` | `org.imagemagick:imagemagick-zip` | `7.1.1-43-q8-x64+dlls` | `${tools.dir}` |
| `unpack-tesseract` | `tesseract:tesseract-zip` | `5.3.2-24-g3922_1` | `${tools.dir}` |
| `unpack-mplayer` | `mplayer:mplayer-zip` | `6.2.0-r38109` | `${tools.dir}` |
| `unpack-sleuthkit-dlls` | `org.sleuthkit:sleuthkit-dlls` | `4.11.1-p3` | `${tools.dir}` |
| `unpack-graphviz` | `graphviz:graphviz` | `2.38` | `${tools.dir}/graphviz` |
| `unpack-die` | `led:die` | `20230125` | `${release.dir}/models` |
| `unpack-vosk-model-en` | `vosk:vosk-model-small-en-us` | `0.15` | `${release.dir}/models/vosk` |
| `unpack-vosk-model-pt` | `vosk:vosk-model-small-pt` | `0.3` | `${release.dir}/models/vosk` |
| `unpack-nativeview` | `libre-office:nativeview-dll` | `1.0.1` | `${lib.dir}` |
| `copy-libreoffice` | `libre-office:libreoffice` | `6.1.5-p3` | `${tools.dir}/libreoffice.zip` |
| `copy-caffviewer` | `de.caff:caffviewer` | `3.13.07` | `${tools.dir}/caffviewer` |
| `copy-stanfordCoreNLP` | `edu.stanford.nlp:stanford-corenlp` | `3.8.0` | `${plugin.dir}` |
| `copy-java-dbx` | `net.sf:java-dbx` | `1.1-p6` | `${plugin.dir}` |
| `copy-telegram-decoder` | `com.github.sepinf-inc.telegram-decoder:telegram-decoder-impl` | `1.0.14` | `${plugin.dir}` |
| `copy-nsfw-model` | `yahoo:nsfw-keras` | `1.0.0` | `${release.dir}/models` |
| `download-regripper` | RegRipper3.0 (via GitHub, `download-maven-plugin`) | `3.0-p3` | `${tools.dir}/regripper` |

### Outputs do `maven-jar-plugin`

| Execução | Saída | Main-Class |
|---|---|---|
| `create-jar` | `${release.dir}/iped.jar` | `iped.app.bootstrap.Bootstrap` |
| `create-search-jar` | `${lib.dir}/iped-search-app.jar` | `iped.app.bootstrap.BootstrapUI` |
| `create-webapi-jar` | `${lib.dir}/iped-webapi.jar` | `iped.engine.webapi.Main` |
| `create-hashdb-jar` | `${lib.dir}/iped-hashdb.jar` | `iped.engine.hashdb.HashDBTool` (apenas `**/hashdb/*`) |

`copy-dependencies` joga todos os JARs em `${lib.dir}`. Splash-screen é `iped/app/splash/iped.png` (embutido).

### `maven-resources-plugin` executions

- `copy-resources` — `resources/root` + `LICENSE.txt`/`ThirdParty.txt`/`ReleaseNotes.txt` em `${release.dir}`.
- `copy-localization` — `resources/localization/` em `${release.dir}/localization`.
- `copy-licenses` — `licenses/` em `${release.dir}/licenses`.
- `copy-config` — `resources/config/` em `${release.dir}/` (vira `IPEDConfig.txt`, `LocalConfig.txt`, `conf/`, `profiles/`).
- `copy-plugin-resources` — `resources/plugins/` em `${plugin.dir}`.
- `copy-scripts` — `resources/scripts/` em `${release.dir}/scripts`.

### Resultado final em `target/release/iped-4.4.0/`

```
├── iped.jar                         (Bootstrap)
├── iped.exe (Windows launcher)
├── jre/                             (JRE embutido)
├── lib/
│   ├── iped-search-app.jar          (BootstrapUI)
│   ├── iped-webapi.jar              (Web API)
│   ├── iped-hashdb.jar              (HashDB CLI)
│   ├── nativeview/                  (NOA native libs)
│   └── *.jar                        (todas as deps)
├── tools/                           (binários nativos descompactados)
├── conf/                            (configs)
├── profiles/                        (presets)
├── scripts/{tasks,parsers,regex_validators}/
├── localization/
├── plugins/
├── models/                          (DIE, NSFW, Vosk)
├── licenses/
├── bin/, help/, htmlreport/
├── LICENSE.txt, ThirdParty.txt, ReleaseNotes.txt
└── python/                          (do JEP)
```

## 7. UI: convenções e padrões

| Sufixo | Significado | Exemplos |
|---|---|---|
| `*Manager` | Gerencia ciclo de vida/estado | `BookmarksManager`, `FilterManager`, `ColumnsManager`, `IconManager`, `ThemeManager` |
| `*Controller` | Orquestra lógica | `ViewerController`, `FilterTableHeaderController`, `BookmarksController` |
| `*Listener` | `ActionListener`/`MouseListener`/`*Listener` | `ResultTableListener`, `TreeListener`, `HitsTableListener`, `GalleryListener` |
| `*Model` | `TableModel`/`TreeModel` | `ResultTableModel`, `CategoryTreeModel`, `GalleryModel`, `DuplicatesTableModel` |
| `*Renderer` | Renderização Swing | `TableCellRenderer`, `TreeCellRenderer`, `BookmarkTreeCellRenderer`, `CategoryTreeCellRenderer` |
| `*Dialog` | `JDialog` | `HashDialog`, `ReportDialog`, `ExportLinksDialog` |
| `*Panel` | `JPanel` | `MetadataPanel`, `FiltersPanel`, `GraphSidePanel` |
| `*Action` | `AbstractAction` (menu/toolbar) | `ExpandSelectedAction`, `FindPathsAction`, `ExportImageAction` |
| `*Worker` | `SwingWorker` async | `UICaseDataLoader`, `AddNodeWorker`, `ExportLinksWorker` |
| `*Filterer` | Combina filtros (`IResultSetFilterer`) | `SimilarImagesFilterer`, `DuplicatesFilterer`, `DynamicDuplicateFilter`, `CaseSearcherFilter` |

## 8. Threading

- **EDT do Swing**: toda manipulação de componente `JFrame`/`JTable`/`JTree`/... deve estar lá. `App.createGUI()` é chamado via `SwingUtilities.invokeAndWait`.
- **`SwingWorker` (e `CancelableWorker` em `iped-viewers-api`)** para I/O e processamento pesado. Exemplo: `UICaseDataLoader` carrega o caso assíncrono.
- **`UIPropertyListenerProvider`** — event bus singleton para comunicação entre threads do engine e a UI (progresso, evidências encontradas, exceções).
- **JavaFX** em viewers (HTML, áudio, metadata): `Platform.runLater(...)`. `Platform.setImplicitExit(false)` para não fechar JVM quando última `Stage` fecha (#2874 fixou race conditions no shutdown).
- **DockingFrames + Themes**: chamadas a `dockingControl.addDockable(...)` na EDT.

## 9. Como adicionar elementos

### 9.1 Nova aba dockável
```java
// 1. Crie seu painel
public class MyPanel extends JPanel { ... }

// 2. Em App.java, declare e adicione ao dockingControl
private DefaultSingleCDockable myDock;

// em createGUI():
myDock = new DefaultSingleCDockable("my-dock-id",
        Messages.getString("App.MyTab"), new MyPanel());
dockingControl.addDockable(myDock);
```
Registre listeners se precisar reagir a seleção (`appCase.addItemSelectionListener` etc.).

### 9.2 Nova coluna na tabela de resultados
1. Defina a propriedade onde for criada (em `BasicProps`/`ExtraProperties` no `iped-api`, populada por um parser/task).
2. Em `ColumnsManager` adicione a coluna ao mapa de ordens.
3. Para renderização customizada:
   ```java
   resultsTable.getColumn("myProperty").setCellRenderer(new MyRenderer());
   ```

### 9.3 Novo filtro
Implemente `iped.engine.search.IResultSetFilter` (ou `IResultSetFilterer`) e registre via `FilterManager.addFilter(...)`. Para UI dedicada, estenda `FiltersPanel` ou crie um `*FilterPanel` separado.

### 9.4 Novo item de menu / ação
Adicione em `MenuClass` (popup contextual da tabela) ou `App` (menu principal). Implemente `ActionListener` ou `AbstractAction` com chave de localização em `iped-app.properties`.

### 9.5 Ações em bookmark
Estenda `BookmarksController` ou adicione listeners em `BookmarksTreeListener`. Para persistência, use o `IMultiBookmarks` do `appCase`.

### 9.6 Internacionalização (i18n)
1. Adicione chave em [`iped-app/resources/localization/iped-app.properties`](resources/localization).
2. Replique para `iped-app_pt_BR.properties`, `_es_AR`, `_de_DE`, `_fr_FR`, `_it_IT`.
3. Use `Messages.getString("MyKey")` (classe `iped.engine.localization.Messages` ou similar — confira a usada na classe que está editando; há mais de um bundle).

### 9.7 Nova ferramenta externa no release
1. Publique zip no repo Maven `iped-project/iped-maven` (ou jitpack).
2. Em `pom.xml`, adicione novo `<execution>` no `maven-dependency-plugin`:
   ```xml
   <execution>
       <id>unpack-mytool</id>
       <phase>package</phase>
       <goals><goal>unpack</goal></goals>
       <configuration>
           <artifactItems>
               <artifactItem>
                   <groupId>mytool</groupId>
                   <artifactId>mytool-zip</artifactId>
                   <version>1.0.0</version>
                   <type>zip</type>
                   <outputDirectory>${tools.dir}</outputDirectory>
               </artifactItem>
           </artifactItems>
       </configuration>
   </execution>
   ```
3. Use o caminho via `Bootstrap.getRootPath() + "/tools/mytool"` no código que invoca.

### 9.8 Novo profile
1. Crie `iped-app/resources/config/profiles/myprofile/`.
2. Coloque arquivos que sobrescrevem o default (apenas o que muda).
3. Use via `-profile myprofile`.

## 10. Configurações que mexem na UI

| Arquivo | Para que |
|---|---|
| `IPEDConfig.txt` | Flags de feature do processamento (mas várias afetam UI: `enableHTMLReport`, `enableLanguageDetect`, etc.). |
| `LocalConfig.txt` | Paths/threads/hashDB locais. `numberOfImageReaders`, `searchThreads`, `uiScale`. |
| `conf/AnalysisConfig.txt` | `embedLibreOffice`, `copyJREToUserHome`, `searchThreads`, cache warmup. |
| `conf/SplashScreenConfig.txt` | Splash. |
| `conf/HTMLReportConfig.txt` | Relatório final. |
| `conf/DefaultFilters.txt` | Filtros pré-instalados. |
| `conf/CategoriesConfig.json` | Árvore de categorias. |
| `conf/AIFiltersConfig.json` | Categorias para a aba "AI Filters". |
| `conf/ResultSetViewersConf.xml` | ResultSetViewers (Map, etc.) ativos. |
| `conf/ProcessingPriorityConfig.txt` | Filas. |
| `conf/UIConfig.*` (se existir) / `conf/Log4j2ConfigurationConsoleOnly.xml`, `Log4j2ConfigurationFile.xml` | Logging. |

## 11. ⚠️ Áreas sensíveis

| Local | Cuidado |
|---|---|
| `Bootstrap.run()` | Mudança quebra inicialização de toda a aplicação. Teste manualmente em Windows e Linux. |
| Splash + `StartUpControl` | Comunicação JVM pai ↔ filha via system properties; alterações podem deixar usuário sem feedback. |
| `App.createGUI()` | Construção do `dockingControl` define o layout salvo no perfil do usuário; mudar IDs invalida layouts persistidos. |
| `PanelsLayout` | Versionamento de layout; trate compatibilidade ao mudar estrutura. |
| `pom.xml` executions | A ordem (`phase=package`) e diretórios são consumidos por todo o resto. Mude com cuidado. |
| Shutdown JavaFX (`#2874`) | `Platform.exit` deve esperar JavaFX terminar antes do `System.exit` — race conditions causam exceções de `EmbeddedScene`/`GlassScene`. |
| `ThemeManager` + DockingFrames | Mexer em cores pode quebrar contraste em temas escuros. |
| Heap factor (`getRAMToHeapFactor`) | Subir demais quebra UIs em máquinas com pouca RAM; cair demais limita processamento. |

## 12. Dependências externas (runtime)

Cargo de plugins/tools que o release espera encontrar:
- **Plugins** (`plugins/`): Stanford CoreNLP 3.8.0, java-dbx 1.1-p6, telegram-decoder-impl 1.0.14, qualquer drop-in do usuário (TSK jar customizado, PhotoDNA jar restrito, etc.). Carregados pelo `PluginConfig` (engine).
- **Tools** (`tools/`): RegRipper, ImageMagick, Tesseract, MPlayer, GraphViz, libpff/libesedb/libagdb/libmsiecf/evt*export/sccainfo/rifiuti2, Sleuthkit DLLs, CaffViewer, LibreOffice zip.
- **Models** (`models/`): DIE (rfdie.dat), NSFW (Yahoo Keras h5), Vosk pt/en.
- **JRE**: `jre/` (Windows) — Liberica 11.0.13 Full FX.
- **Python**: distribuído junto (JEP + dlib) em `python/`.

Em Linux, várias dessas ferramentas vêm de pacotes `apt` (vide [`.github/workflows/maven.yml`](../.github/workflows/maven.yml)).

## 13. Checklist de PR

- [ ] Mudanças na UI testadas com `App` rodando contra caso real, em pelo menos um locale (PT-BR e EN).
- [ ] Layout dock IDs estáveis (não renomeei sem migração).
- [ ] Strings novas adicionadas em **todos** os bundles (`localization/*.properties`).
- [ ] Não bloqueei a EDT — operações longas em `SwingWorker`/`Platform.runLater`.
- [ ] Não modifiquei `Bootstrap`/`BootstrapUI`/`AppMain` sem testar shutdown limpo (JavaFX + Manager).
- [ ] Se adicionei ferramenta externa, criei `<execution>` no `pom.xml` e documentei em `ThirdParty.txt`.
- [ ] Se mexi em `pom.xml`, fiz `mvn clean package` e validei o conteúdo de `target/release/iped-${version}/`.
- [ ] Atualizei [`CLAUDE.md`](../CLAUDE.md) raiz se a mudança afeta build/empacotamento/UI principal.
