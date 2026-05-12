# Módulo `iped-viewers`

> Camada de **visualização** do IPED. Renderiza o conteúdo de cada item (PDF, imagens, e-mail, hexadecimal, áudio, documentos Office, etc.) na interface principal.

## 1. Propósito

Um *viewer* é uma estratégia de renderização para um conjunto de MIME types. O módulo:

- Define a **interface base** (`AbstractViewer`) e API (`iped-viewers-api`).
- Implementa **viewers concretos** (`iped-viewers-impl`) em Swing + JavaFX (`JFXPanel` para HTML/áudio).
- Oferece o **`MultiViewer`** — um Composite/CardLayout que escolhe automaticamente o viewer apropriado a partir do MIME type.
- Integra com a busca textual (highlight de termos) e com `iped-parsers` (`AttachmentSearcher` para anexos referenciados em chats).

Empacotamento: dois sub-módulos.
- [`iped-viewers-api/`](iped-viewers-api) — contratos (~10 classes públicas).
- [`iped-viewers-impl/`](iped-viewers-impl) — implementações (~20 viewers).

## 2. API — `AbstractViewer`

```java
public abstract class AbstractViewer {
    public abstract String getName();
    public abstract boolean isSupportedType(String contentType);
    public abstract void init();                                      // fora EDT
    public abstract void dispose();                                   // EDT
    public abstract void loadFile(IStreamSource content, Set<String> highlightTerms);
    public abstract void scrollToNextHit(boolean forward);

    public int getHitsSupported();                                    // -1 não suporta, 0 controle externo, 1 interno
    public int getToolbarSupported();
    public boolean isSearchSupported();
    public void searchInViewer(String term, HitsUpdater hits);
    public void copyScreen();

    public JPanel getPanel();
    public Window getOwner();
}
```

**Ciclo de vida**:
1. Construtor (UI mínima).
2. `init()` em background — pode carregar libs pesadas (LibreOffice daemon, JAI).
3. `loadFile()` na EDT (chamador encapsula em `CancelableWorker`).
4. `scrollToNextHit()` na EDT.
5. `dispose()` na EDT — liberar recursos nativos.

Convenção: `loadFile(null, ...)` significa **limpar a visualização**.

Outros contratos importantes em `iped-viewers-api`:
- `CancelableWorker<T,V>` — extensão de `SwingWorker` com cancelamento.
- `GUIProvider` — fornece recursos GUI (janelas, locale, fontes).
- `IItemRef` — referência fraca a item da evidência.
- `IMiniaturizable` — viewer que gera thumbnails.
- `AttachmentSearcher` — resolve anexos mencionados em chats/e-mails.
- `ResultSetViewer` / `ResultSetViewerConfiguration` — abas dockable que processam o conjunto de resultados de busca (ex.: `MapViewer` em `iped-geo`).
- `iped.viewers.api.search.HitsUpdater` — callback para atualizar contador de hits.
- `iped.viewers.bookmarks.IBookmarksController` — controle de bookmarks pela UI.

## 3. Viewers concretos (`iped-viewers-impl`)

| Viewer | MIME types | Tecnologia | Observações |
|---|---|---|---|
| [`HexViewerPlus`](iped-viewers-impl/src/main/java/iped/viewers/HexViewerPlus.java) | qualquer (fallback) | DeltaHex 0.1.3-p3 (Swing) | Editor hex/ASCII; lazy loading para arquivos grandes. |
| [`ATextViewer`](iped-viewers-impl/src/main/java/iped/viewers/ATextViewer.java) | `text/*` | JTable monoespacial | Limite: 100.000 linhas / 100 chars por linha (`MAX_LINES`, `MAX_LINE_SIZE`). |
| [`HtmlViewer`](iped-viewers-impl/src/main/java/iped/viewers/HtmlViewer.java) | `text/html`, `application/xhtml+xml`, `text/asp` | JavaFX `WebView` em `JFXPanel` | Limite 10 MB. Highlight via JS injection. |
| `HtmlLinkViewer` | custom `application/x-preview-with-links` | extends `HtmlViewer` | Bridge JS-Java (`window.app`) para abrir anexos do caso via `AttachmentSearcher`. |
| `EmailViewer` | `message/rfc822`, `message/x-emlx` | mime4j + `HtmlLinkViewer` | Parsing MIME + integração com anexos. |
| `MsgViewer` | `application/vnd.ms-outlook` (`MediaTypes.OUTLOOK_MSG`) | POI HSMF + RTF→HTML | Converte RTF, extrai anexos. |
| `AudioViewer` | `audio/*` | JavaFX MediaView (HTML `<audio>`) | Mostra duração + transcrição (`ExtraProperties.TRANSCRIPT_ATTR`). |
| [`IcePDFViewer`](iped-viewers-impl/src/main/java/iped/viewers/IcePDFViewer.java) | `application/pdf` | IcePDF 7.0.0 | Suporta formulários/anotações; busca integrada (`DocumentSearchController`). |
| `PDFBoxViewer` | `application/pdf` (alternativa) | PDFBox 2.0.27 + Graphics2D | Mais leve. CMYK puro Java. |
| `ImageViewer` | `image/*` | ImageIO + TwelveMonkeys + JAI + `ExternalImageConverter` | Zoom, rotação, brilho, blur, grayscale, detecção de face (externo). |
| `TiffViewer` | `image/tiff` | TwelveMonkeys imageio-tiff | Navegação multi-página + orientação. |
| `LibreOfficeViewer` | DOCX/XLSX/PPT/ODF/RTF | LibreOffice UNO + NOA-Libre | Daemon LO out-of-process, busca via `XSearchable`, profile em `~/.iped/libreoffice6/profile`. |
| `MetadataViewer` | qualquer (abas) | JavaFX TabPane + 3× `HtmlViewer` | Basic / Advanced / Custom Properties. |
| `CADViewer` | DWG | Ferramenta externa CaffViewer | Apenas botão "Open External Viewer". |
| `ReferencedFileViewer` | MIME types customizados de parsers (`WhatsAppParser.WHATSAPP_ATTACHMENT`, `TelegramParser.TELEGRAM_ATTACHMENT`, etc.) | `AttachmentSearcher` + `MultiViewer` | Resolve referência → delega para viewer apropriado. |
| `MultiViewer` | composite | Swing `CardLayout` | **É o seletor que orquestra todos os outros**. |
| `NoJavaFXViewer` | mesmas do HtmlViewer | JLabel fallback | Quando JavaFX indisponível (headless, falta de display). |

## 4. Como o IPED escolhe um viewer

`MultiViewer.getSupportedViewer(contentType)` itera sobre `viewerList` e seleciona o **último** que retorna `isSupportedType(contentType, true) == true`. A ordem de registro define prioridade: viewers especializados primeiro, viewers genéricos (Hex, Metadata) por último (fallback).

Registro é feito **em código** no `iped-app` (não há SPI). Veja `iped/app/ui/App.java` e `iped/app/ui/ViewerController.java`.

Fluxo de carregamento:
```
User seleciona item → FileProcessor obtém MIME type
   → MultiViewer.loadFile(content, contentType, terms)
   → CardLayout switcha para o viewer escolhido
   → viewer.loadFile(content, terms) [via CancelableWorker]
```

## 5. Threading

- **EDT do Swing**: toda manipulação de componente Swing deve estar lá.
- `init()`: **fora** da EDT. `loadFile`, `dispose`, `scrollToNextHit`: **na** EDT.
- O chamador (`iped-app`) encapsula `loadFile` em `CancelableWorker`:
  ```java
  new CancelableWorker<Void,Void>() {
      protected Void doInBackground() { /* I/O pesado */ return null; }
      protected void done() { viewer.loadFile(content, terms); }
  }.execute();
  ```
- **JavaFX** tem sua própria EDT. Tudo dentro de `JFXPanel` deve usar `Platform.runLater(...)`. Defina `Platform.setImplicitExit(false)` para não fechar a JVM ao destruir o último `Stage`.
- **LibreOfficeViewer** sincroniza com seu daemon UNO via `synchronized(lock)`.
- Use `HitsUpdater` para atualizar contador de hits sempre na EDT.

## 6. Bridge JavaScript ↔ Java (HtmlLinkViewer)

Após carregar o HTML no `WebEngine`, o viewer injeta um objeto Java acessível em `window.app`:
```java
JSObject window = (JSObject) webEngine.executeScript("window");
window.setMember("app", new JsFileHandler());
```
HTML usa:
```html
<a href="javascript:app.openFile(hash)">Anexo</a>
```
Esse padrão é base do `EmailViewer`, `MsgViewer` e `AudioViewer` (player HTML5 `<audio>`).

## 7. Dependências principais

```xml
<!-- API -->
<dependency> <groupId>org.dockingframes</groupId>  <artifactId>docking-frames-common</artifactId> <version>1.1.2</version> </dependency>
<dependency> <groupId>org.roaringbitmap</groupId>  <artifactId>RoaringBitmap</artifactId>         <version>0.9.39</version></dependency>

<!-- Impl -->
<dependency> <groupId>org.exbin.deltahex</groupId>          <artifactId>deltahex-swing</artifactId> <version>0.1.3-p3</version></dependency>
<dependency> <groupId>com.github.pcorless.icepdf</groupId>  <artifactId>icepdf-viewer</artifactId>   <version>7.0.0</version>   </dependency>
<dependency> <groupId>com.twelvemonkeys.imageio</groupId>   <artifactId>imageio-{psd,webp,tiff,bmp}</artifactId> <version>3.10.1</version></dependency>
<dependency> <groupId>org.libreoffice</groupId>             <artifactId>libreoffice</artifactId>     <version>7.2.2</version>   </dependency>
<dependency> <groupId>org.libreoffice</groupId>             <artifactId>officebean</artifactId>      <version>7.2.2</version>   </dependency>
<dependency> <groupId>libre-office</groupId>                <artifactId>noa-libre</artifactId>       <version>3.0-20190212</version></dependency>
<dependency> <groupId>com.github.bbottema</groupId>         <artifactId>rtf-to-html</artifactId>     <version>1.0.1</version>   </dependency>
<dependency> <groupId>org.apache.lucene</groupId>           <artifactId>lucene-highlighter</artifactId> <version>9.2.0</version></dependency>
```

## 8. Dependências nativas

- **LibreOffice**: precisa instalado **ou** o zip distribuído em `tools/libreoffice.zip`. `LibreOfficeFinder` detecta. Profile customizado em `$SYSUSERCONFIG/.iped/libreoffice6/profile`.
- **JavaFX**: necessário para `HtmlViewer`, `AudioViewer`, `MetadataViewer`. Em ambientes headless ou sem display, `NoJavaFXViewer` substitui.
- **CaffViewer** (CAD): externo, em `tools/caffviewer/caffviewer.jar`.

## 9. Como criar um novo viewer

```java
package iped.viewers;

import iped.io.IStreamSource;
import iped.viewers.api.AbstractViewer;
import java.util.Set;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class MyCustomViewer extends AbstractViewer {

    public MyCustomViewer() {
        super(new BorderLayout());
    }

    @Override public String getName() { return "MyViewer"; }
    @Override public boolean isSupportedType(String contentType) {
        return contentType.equals("application/x-myformat");
    }
    @Override public void init() { /* libs pesadas */ }
    @Override public void dispose() { /* limpeza */ }

    @Override public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        if (content == null) { getPanel().removeAll(); return; }
        // ... renderizar
    }
    @Override public void scrollToNextHit(boolean forward) { /* opcional */ }
}
```

Registro no `iped-app`:
```java
multiViewer.addViewer(new MyCustomViewer());
```

Ícones em `iped-viewers-impl/src/main/resources/iped/viewers/res/`. Path constante: `protected static final String resPath = "/iped/viewers/res/";`.

## 10. Convenções

- Localização: `iped.viewers.localization.Messages.getString("MyViewer.Label")` (bundle em `localization/`).
- Logging: SLF4J. Sem `System.out`.
- Charset: sempre explícito.
- Não armazenar referência a `IStreamSource` além do escopo do `loadFile`; chamar `getTempFile()` apenas se necessário.
- Sempre suporte chamada `loadFile(null, ...)` para limpar a UI.

## 11. Limitações conhecidas

- `HtmlViewer`: requer display, máx 10 MB, JS sandbox restritivo.
- `LibreOfficeViewer`: memory leak; restart periódico. Hangs em arquivos enormes. DDE no Windows pode interferir.
- `IcePDFViewer`: pode crashar com PDFs malformados. Sem suporte a formulários interativos.
- `ImageViewer`: dimensão máxima 2400×2400; blur até 512×512.
- Múltiplos viewers pesados podem bloquear UI.

## 12. Checklist de PR

- [ ] `init`/`dispose`/`loadFile` respeitam o ciclo de vida (EDT).
- [ ] `isSupportedType(null)` não quebra.
- [ ] `loadFile(null, ...)` limpa a UI.
- [ ] Recursos liberados em `dispose()` (incluindo threads, temp files, daemons).
- [ ] Locale aplicado via `Messages`.
- [ ] Sem dependência implícita de JavaFX se não for necessário (ou fallback definido).
- [ ] Testou abrir item, navegar entre itens, fechar a aba do dockable, reabrir.
