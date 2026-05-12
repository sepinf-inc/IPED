# Módulo `iped-carvers`

> **Data carving** — recuperação de arquivos por reconhecimento de assinatura (magic bytes), inclusive em espaço não alocado e dentro de outros arquivos. Núcleo do que faz o IPED encontrar evidências apagadas.

## 1. O que é "carving" aqui

- Percorrer o conteúdo de um item byte a byte procurando **headers** (e opcionalmente **footers**) de formatos conhecidos.
- Quando uma assinatura é encontrada, **calcular o tamanho** do arquivo recuperado (via campo no header, footer ou stop-on-next-header) e **criar um subitem** (`IItem.createChildItem()`) com o offset/tamanho.
- Funciona em espaço não alocado (`application/x-unallocated`) e também em outros tipos selecionáveis (configuráveis).

A implementação usa o algoritmo **Aho-Corasick** para procurar dezenas de padrões em uma única passagem `O(n + m + z)`.

## 2. Sub-módulos

```
iped-carvers/
├── iped-ahocorasick/   # Implementação do algoritmo Aho-Corasick (versão 1.1)
├── iped-carvers-api/   # Contratos: Carver, CarverType, Signature, Hit, CarverConfiguration, CarvedItemListener
└── iped-carvers-impl/  # Carvers concretos + Default/AbstractCarver + XMLCarverConfiguration
```

## 3. Aho-Corasick (`iped-ahocorasick`)

Implementação em Java do algoritmo de Aho & Corasick (1975) para busca simultânea de múltiplos padrões. Adaptado da implementação de Danny Yoo (BSD).

Classes principais:
- `AhoCorasick` — constrói a máquina de estados.
  - `add(byte[] keyword, Object output)` — adiciona padrão.
  - `prepare()` — constrói a tabela de falhas (`failure function`).
  - `search(byte[] bytes)` — devolve `Iterator<SearchResult>`.
- `State` — nó da árvore; `edgeList` (DenseEdgeList/SparseEdgeList), `fail` (link de falha), `outputs` (matches).
- `Searcher` — iterator que avança por `continueSearch()`.
- `SearchResult` — encapsula `lastMatchedState`, `lastIndex`, `getOutputs()`.

A escolha entre `DenseEdgeList` (array) e `SparseEdgeList` (lista) é uma otimização cache-friendly: arrays para estados com muitas transições.

**Por que importa**: sem Aho-Corasick, buscar N padrões custaria `O(n × m × p)`. Com ele, é `O(n + z)` independentemente do número de padrões — viabiliza centenas de assinaturas em gigabytes de dados.

## 4. API (`iped-carvers-api`)

### `Carver`
```java
public interface Carver {
    void notifyHit(IItem parent, Hit hit) throws IOException;
    void notifyEnd(IItem parent) throws IOException;
    IItem carveFromHeader(IItem parent, Hit header) throws IOException;
    IItem carveFromFooter(IItem parent, Hit footer) throws IOException;
    long getLengthFromHit(IItem parent, Hit hit) throws IOException;
    CarverType[] getCarverTypes();
    void registerCarvedItemListener(CarvedItemListener listener);
    void removeCarvedItemListener(CarvedItemListener listener);
    void setIgnoreCorrupted(boolean ignore);
}
```

### `Signature` + `SignatureType`
```java
enum SignatureType { HEADER, FOOTER, ESCAPEFOOTER, LENGTHREF, CONTROL }
```
Suporta **wildcards** (`?` = qualquer byte) e **escapes hex** (`\xx`). Ex.: `RIFF????AVI LIST` ou `\ff\d8\ff`. Internamente uma assinatura com `?` é dividida em subsequências, e o carver só conta como hit quando elas aparecem em ordem.

### `Hit`
Imutável: `(Signature sig, long off)`.

### `CarverType`
Define um formato: nome, MIME type, max/min length, sinaturas (`addHeader`, `addFooter`, `addEscapeFooter`), info do campo de tamanho (`sizePos`, `sizeBytes`, `bigEndian`), flags (`stopOnNextHeader`).

### `CarverConfiguration`
Implementação principal: `XMLCarverConfiguration` (em `iped-carvers-impl`). Lê `CarverConfig.xml`:
- `getCarverTypes()` — todos os tipos configurados.
- `isToProcess(MediaType)` / `isToNotProcess(MediaType)` — filtros por MIME.
- `getPopulatedTree()` — `AhoCorasick` carregada com todos os padrões.
- `createCarverFromJSName(String script)` — factory para carvers JS.

### `CarvedItemListener`
Callback `processCarvedItem(parent, carvedEvidence, offset)` — invocado pelo carver para cada arquivo recuperado. No engine, isso é tratado pelo `BaseCarveTask` para adicionar o subitem ao caso.

## 5. Implementação (`iped-carvers-impl`)

### `AbstractCarver`
Base com a máquina de estados de matching de header/footer.
- Fila `ArrayDeque<Hit> headersWaitingFooters` — headers esperando footer.
- `LinkedList<Hit> headersWithStopOnNext` — para carvers com flag `stopOnNextHeader` (sem footer, cortam ao próximo header).
- `maxWaitingHeaders = 1000` evita crescimento ilimitado.

Algoritmo `notifyHit()`:
1. Header sem footer:
   - Com `lengthRef`: enfileira até ver `LENGTHREF`.
   - Com `stopOnNextHeader`: lista especial.
   - Caso contrário: `carveFromHeader()` imediato.
2. `LENGTHREF`: usa header mais recente, `carveFromLengthRef()`.
3. Header com footer: enfileira.
4. Footer: testa escape-footer; se válido, `carveFromFooter()` pareando com o header mais antigo (`FromFarthestHeaderCarver` pega o mais distante quando há múltiplos).
5. `ESCAPEFOOTER`: marca para não fechar footers seguintes.

Validação:
```java
public boolean isValid(IItem parent, Hit header, long length) {
    try { validateCarvedObject(parent, header, length); return true; }
    catch (InvalidCarvedObjectException e) { return false; }
}
```
Subclasses sobrescrevem `validateCarvedObject` para parsing/validação específica.

### `DefaultCarver`
Pega tamanho de um **campo no header** (configurado por XML). RIFF é especial (soma 8 ao tamanho). Quando `sizePos == -1`, usa `maxLength` ou o tamanho restante do parent.

### `FromFarthestHeaderCarver`
Quando há múltiplos headers antes de um footer, pareia com o mais distante (ex.: ZIP).

### Carvers especializados
- `PDFCarver` — procura `startxref` antes do footer, valida `xref`.
- `ZIPCarver` — usa contagem de entradas do Central Directory.
- `EMLCarver` — boundaries MIME `--\r\n`, `carveFromLastFooter` espera próximo header.
- `TorrentCarver` — usa `BencodingInputStream` para validar dicionário.
- `SQLiteCarver` — valida page size (potência de 2), versões, encoding.
- `MOVCarver`, `MatroskaCarver`, `OpusCarver`, `SevenZipCarver`, `OLECarver` — herdam de `AbstractCarver` com validações específicas.
- `JSCarver` — carvers em JavaScript (Nashorn) configurados por `carverScriptFile`.

## 6. Pipeline no engine

```
iped.engine.task.carver.CarverTask  (extends BaseCarveTask)
  ├─ isToProcess(MediaType)?
  ├─ getBufferedInputStream() do parent
  ├─ findSig(InputStream):
  │   ├─ Lê em chunks de 1 MB
  │   ├─ AhoCorasick.search(buffer) → para cada Hit
  │   │   └─ Carver.notifyHit() → carveFromHeader / carveFromFooter
  │   │        └─ CarvedItemListener.processCarvedItem()
  │   └─ Carver.notifyEnd()
  └─ BaseCarveTask.addCarvedEvidence() → adiciona ao caso
```

Carvers especializados relacionados estão em `iped-engine/src/main/java/iped/engine/task/carver/`:
- `BaseCarveTask`, `CarverTask`, `CarverTaskConfig`
- `KnownMetCarveTask` (eMule), `LedCarveTask` (recuperação por fragmento conhecido)
- `XMLCarverConfiguration`

## 7. Configuração — `conf/CarverConfig.xml`

```xml
<carverconfig>
    <typesToProcess>application/x-unallocated;...</typesToProcess>
    <typesToNotProcess>application/zip;application/pdf;...</typesToNotProcess>
    <ignoreCorrupted>true</ignoreCorrupted>

    <carverTypes>
        <carverType>
            <name>JPEG</name>
            <mediaType>image/jpeg</mediaType>
            <signatures>
                <headerSignature>\ff\d8\ff</headerSignature>
                <footerSignature>\ff\d9</footerSignature>
                <escapeFooterSignature>\ff\ff\d9</escapeFooterSignature>
            </signatures>
            <minLength>100</minLength>
            <maxLength>52428800</maxLength>
        </carverType>

        <carverType>
            <name>SQLITE</name>
            <carverClass>iped.carvers.impl.SQLiteCarver</carverClass>
            <signatures>
                <headerSignature>SQLite format 3\x00</headerSignature>
            </signatures>
            <maxLength>1073741824</maxLength>
        </carverType>

        <carverType>
            <name>MY_FMT</name>
            <carverScriptFile>MyFormat.js</carverScriptFile>
            <mediaType>application/x-myformat</mediaType>
        </carverType>
    </carverTypes>
</carverconfig>
```

Elementos por `carverType`:

| Elemento | Tipo | Descrição |
|---|---|---|
| `name` | String | Identificador. |
| `mediaType` | MIME | Tipo atribuído ao subitem. |
| `carverClass` | FQN | Implementação Java (opcional; padrão `DefaultCarver`). |
| `carverScriptFile` | path | Arquivo `.js` (opcional). |
| `signatures` | bloco | `headerSignature`, `footerSignature`, `escapeFooterSignature`, `lengthRefSignature`, `controlSignature`. |
| `minLength`, `maxLength` | long | Faixa de tamanho válido. |
| `lengthOffset`, `lengthSizeBytes`, `lengthBigEndian` | int/bool | Para `DefaultCarver`. |
| `stopOnNextHeader` | bool | Sem footer; corta ao próximo header. |

Wildcards/escape em assinaturas: `?` (qualquer byte), `\xx` (hex), `\\` (literal).

## 8. Como adicionar um novo formato

### A. Carver "puro" (XML, sem código)
Para formatos com tamanho derivável (campo fixo ou header/footer simples). Edite `CarverConfig.xml`:
```xml
<carverType>
    <name>MYFORMAT</name>
    <signatures><headerSignature>MYFT</headerSignature></signatures>
    <minLength>100</minLength>
    <maxLength>10485760</maxLength>
    <lengthOffset>4</lengthOffset>
    <lengthSizeBytes>4</lengthSizeBytes>
    <lengthBigEndian>false</lengthBigEndian>
    <mediaType>application/x-myformat</mediaType>
</carverType>
```

### B. Carver com validação Java
```java
public class MyFormatCarver extends DefaultCarver {
    public MyFormatCarver() throws DecoderException {
        carverTypes = new CarverType[1];
        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("MYFT");
        carverTypes[0].setMimeType(MediaType.parse("application/x-myformat"));
        carverTypes[0].setMaxLength(10_485_760);
        carverTypes[0].setMinLength(100);
    }

    @Override
    public long getLengthFromHit(IItem parent, Hit header) throws IOException {
        try (SeekableInputStream is = parent.getSeekableInputStream()) {
            is.seek(header.getOffset() + 4);
            // ... ler, decodificar little-endian
        }
        return ...;
    }

    @Override
    public void validateCarvedObject(IItem parent, Hit header, long length)
            throws InvalidCarvedObjectException {
        if (!verificaEstrutura(...)) throw new InvalidCarvedObjectException("Invalid");
    }
}
```
Aponte no XML: `<carverClass>iped.carvers.custom.MyFormatCarver</carverClass>`.

### C. Carver em JavaScript
Crie `carvers/MyFormat.js`:
```javascript
function getCarverTypes() {
    var ct = new CarverType();
    ct.setName("MYFORMAT");
    ct.addHeader("MYFT");
    ct.setMinLength(100);
    ct.setMaxLength(10485760);
    ct.setMimeType(MediaType.parse("application/x-myformat"));
    return [ct];
}
function getLengthFromHeader(parent, header) { /* ... */ }
function validateCarvedObject(parent, header, length) { /* throws InvalidCarvedObjectException */ }
```
Configure com `<carverScriptFile>MyFormat.js</carverScriptFile>`.

## 9. Dependências

```xml
<!-- iped-ahocorasick: SEM dependências externas -->

<!-- iped-carvers-api -->
<dependency> <groupId>iped</groupId>               <artifactId>iped-utils</artifactId>      <version>${project.version}</version></dependency>
<dependency> <groupId>iped</groupId>               <artifactId>iped-api</artifactId>        <version>${project.version}</version></dependency>
<dependency> <groupId>org.apache.tika</groupId>    <artifactId>tika-core</artifactId>        <version>2.4.0-p1</version>        </dependency>
<dependency> <groupId>com.dampcake</groupId>       <artifactId>bencode</artifactId>          <version>1.4.1</version>           </dependency>
<dependency> <groupId>iped</groupId>               <artifactId>iped-ahocorasick</artifactId> <version>1.1</version>              </dependency>

<!-- iped-carvers-impl -->
<dependency> <groupId>iped</groupId>               <artifactId>iped-carvers-api</artifactId> <version>${project.version}</version></dependency>
<dependency> <groupId>junit</groupId>              <artifactId>junit</artifactId>            <scope>test</scope>                  </dependency>
```

## 10. Threads e concorrência

- IPED cria **um carver por item** (dentro de `CarverTask`) — `AbstractCarver` **não é** thread-safe; cada thread tem sua instância.
- `BaseCarveTask` mantém contador global `itensCarved` em `synchronized static` para thread-safety.
- `CarvedItemListener.processCarvedItem()` é chamado da thread do worker — listeners devem ser thread-safe ou sincronizar internamente.

## 11. ⚠️ Cuidados

| Item | Risco |
|---|---|
| `AhoCorasick` | NÃO modifique sem teste de regressão; afeta toda detecção. |
| `AbstractCarver.headersWaitingFooters` (ArrayDeque) | Não thread-safe propositalmente; preserve. |
| `maxWaitingHeaders` | Subir demais aumenta memória; cair demais perde matches válidos. |
| Renomear `name` ou `mediaType` em `CarverConfig.xml` | Quebra estatísticas históricas e filtros. |
| `getLengthFromHit` | `seek`/`read` corretos; cuidado com offsets negativos ou maiores que `parent.getLength()`. |

## 12. Performance

- Buffer 1 MB equilibra cache hit rate e overhead de leitura.
- Aho-Corasick *single-pass*.
- Wildcards são divididos em subsequências (TreeMap `offset → seqIndex`).
- `clearOldHeaders()` evita memória descontrolada.

## 13. Checklist de PR

- [ ] Tipo novo definido em `CarverConfig.xml` (e em `iped-engine/.../scripts/...` se for JS).
- [ ] Para validação Java: classe estende `DefaultCarver` ou `AbstractCarver`; estados de instância são por-thread.
- [ ] `MediaType` registrado (em `MediaTypes` de `iped-api` se for tipo IPED, ou em Tika via detector).
- [ ] Adicionou testes JUnit em `iped-carvers-impl/src/test/java/...`.
- [ ] Testou contra arquivo grande para garantir que `headersWaitingFooters` não explode.
- [ ] Validou que filtros `typesToProcess`/`typesToNotProcess` não excluem indevidamente o novo tipo.
