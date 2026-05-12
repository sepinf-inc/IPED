# Módulo `iped-parsers`

> Camada de **extração estruturada** do IPED. Estende o framework Apache Tika para transformar arquivos brutos (banco de dados de WhatsApp, registry do Windows, PST, etc.) em itens com metadados indexáveis e subitens navegáveis.

## 1. Propósito

- Decodificar formatos complexos (chats, browsers, registry, P2P, etc.) e expor o conteúdo como **subitens** (`embedded documents` no léxico Tika).
- Gerar **metadados padronizados** (em `BasicProps` ou `ExtraProperties` de `iped-api`) que serão indexados pelo `iped-engine`.
- Produzir **HTML renderizável** quando aplicável (chat reports, mailbox views), usado pelos viewers.

O módulo é dividido em dois sub-projetos Maven:
- [`iped-parsers-impl/`](iped-parsers-impl) — implementações concretas (~70 pacotes, 200+ classes).
- [`java-dbf/`](java-dbf) — fork patched de biblioteca para parsing de arquivos dBase III/IV (DBF).

## 2. Estrutura de `iped-parsers-impl`

```
iped/parsers/
├── apk/                # APK Android (manifest + assets)
├── ares/               # P2P Ares (.ares)
├── bittorrent/         # .torrent, resume.dat, Transmission
├── browsers/           # Chrome, Firefox, Edge, Safari, IE (places, history, cache)
├── chat/               # Helpers para nomes de participantes (PartyStringBuilder)
├── compress/           # RAR, 7Z, LZFSE, Package
├── database/           # XBase/DBF, ESEDB (Extensible Storage Engine), MSAccess
├── discord/            # Cache JSON + binário Discord
├── emule/              # known.met, part.met, preferences.dat (ED2K hashes)
├── eventtranscript/    # Windows 11 Event Transcript (XML)
├── evtx/               # Windows Event Log binário (BinaryXML)
├── external/           # Wrapper para executáveis CLI externos
├── fork/               # ForkParser para parsing isolado
├── gdrive/             # Google Drive (snapshot, cloud)
├── image/              # TIFF multi-página
├── jdbc/               # Base para parsers via JDBC
├── lnk/                # Windows .lnk shortcuts
├── mail/               # RFC822, PST (libpst & libpff), DBX, Mbox, IncrediMail, MSG, Win10Mail
├── misc/               # OLE, OFX, OFC, genéricos
├── ocr/                # Tesseract via JEP (Python embedded)
├── plist/              # Apple Plist detector
├── python/             # Bytecode Python (.pyc/.pyo)
├── registry/           # Windows Registry + RegRipper wrapper
├── security/           # Certificates, KeyStore, CryptoAPI blobs
├── shareaza/           # P2P Shareaza (binários MFC)
├── skype/              # Skype legacy + v8+ SQLite
├── sqlite/             # SQLite core, container detector, undelete (slots deletados)
├── standard/           # StandardParser (composite + RawStringParser fallback)
├── telegram/           # Telegram Android/iOS (cache decriptado, protobuf)
├── threema/            # Threema messenger (zip backup)
├── tor/                # Tor Browser cache (index files)
├── ufed/               # Universal Forensic Extraction Device (Cellebrite)
├── usnjrnl/            # NTFS $UsnJrnl:$J (change journal)
├── util/               # Utilitários (handlers, conversores, ItemInfo)
├── vcard/              # vCard contacts
├── video/              # FLV wrapper + MP4 custom detector
├── vlc/                # VLC ini
├── whatsapp/           # WhatsApp Android/iOS (decriptação + protobuf)
└── winx/               # Windows Timeline (10/11)
```

Recursos em `src/main/resources/`:
- `META-INF/services/org.apache.tika.detect.Detector` — registro SPI dos detectores customizados (`SQLiteContainerDetector`, `PListDetector`, `MP4Detector`).
- `iped/parsers/{whatsapp,telegram,discord,threema}/` — templates HTML, CSS, JS e imagens (avatares, ícones) usados para gerar relatórios.
- `nativelibs/libesedb/` — DLLs do libesedb usadas pelo parser de cache do Edge.
- `AllowedRawMailHeaders.txt` — whitelist de headers de email.

## 3. Modelo de um parser IPED

Todo parser herda de `org.apache.tika.parser.AbstractParser` (ou derivados como `SQLite3DBParser`, `AbstractDBParser`).

```java
public class MyFormatParser extends AbstractParser {
    private static final long serialVersionUID = 1L;
    public static final MediaType MY_MIME = MediaType.application("x-myformat");
    private static final Set<MediaType> SUPPORTED = Collections.singleton(MY_MIME);

    @Field public void setExtractEntries(boolean v) { this.extractEntries = v; } // configurável

    @Override public Set<MediaType> getSupportedTypes(ParseContext ctx) { return SUPPORTED; }

    @Override
    public void parse(InputStream in, ContentHandler handler, Metadata md, ParseContext ctx)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, md);
        xhtml.startDocument();
        // ... decode → escrever HTML, popular metadata, gerar subitens
        xhtml.endDocument();
    }
}
```

### Geração de subitens (embedded documents)

```java
EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
        new ParsingEmbeddedDocumentExtractor(context));

Metadata subMeta = new Metadata();
subMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, "msg-001.txt");
subMeta.set(StandardParser.INDEXER_CONTENT_TYPE, "message/x-custom-message");
subMeta.set(ExtraProperties.DECODED_DATA, "true");

extractor.parseEmbedded(new ByteArrayInputStream(content), handler, subMeta, false);
```

### Contexto IPED disponível no `ParseContext`

- `ItemInfo` — metadados do item em processamento.
- `IItemReader` — acesso somente-leitura ao item.
- `IItemSearcher` — busca em itens já indexados (útil para resolver referências, ex.: anexos).
- `ICaseData` — dados globais do caso.
- `EmbeddedDocumentExtractor` — factory de subitens.

## 4. Parsers principais por categoria

### Browsers (`iped.parsers.browsers`)
Todos herdam de `AbstractSqliteBrowserParser` (extends `SQLite3DBParser`). Geram subitens `Visit`, `Download`, `Search`.
- `ChromeSqliteParser` → `x-chrome-history`, `x-chrome-downloads`, `x-chrome-searches`, cache via `CacheIndexParser`.
- `FirefoxSqliteParser` → `places.sqlite`.
- `EdgeWebCacheParser` → ESENT (`WebCacheV01.dat`) via libesedb nativa.
- `SafariSqliteParser` + `SafariPlistParser`.
- `IndexDatParser` → IE legacy.

### Comunicação
- `WhatsAppParser` — msgstore.db (Android/iOS); `ExtractorAndroid`, `ExtractorIOS`, `Message`, `Chat`, `WAContact`. Suporta decriptação. Renderiza HTML com `wachat-html-template.txt` + CSS/JS.
- `TelegramParser` — `database.sqlite`, protobuf, cache decriptado.
- `SkypeParser` — `main.db` (v8+ SQLite) ou binário legado.
- `DiscordParser` — JSON + cache binário (gzip/brotli/base64).
- `ThreemaParser` — backup `.zip`.

### Email
- `RFC822Parser` — `.eml`, `message/rfc822`, `message/x-emlx`. Usa `mime4j`. Whitelist em `AllowedRawMailHeaders.txt`.
- `OutlookPSTParser` (java-libpst) e `LibpffPSTParser` (libpff nativa).
- `OutlookDBXParser`, `MboxParser`, `IncrediMailParser`, `Win10MailParser`.

### Sistema (Windows)
- `RegistryParser` (+ plug-in `RegistryKeyParserManager`) — SYSTEM, SOFTWARE, SAM, SECURITY, NTUSER.DAT.
- `RegRipperParser` — wrapper para o tool externo.
- `EvtxParser` — Event Log binário.
- `EventTranscriptParser` — Windows 11.
- `UsnJrnlParser` — `$UsnJrnl:$J` (gera timeline).
- `WinXTimelineParser` — Windows Timeline.
- `LNKShortcutParser` — atalhos.

### P2P
- eMule: `KnownMetParser`, `PartMetParser`, `PreferencesDatParser` (hashes ED2K, detecção CSAM).
- Shareaza: `ShareazaDownloadParser`, `ShareazaLibraryDatParser`, `ShareazaSearchesDatParser` (binário MFC).
- Ares: `AresParser` + `AresDecoder`.
- BitTorrent: `TorrentFileParser`, `BitTorrentResumeDatParser`, `TransmissionResumeParser` (lib `bencode`).

### SQLite-based
Base: `SQLite3DBParser` → `AbstractDBParser`.
- Connection pool com `SQLiteConfig` read-only.
- WAL/Journal export para recuperação.
- `SQLiteUndelete` recupera slots deletados.
- `TableReportGenerator` cria relatório HTML por tabela.
- `SQLiteContainerDetector` identifica SQLite dentro de blobs.

### Carving e fallback
- `StandardParser` — Composite que detecta MIME, despacha para parser apropriado, com **fallback para `RawStringParser`** em caso de erro.
- `RawStringParser` — extrai texto via regex, calcula entropia. Última linha de defesa.

### Outros
- `APKParser`, `VCardParser`, `OFXParser`, `GenericOLEParser`, `ExternalParser`, `PythonParser`, `OCRParser` (Tesseract via JEP), `SecurityParser`, `UfedParser`, `TiffPageParser`, `EmptyVideoParser`, `FLVParserWrapper`, `MP4Detector`.

## 5. Dependências principais

| Lib | Versão | Para que serve |
|---|---|---|
| `tika-core`, `tika-parsers-standard-package` | 2.4.0(-p1) | Framework de parsing |
| `tika-parser-sqlite3-module` | 2.4.0 | SQLite via Tika |
| `sqlite-jdbc` | 3.41.2.2 | SQLite JDBC driver |
| `java-libpst` | 0.9.5-20210209-p1 | Outlook PST |
| `bencode` | 1.4.1 | BitTorrent / .torrent |
| `sevenzipjbinding(-all-platforms)` | 16.02-2.01 | RAR, 7Z |
| `icepdf` | 7.0.0 | PDF rendering (mas core PDF é PDFBox no engine) |
| `isoparser` | 1.9.41.6 | MP4 atoms |
| `libfqlite` | 1.57.05 | SQLite undelete |
| `png-reader` | jdk11+28-p1 | PNG metadata |
| `dd-plist` | 1.26 | Apple Plist |
| `telegram-decoder-{api,impl}` | 1.0.14 | Telegram decode |
| `jna` | 5.7.0 | Native libs (libesedb) |
| `jep` | 4.0.3 | Java Embedded Python (OCR) |
| `apk-parser` | 2.6.10 | APK Android |
| `ofx4j` | 1.36 | Open Financial Exchange |
| `ez-vcard` | 0.10.6 | vCard |
| `jackson-{core,databind}`, `gson`, `openjson`, `json-simple` | – | JSON variantes |
| `commons-text`, `commons-lang`, `commons-validator` | – | utilitários |
| `reflections`, `j2html`, `guava`, `spring-expression` | – | reflexão/HTML/avaliador |
| `mockito-core` (test) | 3.8.0 | Testes |

`java-dbf` é dependência interna (submódulo) usado pelo `XBaseParser`.

## 6. Registro de parsers e MIME types

- **Parsers**: descobertos via Tika `CompositeParser`/`TikaConfig.getDefaultConfig()`. O IPED usa `StandardParser` como ponto de entrada — ele agrega `getAllComponentParsers()` do Tika.
- **Detectores**: registrados em `META-INF/services/org.apache.tika.detect.Detector`. Para criar um detector novo, implemente `Detector` e adicione a linha no SPI.

Fluxo simplificado:
```
iped-engine → StandardParser.parse()
   1. Detector chain → MediaType
   2. Procura parser para o MediaType
   3. parser.parse() → escreve XHTML + Metadata
   4. Em erro: fallback para RawStringParser
   5. Subitens via EmbeddedDocumentExtractor
```

## 7. Convenções para metadados

- Sempre defina `TikaCoreProperties.RESOURCE_NAME_KEY` ao gerar um subitem.
- Para subitens, use `StandardParser.INDEXER_CONTENT_TYPE` (NÃO `Metadata.CONTENT_TYPE`) — o engine usa esse campo para indexar como o tipo "lógico" do subitem.
- Marque conteúdo decodificado com `ExtraProperties.DECODED_DATA = "true"`.
- Linhas de comunicação:
  - `ExtraProperties.COMMUNICATION_FROM/TO/DATE/DIRECTION`
  - `ExtraProperties.CONVERSATION_ID/NAME/PARTICIPANTS/...`
  - `ExtraProperties.MESSAGE_SUBJECT/BODY/DATE/ATTACHMENT_COUNT`
- Anexos referenciados: `ExtraProperties.LINKED_ITEMS` + `ExtraProperties.SHARED_HASHES`.
- **Nunca sobrescreva** chaves de `BasicProps` (`NAME`, `LENGTH`, `HASH`, `MODIFIED`...) — são populadas pelo engine; mexer aí quebra indexação.

## 8. Testes

- Estrutura em `src/test/java/iped/parsers/`. Base: `AbstractPkgTest` com `EmbeddedTrackingParser` (rastreador de subitens) e `ParseContext` preconfigurado.
- Fixtures de arquivos pequenos em `src/test/resources/`.
- Comandos:
  ```bash
  mvn -pl iped-parsers/iped-parsers-impl test                     # todos
  mvn -pl iped-parsers/iped-parsers-impl test -Dtest=WhatsAppParserTest
  mvn -pl iped-parsers/iped-parsers-impl test -Dtest='*Mail*'
  mvn -pl iped-parsers/iped-parsers-impl jacoco:report            # coverage em target/site/jacoco/
  ```
- Debug remoto: `mvn -Dmaven.surefire.debug test` (porta 5005).

## 9. Como adicionar um novo parser

1. Crie a classe em `iped.parsers.&lt;categoria&gt;.MyFormatParser`.
2. Defina `MediaType` público (`public static final MediaType MY_MIME = ...`).
3. Implemente `getSupportedTypes` e `parse`.
4. Se a detecção precisa de magic bytes, implemente um `Detector` e registre em `META-INF/services/org.apache.tika.detect.Detector`.
5. Adicione teste em `src/test/java/.../MyFormatParserTest.java` estendendo `AbstractPkgTest`. Anexe fixtures em `src/test/resources`.
6. Não esqueça: parsers devem ser **stateless** (exceto os campos `@Field` injetados pelo Tika) e **serializáveis** (`serialVersionUID`).

## 10. Checklist de PR

- [ ] Parser herda de `AbstractParser` (ou `SQLite3DBParser`/`AbstractDBParser` quando aplicável).
- [ ] `getSupportedTypes()` retorna `Set<MediaType>` (use `Collections.singleton` se único).
- [ ] Subitens recebem `TikaCoreProperties.RESOURCE_NAME_KEY` e `StandardParser.INDEXER_CONTENT_TYPE`.
- [ ] Não usa charset implícito — UTF-8 ou ISO-8859-1 explícito.
- [ ] Streams fechados via try-with-resources (TikaInputStream geralmente gerencia).
- [ ] Teste JUnit cobrindo subitens e metadados principais.
- [ ] Se adicionou detector, registrou em `META-INF/services/...`.
- [ ] Se adicionou recurso (HTML/CSS/JS), colocou em `iped/parsers/&lt;categoria&gt;/`.

## 11. Submódulo `java-dbf`

Fork patched do `javadbf` para parsing de arquivos dBase III/IV (.dbf). Consumido por `iped.parsers.database.XBaseParser`. Mudanças aqui são raras; cuide para manter compatibilidade com testes existentes.
