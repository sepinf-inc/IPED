# Módulo `iped-engine`

> **Coração do IPED.** Orquestra todo o ciclo de processamento: leitura de evidências (imagens forenses, pastas, casos UFED/IPED), pipeline paralelo de tarefas, indexação Lucene, busca, geração de relatórios, API Web e graph analytics.

> Mudanças em `Manager`, `Worker`, `ProcessingQueues`, `IndexWriter` ou nas configs base têm impacto em **tudo**. Trate este módulo com cuidado redobrado.

## 1. Propósito

- **Ingestão**: ler imagens forenses (Sleuthkit), casos IPED, relatórios UFED, AD1 e pastas comuns.
- **Pipeline paralelo**: workers consumindo filas prioritárias com uma cadeia configurável de `AbstractTask`s.
- **Análises**: hashing, signature/MIME, parsing (Tika), carving, OCR, regex, NER, detecção de idioma, similaridade de imagens, reconhecimento facial, DIE (CSAM), PhotoDNA, transcrição de áudio.
- **Indexação Lucene 9.2.0** com `AppAnalyzer` customizado e suporte multi-source.
- **Busca**: `IPEDSearcher`, `IItemSearcher`, `LuceneSearchResult`.
- **Sleuthkit out-of-process**: cliente/servidor C++ isola crashes nativos.
- **Relatórios**: HTML, CSV, casos portáteis, bookmarks.
- **Web API**: REST/Swagger (Jersey + Grizzly) para busca remota de casos.
- **Graph**: análise de relacionamentos em Neo4j (chamadas, e-mails, mensagens, redes wireless).
- **Extensibilidade**: scripts JavaScript (Nashorn) e Python (Jep) para tarefas customizadas.

Versão: `4.4.0-SNAPSHOT`. Java 11+. Lucene 9.2.0, Tika 2.4.0, Sleuthkit 4.12.0.p1.

## 2. Estrutura de pacotes

```
iped/engine/
├── core/         # Manager, Worker, ProcessingQueues, Statistics, EvidenceStatus, QueuesProcessingOrder
├── task/         # AbstractTask + ~100 tarefas concretas (HashTask, ParsingTask, IndexTask, CarverTask, ...)
│   ├── carver/   # BaseCarveTask, CarverTask, KnownMetCarveTask, LedCarveTask, XMLCarverConfiguration
│   ├── die/      # DIETask + RandomForestPredictor (nudity detection)
│   ├── index/    # IndexTask, IndexItem, ElasticSearchIndexTask
│   ├── jumplist/ # JumpListTask + AppIDCalculator
│   ├── regex/    # RegexTask + ~30 validadores (CPF, CNPJ, cartões, crypto, ...)
│   ├── similarity/  # ImageSimilarityTask, SimilarFaces, ...
│   └── transcript/  # AudioTranscriptTask + implementações Vosk/Microsoft/Google/Whisper
├── config/       # ConfigurationManager + ~55 Configurables (AbstractTaskConfig, IndexTaskConfig, ...)
├── data/         # Item, IPEDSource, CaseData, Bookmarks, Category, DataSource
├── datasource/   # SleuthkitReader, IPEDReader, FolderTreeReader, UfedXmlReader, AD1DataSourceReader
├── sleuthkit/    # SleuthkitClient (pool de processos), SleuthkitInputStreamFactory, SleuthkitJNI binding
├── lucene/       # AppAnalyzer, StandardASCIIAnalyzer, ConfiguredFSDirectory, CustomIndexDeletionPolicy
├── search/       # IPEDSearcher, ItemSearcher, LuceneSearchResult, QueryBuilder, SimilarImagesSearch
├── io/           # ParsingReader, ParsingProcess (out-of-process), MetadataInputStreamFactory, FragmentingReader
├── graph/        # GraphTask, GraphService (Neo4j), Cypher templates
├── webapi/       # Jersey REST endpoints: Search, Sources, Content, Text, Thumbnail, Bookmarks
├── preview/      # PreviewRepositoryManager, MinIO, filesystem
├── localization/ # Messages bundle
├── log/          # Setup de log4j2
├── hashdb/       # HashDBTool (CLI), HashDBLookup, NSRL/ProjectVic import
├── tika/         # Tika config customizado
└── util/         # Util, UIPropertyListenerProvider, ...
```

Resources em `iped-engine/src/main/resources/`:
- `iped/engine/graph/links/*.cypher` — templates Cypher para análise de comunicação.
- `swift/*.csv` — códigos SWIFT.
- `META-INF/services/`:
  - `iped.engine.task.regex.RegexValidatorService` — plugins de validadores.
  - `iped.engine.graph.links.SearchLinksQuery` — plugins de queries de graph.

## 3. Conceitos centrais

### Manager
Orquestrador único do caso. [`iped/engine/core/Manager.java`](src/main/java/iped/engine/core/Manager.java).
- Cria `IndexWriter` (Lucene) compartilhado entre workers.
- Inicializa o pipeline (via `TaskInstaller`).
- Lança o `counter` (conta itens sem enfileirar) e o `producer` (efetivamente enfileira).
- Lança N `Worker`s (tipicamente `min(processors, cores configurados)`).
- Loop `monitorProcessing()`: reporta progresso, checa exceções, faz commits periódicos.
- Pós-processamento: mapeamentos reversos (lucene-id ↔ item-id), filtros, logs.

### Worker
Thread. [`iped/engine/core/Worker.java`](src/main/java/iped/engine/core/Worker.java). Estados `RUNNING`, `PAUSING`, `PAUSED`.
- Pop de item em `ProcessingQueues.nextItem()`.
- Executa cadeia de tasks: `firstTask.process(item)` → `task.nextTask.process(item)` → ...
- Subitens (zip extraído, arquivo recuperado por carving) chamam `worker.processNewItem(item)` → reenfileirados.
- Cada worker tem **instâncias próprias** de cada `AbstractTask` (evita sincronização).
- Excepção → propagada ao Manager, que aborta processamento.

### ProcessingQueues
[`iped/engine/core/ProcessingQueues.java`](src/main/java/iped/engine/core/ProcessingQueues.java). `TreeMap<Integer prioridade, FilaEnumPrioridades>` — filas indexadas por prioridade (subitens normalmente > itens de disco). Cada fila combina `LinkedList` (prioridade interna) com `ArrayList` (seleção aleatória para evitar contenção de recurso). Tamanho máximo configurável (auto-escalado pelo heap). Bloqueante com timeout.

### AbstractTask
[`iped/engine/task/AbstractTask.java`](src/main/java/iped/engine/task/AbstractTask.java). Base de toda tarefa do pipeline:
```java
public abstract class AbstractTask {
    protected Worker worker;
    protected Statistics stats;
    protected File output;
    protected ICaseData caseData;
    protected AbstractTask nextTask;

    public void init(ConfigurationManager cm) throws Exception { ... }
    public abstract void process(IItem evidence) throws Exception;
    public void finish() throws Exception { ... }
    public abstract List<Configurable<?>> getConfigurables();
    public boolean isEnabled() { ... }
}
```

### TaskInstaller
[`iped/engine/task/TaskInstaller.java`](src/main/java/iped/engine/task/TaskInstaller.java). Lê `conf/TaskInstaller.xml`, instancia tarefas via reflexão e encadeia (`task.nextTask = ...`). Suporta:
```xml
<tasks>
  <task class="iped.engine.task.SignatureTask"/>
  <task script="tasks/myTask.js"/>
  <task script="tasks/myTask.py"/>
</tasks>
```

## 4. Pipeline padrão (ordem importa)

| # | Task | O que faz |
|---|---|---|
| 1 | `SignatureTask` | Detecta MIME via Tika `Detector`, define `MediaType`. |
| 2 | `SetTypeTask` | Normaliza extensão a partir do MIME. |
| 3 | `IgnoreHardLinkTask` | Filtra hardlinks duplicados. |
| 4 | `TempFileTask` | Filtra arquivos temporários/system. |
| 5 | `SkipCommitedTask` | Pula itens já processados (modo `--continue`). |
| 6 | `HashTask` | MD5, SHA-1/256/512, Edonkey (paralelo). |
| 7 | `HashDBLookupTask` | Consulta NSRL/ProjectVic (marca "known"). |
| 8 | `PhotoDNATask` / `PhotoDNALookup` | PhotoDNA + consulta NCMEC. |
| 9 | `ParsingTask` | Extração de texto/metadata via Tika; expande containers. |
| 10 | `CarverTask` / `KnownMetCarveTask` / `LedCarveTask` | Recupera arquivos por assinatura. |
| 11 | `SetCategoryTask` | Atribui categorias. |
| 12 | `LanguageDetectTask` | Detecção de idioma (>70). |
| 13 | `NamedEntityTask` | NER via Tika + Stanford NLP. |
| 14 | `RegexTask` | CPF/CNPJ/cartões/bitcoin/... (com validators plugáveis). |
| 15 | `ImageThumbTask` / `VideoThumbTask` / `DocThumbTask` | Geração de thumbnails. |
| 16 | `ImageSimilarityTask`, `DIETask`, `RemoteImageClassifierTask` | IA visual. |
| 17 | `MakePreviewTask` | Previews para repositório (MinIO/FS). |
| 18 | `AudioTranscriptTask` (Vosk/Whisper/Google/Microsoft) | Transcrição. |
| 19 | `JumpListTask`, `EmbeddedDiskProcessTask`, `DuplicateTask`, `EntropyTask`, `QRCodeTask` | Análises auxiliares. |
| 20 | **`IndexTask`** | Cria `Document` Lucene e adiciona ao `IndexWriter`. |
| 21 | `ElasticSearchIndexTask` | (opcional) indexa em paralelo no OpenSearch/ES. |
| 22 | `GraphTask` | Constrói grafo Neo4j. |
| 23 | `HTMLReportTask`, `ExportFileTask`, `ExportCSVTask` | Relatórios finais. |
| 24 | `ScriptTask`, `PythonTask` | Tarefas customizadas. |
| 25 | `MinIOTask` | Upload para storage. |
| 26 | `P2PBookmarker` | Bookmarks automáticos de P2P. |

A ordem real vive em `iped-app/resources/config/conf/TaskInstaller.xml` e nos profiles (`profiles/{forensic,pedo,triage,fastmode,blind}/`).

## 5. Sistema de Configuração

### `ConfigurationManager`
Singleton (`ConfigurationManager.get()`). Mapeia `Configurable<T>` → caminhos de arquivo, carregando lazy via `IConfigurationDirectory.lookUpResource(configurable)`.

### Hierarquia base
```
Configurable<T> (iped-api)
└─ AbstractPropertiesConfigurable        (parsing UTF-8 via UTF8Properties)
   └─ AbstractTaskPropertiesConfig
      └─ AbstractTaskConfig<T>           (base para configs de Tasks)
```

### Padrão por Task
```java
public class MyTaskConfig extends AbstractTaskConfig<String> {
    @Override public String getTaskEnableProperty() { return "MyTask.enabled"; }
    @Override public String getTaskConfigFileName() { return "MyTaskConfig.properties"; }
    @Override public void processTaskConfig(Path resource) { /* parse */ }
    @Override public boolean isEnabled() { return enabledProp.isEnabled(); }
}
```

### Configurações relevantes (~80 classes em `iped/engine/config/`)
- **Pipeline/Engine**: `Configuration`, `LocalConfig`, `AnalysisConfig`, `ProcessingPriorityConfig`, `TaskInstallerConfig`, `PluginConfig`.
- **I/O & Sleuthkit**: `FileSystemConfig` (`numberOfImageReaders`).
- **Tasks**: `HashTaskConfig`, `IndexTaskConfig`, `ParsingTaskConfig`, `SignatureConfig`, `CategoryConfig`, `CarverTaskConfig`, `OCRConfig`, `RegexConfigurable`, `AudioTranscriptConfig`, `ImageThumbTaskConfig`, `VideoThumbsConfig`, `DocThumbTaskConfig`, `MakePreviewConfig`, `HTMLReportTaskConfig`, `PhotoDNAConfig`, `FaceRecognitionConfig`, `AgeEstimationConfig`, `DIEConfig`, `CSAMDetectorConfig`, `MinIOConfig`, `ElasticSearchTaskConfig`, `HashDBLookupConfig`.
- **UI**: `LocaleConfig`, `SplashScreenConfig`.
- **AI/Graph**: `AIFiltersConfig`, `RemoteImageClassifierConfig`, `NamedEntityRecognitionConfig`.

Arquivos físicos vivem em `iped-app/resources/config/conf/` e `profiles/{forensic,pedo,triage,fastmode,blind}/conf/`.

## 6. Modelo de Caso

| Classe | Responsabilidade |
|---|---|
| [`Item`](src/main/java/iped/engine/data/Item.java) | Implementa `IItem`. Metadados, hashes, categorias, bookmarks, atributos extras. Lazy I/O via `ISeekableInputStreamFactory`. |
| [`IPEDSource`](src/main/java/iped/engine/data/IPEDSource.java) | Implementa `IIPEDSource`. `IndexReader`, `IndexSearcher`, mapeia id ↔ lucene-doc, bookmarks, categoria. Diretório: `{caseDir}/iped/{index,data,lib}`. |
| `IPEDMultiSource` | Agregação multi-caso. |
| `CaseData` | Estado global mutável compartilhado entre tasks (`objectMap`), flags (`containsReport`, `ipedReport`), contadores (`discoveredVolume`, `discoveredEvidences`). Serializa para GZIP. |
| `Bookmarks` | `TreeMap<Integer, byte[]>` (bitset de itens), nomes, comentários, cores. |
| `MultiBookmarks` | Multi-caso. |
| `Category` | Árvore (parent/children/cor) com filtragem hierárquica. |
| `DataSource` | Caminho/nome/timezone da fonte. |

## 7. Data Sources

`iped/engine/datasource/`:
- `SleuthkitReader` — E01, DD/raw, VHD, VMDK, ISO 9660, Logical Evidence Container. Cria `SleuthkitCase` (SQLite), itera o filesystem (NTFS, FAT, ext, HFS+, UFS). Configura `SleuthkitInputStreamFactory` para lazy stream.
- `IPEDReader` — carrega outro caso IPED (index + thumbs + bookmarks).
- `FolderTreeReader` — pastas via `Files.walkFileTree` (NIO), respeita patterns de exclusão.
- `UfedXmlReader` — relatórios Cellebrite (XML).
- `AD1DataSourceReader` — AccessData AD1 (em `ad1/AD1Extractor.java`).
- `ItemProducer` — interface usada por counter/producer.

Imagens embutidas dentro de imagens (recursão) são abertas por `EmbeddedDiskProcessTask` + `SleuthkitReader`.

## 8. Sleuthkit out-of-process

**Por quê**: libtsk em JNI tem risco de leak/crash/freeze. Solução: `SleuthkitClient` mantém pool de subprocessos C++ (`SleuthkitServer`), comunicando via memory-mapped files/sockets. Se um server cair, é respawnado e o worker faz retry.

Componentes em `iped/engine/sleuthkit/`:
- `SleuthkitClient` — pool, timeout (3600s default), reconnect.
- `SleuthkitInputStreamFactory` — entrega `SeekableInputStream` sob demanda.
- `SleuthkitJNI` — binding nativo.
- `SleuthkitServer` é binário externo distribuído em `tools/`.

Config: `FileSystemConfig.getNumImageReaders()`.

## 9. Indexação Lucene

`iped/engine/task/index/IndexTask.java` e `IndexItem.java`:
- Converte `IItem` em `org.apache.lucene.document.Document`.
- Campos: `BasicProps` + metadados + extras. Conteúdo (full-text) opcional.
- Fragmenta documentos grandes (`FragmentingReader`) para evitar OOM.
- `IndexWriter` único, compartilhado entre workers.
- Commits a cada ~30 min (`commitIntervalMillis = 1800000`) + commit final.

`iped/engine/lucene/`:
- `AppAnalyzer` — analyzer por campo: `StandardASCIIAnalyzer` (texto), `KeywordAnalyzer` (hashes/IDs/datas).
- Configurável: lowercase, ASCII fold, filtro de caracteres não-latinos, tamanho máximo de token, caracteres extras para tokenização (importante para PT-BR).
- `CustomIndexDeletionPolicy` — preserva múltiplos commits para rollback em crash.

Busca: `iped/engine/search/`:
- `IPEDSearcher` — implementa `IIPEDSearcher`. Executa `Query` Lucene.
- `ItemSearcher` — implementa `IItemSearcher`. Retorna `List<IItemReader>`.
- `QueryBuilder` — string → AST Lucene.
- `LuceneSearchResult` — iterador com scores.

## 10. Web API (`iped/engine/webapi/`)

Servidor HTTP **Grizzly + Jersey/JAX-RS** + Swagger. Entry point: `Main.java`.

Endpoints típicos:
- `GET /` — root + Swagger UI.
- `GET /sources` — lista de casos.
- `GET /search?q=<query>&sourceID=<id>` — busca full-text.
- `GET /content/{sourceID}/{itemID}` — download de bytes.
- `GET /text/{sourceID}/{itemID}` — texto extraído.
- `GET /thumbnail/{sourceID}/{itemID}` — thumbnail.
- `POST /bookmarks/...` — gerenciar bookmarks/tags.

Config: `WebApiConfig` (ou similar) define porta e auth.

## 11. Hashes e bases

- Algoritmos suportados: **MD5, SHA-1, SHA-256, SHA-512, Edonkey** (definidos em `HashTask.HASH`).
- Computação multi-thread via `ExecutorService`.
- **HashDBLookupTask** consulta SQLite local com bases importadas (NSRL, ProjectVic, custom CSV). Marca itens como "known" para exclusão de análise.
- **PhotoDNATask** + **PhotoDNALookup** — APIs externas (NCMEC) para CSAM (requer credencial; jar adicional `br.dpf:photodna-api`).
- `HashDBTool` (CLI, em `iped/engine/hashdb/`) constrói/manipula a base.

## 12. Profiles e configs

Em `iped-app/resources/config/profiles/`:
- `forensic` — pipeline completo.
- `pedo` — foco em CSAM (PhotoDNA, DIE, faces).
- `triage` — mais rápido, foco em triagem inicial.
- `fastmode` — preview, sem indexação/parsing pesados.
- `blind` — extração automática de dados sem UI.

Cada profile sobrescreve seletivamente arquivos em `conf/`.

## 13. Scripting

**JavaScript (Nashorn)** — `iped-app/resources/scripts/tasks/*.js`. Funções esperadas: `getName()`, `getConfigurables()`, `init(cm)`, `process(item)`, `finish()`.

**Python (Jep)** — `iped-app/resources/scripts/tasks/*.py`. Classe com mesmos métodos; instância global no final. Requer libpython + libjep + `python-jep-dlib` (distribuído junto).

Bindings globais expostos: `caseData`, `worker`, `stats`, `output`, `moduleDir`.

Veja exemplos: `ExampleScriptTask.js`, `PythonScriptTask.py`, `AgeEstimationTask.py`, `FaceRecognitionTask.py`, `CSAMDetectorTask.py`, `Wav2Vec2Process.py`, `WhisperProcess.py`.

## 14. Dependências principais

| Lib | Versão | Para que |
|---|---|---|
| `org.apache.lucene:lucene-*` (core, analysis-common, backward-codecs, highlighter, queryparser, misc, join) | 9.2.0 | Indexação + busca |
| `org.apache.tika:tika-*` (core, parser-nlp-module, langdetect-optimaize) | 2.4.0(-p1) | Detecção + parsing |
| `org.sleuthkit:sleuthkit` | 4.12.0.p1 | Imagens forenses |
| `org.apache.pdfbox:pdfbox(+tools+xmpbox)`, `jbig2-imageio` | 2.0.27 / 3.0.4 | PDF |
| `org.xerial:sqlite-jdbc` | 3.41.2.2 | SQLite |
| `org.neo4j:neo4j` | 4.4.4 | Graph |
| `org.glassfish.jersey.containers:jersey-container-grizzly2-servlet` + jersey-hk2 | 2.30.1 | Web API |
| `io.swagger:swagger-jersey2-jaxrs` | 1.6.10 | Swagger |
| `org.glassfish.jersey.media:jersey-media-json-jackson` | 2.28 | JSON |
| `io.minio:minio` | 8.3.8 | Object storage |
| `org.opensearch.client:opensearch-rest-high-level-client` | 2.1.0 | ES/OpenSearch |
| `com.alphacephei:vosk` | 0.3.32 | Speech offline |
| `com.microsoft.cognitiveservices.speech:client-sdk` | 1.19.0 (provided) | Azure speech |
| `com.google.cloud:google-cloud-speech` | 1.22.5 (provided) | Google speech |
| `org.openjdk.nashorn:nashorn-core` | 15.4 | JS scripting |
| `dk.brics.automaton:automaton` | 1.11-8 | Regex automata |
| `org.bouncycastle:bcpkix-jdk15on` | 1.70 | Crypto |
| `de.ruedigermoeller:fst` | 2.57 | Serialização rápida |
| `com.zaxxer:HikariCP` | 7.0.2 | Pool de conexões |
| `com.h2database:h2` | 2.3.232 | DB embarcado (cache) |
| `org.apache.commons:commons-compress` | 1.27.1 | ZIP, RAR, 7z, tar (#1068) |
| `com.googlecode.libphonenumber:libphonenumber` | 8.9.14 | Validação de telefone |
| `com.github.luben:zstd-jni` | 1.3.3-3 | Compressão Zstd |
| `com.mchange:c3p0`, `mchange-commons-java` | 0.9.5.5 / 0.2.20 | JDBC pool legado |
| `com.zaxxer:SparseBitSet` | 1.1 | Bitsets esparsos |
| `org.apache.httpcomponents:httpmime` | 4.5.13 | HTTP MIME |
| `com.github.oshi:oshi-core-java11` | 6.2.2 | Info de sistema |
| `com.google.zxing:core+javase` | 3.5.1 / 3.5.0 | QR code |
| `br.dpf:photodna-api` | 1.0 | PhotoDNA (restrito) |
| `iped:iped-ahocorasick` | 1.1 | Aho-Corasick |
| `iped:iped-parsers-impl`, `iped-carvers-{api,impl}`, `iped-viewers-{api,impl}`, `iped-utils`, `iped-api` | `${project.version}` | Internas |

## 15. Padrões de design

- **Producer/Consumer** — `ProcessingQueues` entre `ItemProducer` e `Worker`s.
- **Pipeline / Chain of Responsibility** — `AbstractTask` encadeado por `nextTask`.
- **Strategy** — Configurables determinam comportamento por arquivo.
- **Out-of-process** — `SleuthkitClient`/`SleuthkitServer`, `ParsingProcess` para isolamento de crash.
- **Lazy initialization** — `ConfigurationManager.loadConfig(configurable)` só lê arquivo quando necessário.
- **Per-worker state** — Tasks instanciadas N vezes (uma por worker) evitam sincronização.
- **Reflection + XML wiring** — `TaskInstaller.xml` define a cadeia; Spring-style sem Spring.

## 16. Como adicionar uma nova tarefa

```java
package com.example;

import iped.engine.config.AbstractTaskConfig;
import iped.engine.task.AbstractTask;
import iped.configuration.Configurable;
import iped.data.IItem;
import java.util.List;

public class MyTask extends AbstractTask {

    private MyTaskConfig config;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return List.of(ConfigurationManager.get().findObject(MyTaskConfig.class));
    }

    @Override
    public void init(ConfigurationManager cm) throws Exception {
        config = cm.findObject(MyTaskConfig.class);
    }

    @Override
    public boolean isEnabled() { return config != null && config.isEnabled(); }

    @Override
    public void process(IItem evidence) throws Exception {
        if (evidence.isDir() || !isEnabled()) return;
        // ... seu processamento
        evidence.setExtraAttribute("my:result", value);
    }

    @Override
    public void finish() throws Exception { /* cleanup */ }
}
```

Config (opcional):
```java
public class MyTaskConfig extends AbstractTaskConfig<String> {
    @Override public String getTaskEnableProperty()  { return "MyTask.enabled"; }
    @Override public String getTaskConfigFileName() { return "MyTaskConfig.txt"; }
    @Override public void processTaskConfig(Path p) throws IOException { /* parse */ }
    @Override public boolean isEnabled() { return enabledProp.isEnabled(); }
}
```

Registro:
1. Edite `iped-app/resources/config/conf/TaskInstaller.xml` adicionando `<task class="com.example.MyTask"/>` na posição correta.
2. Edite `IPEDConfig.txt` para `MyTask.enabled = true`.
3. Coloque `MyTaskConfig.txt` em `conf/`.

## 17. Como adicionar um novo `DataSourceReader`

1. Estenda `DataSourceReader` em `iped/engine/datasource/`.
2. Implemente `read(File)` chamando `caseData.incDiscoveredEvidences(...)`, `produceItem(...)` etc.
3. Registre em `iped-engine` (busca por instâncias é feita por `Configuration.loadDataSourceReaders` — confira o código atual).

## 18. ⚠️ Áreas críticas

| Área | Cuidado |
|---|---|
| `Manager.startProcessing()` | Sequência de init/monitor/close — alterar pode quebrar workers. |
| `Worker.run()` | Item fetching + pipeline — race conditions podem comer/duplicar itens. |
| `ProcessingQueues` | Synchronization complexo. |
| `IndexWriter` | **Apenas Manager comanda commits**; workers só fazem `addDocument`. Não chame `commit()` manualmente. |
| `SleuthkitClient` pool | Mudanças aqui afetam estabilidade global. |
| `TaskInstaller` order | A ordem das tarefas implica dependências; mover algo arbitrariamente quebra cadeia. |
| Strings literais em `BasicProps`/`IndexItem` | São nomes de campo Lucene; renomear invalida casos existentes. |
| `AppAnalyzer` configs | Mudar fold/ASCII/lowercase invalida queries antigas. |
| `commitIntervalMillis` | Muito baixo derruba performance; muito alto aumenta perda em crash. |

## 19. Debugging

- **Logs** Log4j2: `iped.engine.core.Manager`, `iped.engine.core.Worker`, `iped.engine.task.*`, `iped.engine.sleuthkit.SleuthkitClient`, `iped.engine.search.IPEDSearcher`.
- **Estatísticas** em `Manager.stats` e via `caseData.getObjectMap()`.
- Sintomas comuns:
  - Task não executa → confira `isEnabled()` + `IPEDConfig.txt`.
  - Index não busca → verifique `IndexTask` ativo e commit final.
  - OOM → reduza fragmento de documento, `maxQueueSize`, threads.
  - Sleuthkit crash → veja `SleuthkitClient` log + número de servers + formato da imagem.
  - Script error → bindings em `init()` + sintaxe.

## 20. Convenções de nomenclatura

- Task: `*Task` (ex.: `HashTask`, `SignatureTask`).
- Config: `*Config` (ex.: `HashTaskConfig`).
- Propriedade enable: `&lt;TaskNameSemTask&gt;.enabled` (ex.: `HashTask.enabled`).
- Config file: `&lt;TaskNameSemTask&gt;Config.txt` ou `&lt;TaskNameSemTask&gt;Config.properties`.
- Campos UI: `ExtraProperties.*`.
- Campos Lucene: constantes em `IndexItem` (alguns mapeiam direto de `BasicProps`).

## 21. Bons hábitos

✅ Use `ConfigurationManager.get().findObject(MyConfig.class)` para acessar configs.  
✅ Adicione itens via `worker.processNewItem()` (não diretamente em filas).  
✅ Compartilhe estado entre tasks via `caseData.getObjectMap()` com cleanup.  
✅ Implemente `isEnabled()` corretamente — respeita config.  
✅ Tarefas que abrem stream devem fechar (try-with-resources).  
✅ Itens diretórios (`item.isDir()`) geralmente não devem ter conteúdo aberto.  

❌ Não instancie `IndexWriter` manualmente.  
❌ Não faça commit manual em Lucene.  
❌ Não chame `Sleuthkit*` direto — use `SleuthkitClient`.  
❌ Não armazene listas grandes em `caseData.objectMap` sem `finish()` que limpe.  
❌ Não assuma ordem de tasks — sempre marque dependências em `TaskInstaller.xml`.  
❌ Não bloqueie threads de worker em I/O lento sem timeout (regressão de throughput).

## 22. Checklist de PR

- [ ] Nova task tem `Config` correspondente (mesmo que minimal).
- [ ] `IPEDConfig.txt` documenta a nova flag.
- [ ] `TaskInstaller.xml` foi atualizado na posição correta.
- [ ] `isEnabled()` é respeitado em `process`.
- [ ] Logging via SLF4J.
- [ ] Não modificou `Manager`, `Worker`, `ProcessingQueues` sem teste de regressão.
- [ ] Não renomeou strings que viram chave de campo Lucene.
- [ ] Não introduziu dependência nativa sem documentação em `tools/`.
- [ ] Adicionou teste JUnit em `iped-engine/src/test/java/...` se for caminho importante.
