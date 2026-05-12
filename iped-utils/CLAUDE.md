# Módulo `iped-utils`

> Biblioteca de utilitários transversais usada por **todos** os outros módulos do IPED. Trate qualquer mudança aqui como sensível: modificações em `IOUtil`/`ImageUtil` impactam engine, parsers e viewers.

## 1. Propósito

Fundação técnica do IPED: I/O, streams, hashes, imagens, formatação localizada, datas, processo externo (ImageMagick), ícones e XML. Sem lógica de negócio — apenas helpers reutilizáveis.

- Empacotamento: `jar`. Compilado para **Java 11**.
- Versão: `4.4.0-SNAPSHOT` (parent `iped:iped-parent`).
- Consome `iped-api` para interfaces (`IHashValue`, `IItem`, `IItemReader`, `SeekableInputStream`).

## 2. Layout

Tudo vive no pacote único `iped.utils` (33 classes públicas). Organização lógica:

| Grupo | Classes |
|---|---|
| **I/O & Streams** | [`SeekableFileInputStream`](src/main/java/iped/utils/SeekableFileInputStream.java), [`LimitedInputStream`](src/main/java/iped/utils/LimitedInputStream.java), [`LimitedSeekableInputStream`](src/main/java/iped/utils/LimitedSeekableInputStream.java), `ByteArrayImageInputStream`, `EmptyInputStream`, `FileInputStreamFactory`, `SeekableInputStreamFactory`, `SimpleInputStreamFactory`, `RandomFilterInputStream`, `ReadOnlyRAFSeekableByteChannel`, `PreviewStreamSource` |
| **Image** | [`ImageUtil`](src/main/java/iped/utils/ImageUtil.java), [`IconUtil`](src/main/java/iped/utils/IconUtil.java), [`ExternalImageConverter`](src/main/java/iped/utils/ExternalImageConverter.java), `QualityIcon`, `SelectImagePathWithDialog` |
| **Hash & Conteúdo** | [`HashValue`](src/main/java/iped/utils/HashValue.java), `FileContentSource` |
| **Formato/i18n** | [`UTF8Properties`](src/main/java/iped/utils/UTF8Properties.java), [`LocalizedFormat`](src/main/java/iped/utils/LocalizedFormat.java), `Messages` |
| **Data/Tempo** | [`DateUtil`](src/main/java/iped/utils/DateUtil.java), [`TimeConverter`](src/main/java/iped/utils/TimeConverter.java) |
| **String/Encoding** | `StringUtil`, `SimpleHTMLEncoder`, `EmojiUtil` |
| **Sistema** | [`IOUtil`](src/main/java/iped/utils/IOUtil.java), `ProcessUtil` *(deprecated)*, `XMLUtil`, `LockManager` |
| **UI** | `UiUtil`, `SpinnerDialog` |

## 3. Classes-chave

### 3.1 [`IOUtil`](src/main/java/iped/utils/IOUtil.java) — núcleo de arquivos
Operações de arquivo, fechamento de streams, validação de segurança. Métodos críticos:

- `IOUtil.closeQuietly(Closeable)` — fecha ignorando exceções (padrão de cleanup).
- `IOUtil.getValidFilename(String, int)` — sanitiza para Windows/NTFS (máx 160 chars, escapa `\/:;*?"<>|`, reservadas `CON`, `PRN`, etc.).
- `IOUtil.isDangerousExtension(String)` — confere contra whitelist de ~102 extensões executáveis (`EXE`, `BAT`, `COM`, `DLL`, `PS1`, `VBS`, `JAR`...).
- `IOUtil.copyFile(File, File)` — cópia com buffer de 1 MB.
- `IOUtil.deleteDirectory(File)` — exclusão recursiva com workaround para Windows long paths.
- `IOUtil.canWrite(File)` — teste real de escrita (mais confiável que `File.canWrite()`).
- `IOUtil.loadInputStream(InputStream)` — **CUIDADO:** carrega tudo na memória.
- `IOUtil.isDiskFull(IOException)` — detecta "disco cheio" multilíngue.
- `IOUtil.ignoreInputStream(InputStream)` — thread daemon que drena stdout/stderr de `Process`.

> Centenas de chamadas em `iped-engine` e `iped-parsers`. **Não altere assinaturas existentes.**

### 3.2 [`ImageUtil`](src/main/java/iped/utils/ImageUtil.java) — processamento de imagem
- Redimensionamento: `resizeImage`, `resizeImageFixed`, `getSamplingFactor`.
- Subsampling otimizado para imagens grandes: `getSubSampledImage(IItemReader, int size, String mimeType)`.
- Orientação EXIF: `applyOrientation(BufferedImage, int orientation)` (1–8), `rotate(img, pos)` (0,90,180,270).
- Metadados JPEG: `readJpegWithMetaData`, `readJpegMetaDataComment`, `saveJpegWithMetadata`.
- Filtros: `blur`, `grayscale`, `getOpaqueImage`, `getCenteredImage`, `cloneImage`.
- Frames de vídeo (sprite): `getFrames`, `getBestFramesFit`, `getBmpFrames`.
- `updateImageIOPluginsPriority()` força TwelveMonkeys/Apache antes de Sun.
- Casos especiais: JBIG2 (via MIME type), ICO (múltiplas imagens, seleciona maior).

### 3.3 [`ExternalImageConverter`](src/main/java/iped/utils/ExternalImageConverter.java)
Wrapper para ImageMagick (`magick convert`) ou GraphicsMagick (`gm`). Detecção automática, pool de threads, timeouts dinâmicos.

System properties: `extImgConv.enabled`, `extImgConv.useGM`, `extImgConv.lowDensity`, `extImgConv.highDensity`, `extImgConv.magickAreaLimit`, `extImgConv.minTimeout`, `extImgConv.timeoutPerMB`, `extImgConv.winToolPathPrefix`.

Timeout = `minTimeout + (imageSizeMB * timeoutPerMB)`, dobrado em high-res.

### 3.4 [`HashValue`](src/main/java/iped/utils/HashValue.java)
Wrapper serializable para hashes (qualquer formato: MD5, SHA-1, SHA-256, edonkey). Implementa `iped.data.IHashValue`. Construtores aceitam `byte[]` ou string hex (decodificada via Apache Commons Codec — lança `IllegalArgumentException` se inválido).

### 3.5 [`DateUtil`](src/main/java/iped/utils/DateUtil.java) + [`TimeConverter`](src/main/java/iped/utils/TimeConverter.java)
- `DateUtil`: thread-safe via `ThreadLocal<DateFormat>`, suporta múltiplos formatos ISO-8601. `tryToParseDate(String)`, `dateToString(Date)`, `stringToDate(String)`.
- `TimeConverter`: conversão entre representações de timestamp:
  - `filetimeToMillis(long)`, `fileTimeToDate(long)` — NTFS (100-ns desde 1601).
  - `systemTimeToDate(long)` — Windows SYSTEMTIME.
  - `unixTimeToDate(long)` — Unix epoch (segundos).
  - `PRTimeToDate(long)` — Mozilla PR_TIME (microsegundos).

### 3.6 [`UTF8Properties`](src/main/java/iped/utils/UTF8Properties.java)
Substituto de `java.util.Properties` com suporte real a UTF-8 (Java padrão usa ISO-8859-1). Parsing manual de comentários (`#`), suporte a escape de `=`. Carrega/grava com chaves ordenadas.

### 3.7 [`LimitedInputStream`](src/main/java/iped/utils/LimitedInputStream.java) e [`LimitedSeekableInputStream`](src/main/java/iped/utils/LimitedSeekableInputStream.java)
Decoradores que limitam leitura a N bytes (com preservação de `mark`/`reset`). O `LimitedSeekableInputStream` adiciona `start` (offset inicial) — usado em parsers de containers.

### 3.8 [`SeekableFileInputStream`](src/main/java/iped/utils/SeekableFileInputStream.java)
Implementação de `iped.io.SeekableInputStream` via `FileChannel` (NIO). Preferida sobre `RandomAccessFile`.

### 3.9 [`IconUtil`](src/main/java/iped/utils/IconUtil.java)
Cache thread-safe de ícones. `getToolbarIcon(name, resPath)`, `getIcon(name, resPath, size)`, `getIconImages(name, resPath)`. Carrega PNG do classpath, gera variações redimensionadas via `ImageUtil.resizeImage`, renderiza com `QualityIcon` (antialiasing).

### 3.10 Outros
- `LocalizedFormat` — `NumberFormat` thread-safe respeitando locale resolvido por `LocaleResolver`.
- `StringUtil` — `convertCamelCaseToSpaces`, `getIgnoreCaseComparator`.
- `SimpleHTMLEncoder` — escape de `&`, `<`, `>`, `"`, `'` (origem Apache).
- `XMLUtil` — `getFirstElement(Element, tagName)`.
- `ProcessUtil` — **deprecated**, descobre/mata processo por porta ou nome (Windows `wmic`, Linux `ps`).
- `EmojiUtil` — codifica emojis para HTML (usado em viewers).

## 4. Dependências

```xml
<dependency> <groupId>org.slf4j</groupId>                  <artifactId>slf4j-api</artifactId>                     </dependency>
<dependency> <groupId>net.jpountz.lz4</groupId>            <artifactId>lz4</artifactId>            <version>1.3</version> </dependency>
<dependency> <groupId>commons-codec</groupId>              <artifactId>commons-codec</artifactId>  <version>1.15</version></dependency>
<dependency> <groupId>iped</groupId>                       <artifactId>iped-api</artifactId>       <version>${project.version}</version></dependency>
<dependency> <groupId>com.github.ben-manes.caffeine</groupId> <artifactId>caffeine</artifactId>    <version>3.2.2</version></dependency>
```

Indiretas (JDK): `javax.imageio.*`, `java.nio.file.*`, `java.awt.image.*`, `org.w3c.dom.*`, `javax.swing.*`.

## 5. Padrões e convenções

- **Filter/Decorator**: `LimitedInputStream extends FilterInputStream`.
- **Factory**: `FileInputStreamFactory`, `SimpleInputStreamFactory`.
- **Thread-safety**:
  - `ThreadLocal<DateFormat>` (DateUtil) — não compartilhe `SimpleDateFormat`.
  - `volatile` para flags (ex.: `IOUtil.ContainerVolatile`).
  - `synchronized` raramente, apenas para *one-time initialization* (`ExternalImageConverter.adjustCommand`).
- **Charset explícito**: `new InputStreamReader(file, "UTF-8")` ou `StandardCharsets.ISO_8859_1`. **Nunca** charset implícito.
- **Logger**: SLF4J — `LoggerFactory.getLogger(ClassName.class)`. Não use `System.out`/`err` (exceto `ProcessUtil` deprecated).
- **Resource management**: prefira `try-with-resources`; `IOUtil.closeQuietly` para limpeza em `finally`.

## 6. Como contribuir

| Quero adicionar… | Onde |
|---|---|
| Função I/O genérica | `IOUtil` (estático). Documente thread-safety. |
| Operação de imagem | `ImageUtil` (estático). |
| Wrapper de stream | nova classe em `iped.utils`. |
| Conversão de data/timestamp | `DateUtil` ou `TimeConverter`. |
| Helper XML | `XMLUtil`. |
| Utilidade de string | `StringUtil`. |

Convenções:
- Métodos estáticos para utilitários sem estado.
- `get*` sem efeito colateral, `read*/write*` para I/O.
- `is*/has*` para booleans.

## 7. ⚠️ Classes sensíveis (top hits)

| Método | Razão |
|---|---|
| `IOUtil.closeQuietly` | >100 callers; mudança de assinatura quebra tudo. |
| `IOUtil.getValidFilename` | Crítico para segurança (NTFS reserved names, caracteres inválidos). |
| `ImageUtil.getSubSampledImage` | >40 callers; bottleneck de performance. |
| `HashValue(String)` | Parser hex usado em todo lookup de hash. |

Mudar essas classes exige busca cruzada (`Grep`) por todos os usos em `iped-engine`, `iped-parsers-impl`, `iped-viewers-impl`.

## 8. Consumidores principais

- **iped-engine** (~137 imports): `Item`, `IPEDSource`, `Manager`, `ImageThumbTask`, `DocThumbTask`, `VideoThumbTask`, `ExportFileTask`, `HTMLReportTask`, `IndexTask`, `ElasticSearchIndexTask`, `ParsingTask`.
- **iped-parsers-impl** (~108 imports): WhatsApp, Telegram, Discord, APKParser, OCRParser, Win10MailParser, PDF parser etc. Tipicamente para `DateUtil`, `TimeConverter`, `IOUtil`, `UTF8Properties`.
- **iped-viewers-impl** (~36 imports): `ImageViewer`, `TiffViewer`, `HexViewerPlus`, `AudioViewer`, `MetadataViewer`, `HtmlViewer`. Principalmente `ImageUtil`, `IconUtil`, `IOUtil`, `LocalizedFormat`.

## 9. Checklist de PR

- [ ] Não removeu ou renomeou método público existente.
- [ ] Charset explícito em qualquer I/O de texto.
- [ ] Resource cleanup garantido (`try-with-resources` ou `IOUtil.closeQuietly`).
- [ ] Logger SLF4J — sem `System.out`.
- [ ] Se mexeu em `IOUtil` ou `ImageUtil`, rodou ao menos `mvn -pl iped-engine,iped-parsers/iped-parsers-impl,iped-viewers/iped-viewers-impl test`.
