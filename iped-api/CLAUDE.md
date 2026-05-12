# Módulo `iped-api`

> Documentação destinada a agentes de IA e desenvolvedores. Mantenha esse documento atualizado ao alterar a API.

## 1. Propósito

O `iped-api` é o **módulo de contratos** do IPED (Indexador e Processador de Evidências Digitais). Não contém lógica — apenas interfaces, exceções e constantes que **todos** os outros módulos consomem ou implementam.

- Define o contrato central da plataforma (item de evidência, fonte de dados, busca, configuração, I/O).
- É a base para plugins, extensões e isolamento de camadas (engine ↔ viewers ↔ parsers).
- Empacotamento: `jar`. Compilado para **Java 11**.
- Versão: `4.4.0-SNAPSHOT` (parent `iped:iped-parent`).

Qualquer mudança aqui propaga para todo o ecossistema. Trate como API pública: **prefira aditivo, evite breaking changes.**

## 2. Layout

```
iped-api/src/main/java/iped/
├── configuration/  # Configurable, ObjectManager, EnabledInterface, IConfigurationDirectory
├── data/           # IItem, IItemReader, IItemId, IIPEDSource, ICaseData, IBookmarks, IMultiBookmarks, IHashValue, SelectionListener
├── datasource/     # IDataSource
├── exception/      # IPEDException, ParseException, QueryNodeException, ZipBombException
├── io/             # SeekableInputStream, ISeekableInputStreamFactory, IStreamSource, URLUtil
├── localization/   # LocaleResolver, LocalizedProperties, Messages
├── properties/     # BasicProps, ExtraProperties, MediaTypes
└── search/         # IIPEDSearcher, IItemSearcher, IMultiSearchResult, SearchResult
```

## 3. Interfaces e classes principais

### 3.1 [`iped.data.IItem`](src/main/java/iped/data/IItem.java)
Interface principal — **arquivo de evidência completo** com propriedades, metadados e acesso ao conteúdo.

- Estende `IItemReader`.
- ~70 métodos. Categorias: categoria/labels, hierarquia (parent/child), metadados de arquivo, datas (`creation`, `modification`, `access`, `change`), flags (`carved`, `deleted`, `subItem`, `root`, `dir`, `timedOut`), atributos extras (`getExtraAttribute`/`setExtraAttribute`), I/O (`getBufferedInputStream`, `getSeekableInputStream`, `getTikaStream`, `getTempFile`).
- `createChildItem()` é o factory method usado pelos parsers/carvers para gerar subitens.
- `dispose()` libera temporários e handles — chame-o no fim do ciclo de vida.

**Implementação concreta:** `iped.engine.data.Item` (em `iped-engine`).

### 3.2 [`iped.data.IItemReader`](src/main/java/iped/data/IItemReader.java)
Vista somente-leitura sobre um `IItem`. Estende `IStreamSource`. Use-a em métodos que só precisam ler — não acoplar UI/Engine ao `IItem` mutável.

### 3.3 [`iped.data.IIPEDSource`](src/main/java/iped/data/IIPEDSource.java)
Acesso centralizado a uma fonte (caso) com índice Lucene, leitura de itens, bookmarks. Constantes: `INDEX_DIR = "index"`, `MODULE_DIR = "iped"`, `SLEUTH_DB = "sleuth.db"`.

### 3.4 [`iped.configuration.Configurable<T>`](src/main/java/iped/configuration/Configurable.java)
Padrão usado para todo componente que carrega configuração de arquivo:
```java
DirectoryStream.Filter<Path> getResourceLookupFilter();
void processConfig(Path resource);
T getConfiguration();
void setConfiguration(T config);
```
Implementações vivem em `iped-engine/config/` (ver `AbstractPropertiesConfigurable`, `AbstractTaskPropertiesConfig`, etc.).

### 3.5 [`iped.properties.BasicProps`](src/main/java/iped/properties/BasicProps.java)
~45 constantes de propriedades básicas indexadas no Lucene: `ID`, `PARENTID`, `NAME`, `EXT`, `TYPE`, `LENGTH`, `PATH`, `CATEGORY`, `CREATED`, `MODIFIED`, `ACCESSED`, `CHANGED`, `DELETED`, `HASH`, `ISDIR`, `ISROOT`, `HASCHILD`, `CARVED`, `SUBITEM`, `OFFSET`, `TIMEOUT`, `CONTENTTYPE`, `META_ADDRESS`, `MFT_SEQUENCE`, `FILESYSTEM_ID`, `TRACK_ID`, etc. As strings literais **são chaves de documentos Lucene** — renomear quebra índices persistidos.

### 3.6 [`iped.properties.ExtraProperties`](src/main/java/iped/properties/ExtraProperties.java)
Propriedades extras agrupadas por domínio: comunicação (`CONVERSATION_*`, `COMMUNICATION_*`, `MESSAGE_*`), email, chat, UFED, hashes/segurança, datas especiais. Use estas chaves para campos definidos por parsers.

### 3.7 [`iped.properties.MediaTypes`](src/main/java/iped/properties/MediaTypes.java)
Tipos MIME customizados do IPED + helpers para o registro Tika:
- `DISK_IMAGE`, `RAW_IMAGE`, `EWF_IMAGE`, `ISO_IMAGE`, `VMDK`, `VHD`, `VHDX`
- `METADATA_ENTRY`, `UFED_EMAIL_MIME`, `UFED_MESSAGE_MIME`, `UFED_CALL_MIME`, etc.
- `OUTLOOK_MSG`, `MS_PUBLISHER`, `CHAT_MESSAGE_MIME`
- Métodos: `getMediaTypeRegistry()`, `normalize()`, `getParentType()`, `isInstanceOf()`, `isMetadataEntryType()`.

### 3.8 Outras interfaces críticas
- `IItemId`, `IIPEDSearcher`, `IItemSearcher`, `IMultiSearchResult`, `SearchResult` — API de busca Lucene.
- `IBookmarks`/`IMultiBookmarks` — marcadores em caso único / multi-caso (com `SelectionListener`).
- `SeekableInputStream` / `ISeekableInputStreamFactory` / `IStreamSource` — I/O com `seek`.
- `IConfigurationDirectory`, `ObjectManager<T>`, `EnabledInterface` — sistema de plugins/configs.
- `IPEDException`, `ParseException`, `QueryNodeException`, `ZipBombException` (com `MAX_COMPRESSION = 100` e `ZIPBOMB_MIN_SIZE = 1GB`).
- `LocaleResolver` (lê system property `iped-locale`), `LocalizedProperties`, `Messages`.

## 4. Dependências

```xml
<dependency> <groupId>org.apache.lucene</groupId>    <artifactId>lucene-core</artifactId>    <version>9.2.0</version>       </dependency>
<dependency> <groupId>org.apache.tika</groupId>      <artifactId>tika-core</artifactId>      <version>2.4.0-p1</version>    </dependency>
```
Logging (`log4j`, `slf4j`), JUnit, Hamcrest vêm do parent `iped:iped-parent`.

- **Lucene** é usado em `IIPEDSearcher`, `IMultiSearchResult`, `IIPEDSource.getSearcher()/getAnalyzer()/getReader()`.
- **Tika** é usado em `IItem.getTikaStream()` e `MediaTypes` (registro de tipos MIME).

## 5. Convenções

- Interfaces públicas têm prefixo `I` (`IItem`, `IIPEDSource`, `IBookmarks`).
- Implementações concretas correspondem sem o prefixo: `Item`, `IPEDSource`, `Bookmarks` (em `iped-engine`).
- Javadocs estão majoritariamente em **português brasileiro**. Mantenha o padrão ao adicionar javadocs.
- Constantes em `UPPERCASE_WITH_UNDERSCORE`, com prefixos por domínio (`CONVERSATION_*`, `UFED_*`).
- Métodos: `get/set`, `is/has` para booleanos, `add/remove` para coleções.

## 6. Padrões de design observados

- **Factory** — `ISeekableInputStreamFactory`, `IItem.createChildItem()`.
- **Listener** — `SelectionListener`, `IMultiBookmarks.addSelectionListener`.
- **Manager/Registry** — `ObjectManager<T>`, `IConfigurationDirectory`.
- **Configurable mixin** — `Configurable<T>` com `getResourceLookupFilter()` (Strategy + Template Method).
- **Adapter** — `SeekableInputStream` adapta `InputStream` com `seek`.
- **Composite** — `IBookmarks` (single source) e `IMultiBookmarks` (multi source).

## 7. Como adicionar elementos com segurança

### 7.1 Nova propriedade num `IItem`
1. Adicione a constante em [`BasicProps`](src/main/java/iped/properties/BasicProps.java) **ou** em [`ExtraProperties`](src/main/java/iped/properties/ExtraProperties.java) (preferível para metadados de domínio).
2. Se entrar em `BasicProps`, lembre-se de incluir no `BasicProps.SET` (HashSet de todas as constantes).
3. Para dados de runtime (não persistentes), use `IItem.setExtraAttribute(key, value)` — não precisa alterar a API.
4. Para persistência no índice Lucene, propague em `iped-engine/task/index/IndexItem.java`.

### 7.2 Novo `Configurable<T>`
```java
public class MyConfig implements Serializable { /* getters/setters */ }

public class MyConfigurable implements Configurable<MyConfig> {
    private MyConfig config = new MyConfig();

    @Override public DirectoryStream.Filter<Path> getResourceLookupFilter() {
        return p -> p.getFileName().toString().equals("MyConfig.txt");
    }
    @Override public void processConfig(Path resource) throws IOException { /* parse arquivo */ }
    @Override public MyConfig getConfiguration() { return config; }
    @Override public void setConfiguration(MyConfig c) { this.config = c; }
}
```
Registre via `ConfigurationManager` no engine.

### 7.3 Novo MIME type
- Adicione em [`MediaTypes`](src/main/java/iped/properties/MediaTypes.java) como `public static final MediaType X_MEU = MediaType.application("x-meu");`
- Coordene com o detector Tika (META-INF/services em `iped-parsers`) se a detecção for por bytes.

## 8. ⚠️ Áreas sensíveis (não modificar sem revisão)

| Arquivo | Por quê |
|---|---|
| `IItem.java` | ~70 métodos; quebrar é quebrar engine, parsers, viewers, carvers, geo, webapi. |
| `IItemReader.java` | Estendida por `IItem`. Mesma criticidade. |
| `BasicProps.java` | As strings literais são chaves de documentos Lucene persistidos. Renomear inutiliza casos existentes. |
| `IIPEDSource.java` | Base do acesso a casos; muitos consumidores. |
| `MediaTypes.java` | Renomear MIME types invalida filtros e queries. |
| `ExtraProperties.java` | Renomear quebra parsers que populam esses campos. |
| `Configurable.java` | API base de toda configuração. |

**Regra geral:** prefira **adicionar** métodos com `default` (interfaces) ou novas constantes. **Nunca remova ou renomeie** sem migração coordenada em todos os módulos.

## 9. Onde estão as implementações?

- `IItem`, `IItemId` → `iped-engine/src/main/java/iped/engine/data/Item.java`, `ItemId.java`
- `IIPEDSource` → `iped-engine/src/main/java/iped/engine/data/IPEDSource.java`
- `IIPEDSearcher` → `iped-engine/src/main/java/iped/engine/search/IPEDSearcher.java`
- `IBookmarks`, `IMultiBookmarks` → `iped-engine/.../bookmarks/`
- `Configurable<*>` → `iped-engine/src/main/java/iped/engine/config/` (muitas instâncias)
- `SeekableInputStream` concretas → `iped-utils/src/main/java/iped/utils/SeekableFileInputStream.java` etc.

Use `Grep` para localizar implementações ou consumidores antes de mudar a interface.

## 10. Checklist antes de fazer PR contra esta API

- [ ] Mudança é aditiva (novo método, nova constante)? Se for breaking, há plano de migração?
- [ ] Adicionou javadoc em PT-BR no padrão do módulo?
- [ ] Atualizou `BasicProps.SET` se mexeu em `BasicProps`?
- [ ] Não renomeou strings literais que são chaves de índice Lucene?
- [ ] Verificou implementações em `iped-engine/data/Item.java`?
- [ ] Rodou `mvn -pl iped-api -am install` localmente?
