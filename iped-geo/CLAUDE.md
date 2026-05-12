# Módulo `iped-geo`

> **Georreferenciamento e visualização cartográfica** das evidências. Aba "Map" da interface do IPED. Extrai coordenadas (EXIF, KML/GPX, metadados de apps), renderiza em mapa interativo (Leaflet/OpenStreetMap ou Google Maps) e mantém sincronização bidirecional com a tabela de resultados.

## 1. Propósito

- Extrair coordenadas de itens (`ExtraProperties.LOCATIONS = "lat;lon"`).
- Renderizar marcadores em mapa via JavaFX `WebView` (Leaflet por padrão).
- Suportar clustering, seleção retangular/circular, exportação para KML.
- Bridge bidirecional Java ↔ JavaScript: clique em marcador → seleção na tabela, e vice-versa.
- Parsear arquivos geoespaciais (KML, GPX) como itens da evidência via `GeofileParser`.

## 2. Estrutura de pacotes

```
iped/geo/
├── (raiz)
│   ├── AbstractMapCanvas.java          # API abstrata para qualquer canvas de mapa
│   ├── MapSelectionListener.java        # listener de seleção retangular/circular
│   ├── MarkerEventListener.java         # listener de clique/duplo-clique em marcador
│   └── MarkerCheckBoxListener.java      # listener de checkbox dos marcadores
├── impl/                                # Implementação Swing
│   ├── MapViewer.java                  # implementa ResultSetViewer (aba IPED)
│   ├── AppMapPanel.java                # JPanel host do canvas
│   ├── MapCanvasFactory.java
│   ├── AppMapSelectionListener.java
│   ├── AppMapMarkerEventListener.java
│   ├── AppMarkerCheckBoxListener.java
│   ├── JMapOptionsPane.java
│   └── MapPanelConfig.java              # Configurable
├── webkit/                              # Bridge JavaFX WebView
│   ├── AbstractWebkitMapCanvas.java
│   └── JSInterfaceFunctions.java        # contrato JS → Java
├── openstreet/                          # Implementação Leaflet
│   ├── MapCanvasOpenStreet.java
│   └── JSInterfaceFunctionsOpenStreet.java
├── kml/
│   ├── KMLResult.java                   # holder (string KML + metadata)
│   ├── GetResultsKMLWorker.java         # SwingWorker → exporta KML
│   └── PlaceMark.java
├── js/
│   └── GetResultsJSWorker.java          # SwingWorker → KML para visualização inline
├── parsers/                             # Parsing de arquivos geoespaciais
│   ├── GeofileParser.java               # Apache Tika parser (KML/GPX)
│   └── kmlstore/
│       ├── KMLParser.java               # JDOM
│       ├── KMLFeatureListFactory.java
│       ├── GPXFeatureListFactory.java
│       ├── FeatureListFactory.java
│       ├── FeatureListFactoryRegister.java
│       └── Folder.java
└── localization/
    └── Messages.java
```

Recursos em `src/main/resources/`:
- `iped/geo/openstreet/` — `main.html` (template Leaflet), `L.KML.js` (parser KML customizado), `leaflet.markercluster.js`, `leaflet.geometryutil.js`, `leaflet-arrowheads.js`, CSS.
- `iped/geo/googlemaps/` — alternativa: `main.html`, `geoxmlfull_v3.js`, `keydragzoom.js`, `extensions.js`.
- `iped/geo/toolbar.html` — barra de ferramentas embutida.
- `carvers/GEOCarver.js` + `carver-geo.xml` — carving de fragmentos geo.

## 3. API de canvas

### `AbstractMapCanvas` (extends `Canvas`)
Abstração que separa **lógica de mapa** da **tecnologia de renderização**. Métodos abstratos:
- `connect()` / `disconnect()` — ciclo de vida do WebView.
- `setText(String html)` — injeta HTML.
- `setKML(String kml)` — injeta KML para o parser JS.
- `update()`, `clearSelection()`.
- `selectCheckbox(String mid, boolean b)` — sincroniza checkbox JS.
- `sendSelection()` — sincroniza seleção Java → JS.

Listeners: `MapSelectionListener`, `MarkerEventListener`, `MarkerCheckBoxListener`. `onLoadRunnables` é a fila de callbacks executados após o load do WebEngine.

### `MapCanvasOpenStreet` (Leaflet)
Implementação principal:
- Carrega `main.html` da pasta `resources/iped/geo/openstreet/`.
- Substitui placeholders `{{L.KML}}`, `{{leafletgeometryutil}}`, `{{leafletarrowheads}}` com os scripts JS.
- Estabelece a ponte: `window.app = new JSInterfaceFunctionsOpenStreet(this)`.
- Suporta múltiplos provedores de tiles (OSM, Bing, Google mutant, TileServer local).
- Detecta tema light/dark do IPED.

### `JSInterfaceFunctionsOpenStreet`
Bridge JS → Java. Métodos chamados pelo JavaScript:
- `markerMouseClickedBF(mid, button, modifiers)`
- `markerMouseDblClickedBF(mid, button)`
- `checkMarkerBF(mid, checked)`
- `selectMarkerBF(JSObject markers)`
- `exportKmlBF()`

## 4. Integração com o IPED

`MapViewer` implementa `iped.viewers.api.ResultSetViewer`. Registrado como uma aba dockable. Sequência típica:

```
User executa busca → JTable atualiza
   → MapViewer.tableChanged()
   → AppMapPanel.updateMap()
   → GetResultsJSWorker  (SwingWorker, fora EDT)
        ├─ varre os documentos do índice Lucene
        ├─ lê ExtraProperties.LOCATIONS (formato "lat;lon" ou "lat;lon;alt")
        ├─ gera <Placemark> KML por item
        └─ mantém RoaringBitmap de itens com GPS
   → AppMapPanel.accept(KMLResult)
   → browserCanvas.setKML(kml)
   → Platform.runLater → webEngine.executeScript("loadKml(...)")
   → JS parsa, cria Markers/Polylines, renderiza
```

IDs dos marcadores: `marker_<sourceId>_<itemId>` (com `_<subitemId>` opcional).

Sincronização bidirecional:
- Tabela → Mapa: `MapViewer.valueChanged()` → `browserCanvas.sendSelection()` → JS destaca.
- Mapa → Tabela: clique no marker → `window.app.markerMouseClickedBF()` → `AppMapMarkerEventListener.onClicked()` → `JTable.setRowSelectionInterval()`.

## 5. Parsing de KML/GPX (`GeofileParser`)

`GeofileParser` é um `org.apache.tika.parser.AbstractParser`:
- Lê KML/GPX usando **GeoTools** (`gt-main`, `gt-opengis`, `gt-geojson`).
- Cria itens virtuais:
  - Pastas KML → item com `HASCHILD=true`.
  - Placemarks → subitens com `PARENT_VIRTUAL_ID`.
  - Tracks → itens marcados com `geo:isTrack=1`.
- Geometrias armazenadas como **GeoJSON** em `geo:featureString` para re-renderização.

## 6. Dependências principais

```xml
<dependency> <groupId>org.geotools</groupId>        <artifactId>gt-main</artifactId>    <version>19.2</version></dependency>
<dependency> <groupId>org.geotools</groupId>        <artifactId>gt-opengis</artifactId> <version>19.2</version></dependency>
<dependency> <groupId>org.geotools</groupId>        <artifactId>gt-geojson</artifactId> <version>19.2</version></dependency>
<dependency> <groupId>org.roaringbitmap</groupId>   <artifactId>RoaringBitmap</artifactId>     <version>0.9.45</version></dependency>
<!-- Internas -->
<dependency> <groupId>iped</groupId> <artifactId>iped-api</artifactId>           <version>${project.version}</version></dependency>
<dependency> <groupId>iped</groupId> <artifactId>iped-utils</artifactId>         <version>${project.version}</version></dependency>
<dependency> <groupId>iped</groupId> <artifactId>iped-engine</artifactId>        <version>${project.version}</version></dependency>
<dependency> <groupId>iped</groupId> <artifactId>iped-viewers-api</artifactId>   <version>${project.version}</version></dependency>
<dependency> <groupId>iped</groupId> <artifactId>iped-parsers-impl</artifactId>  <version>${project.version}</version></dependency>
```

Tecnologia de renderização: **JavaFX WebView** (já presente no JDK Full FX). Não usa Selenium nem Chromium.

## 7. Bridge Java ↔ JavaScript

**Java → JS**:
```java
webEngine.executeScript("loadKml(kmlString)");
webEngine.executeScript("map.setView(lat, lng, zoom)");
webEngine.executeScript("track.selectMarker('marker_1_123')");
```

**JS → Java**:
```javascript
window.app.markerMouseClickedBF("marker_1_123", 1, "ctrl");
window.app.checkMarkerBF("marker_1_123", true);
```

A ponte é estabelecida após `WebEngine` atingir `State.SUCCEEDED`. Threading: **toda manipulação do WebView precisa estar em `Platform.runLater(...)`**.

## 8. Configuração e formato de coordenadas

- `ExtraProperties.LOCATIONS` — string `"latitude;longitude"`, opcionalmente `"latitude;longitude;altitude"`. Múltiplas localizações = array.
- Validação: `-90 ≤ lat ≤ 90`, `-180 ≤ lon ≤ 180`.
- Conversão DMS ↔ decimal: `KMLResult.converteCoordFormat()`.
- API key Google Maps (se usar): configurada em `JMapOptionsPane` → `MapPanelConfig`.

## 9. Como adicionar um novo provedor de mapas

1. Crie classe `MapCanvasMapbox extends AbstractWebkitMapCanvas` (ou `AbstractMapCanvas`):
   ```java
   public class MapCanvasMapbox extends AbstractWebkitMapCanvas {
       @Override public void connect()       { /* init WebEngine */ }
       @Override public void setKML(String k){ webEngine.executeScript("loadKml(" + esc(k) + ")"); }
       // ... outros métodos
   }
   ```
2. Crie a bridge correspondente:
   ```java
   public class JSInterfaceFunctionsMapbox implements JSInterfaceFunctions { /* ... */ }
   ```
3. Adicione o template HTML em `src/main/resources/iped/geo/mapbox/main.html`.
4. Modifique `MapCanvasFactory` para escolher pelo URL/provedor:
   ```java
   if (provider.contains("mapbox")) return new MapCanvasMapbox();
   ```

## 10. Customização de marcadores

- **Via KML** (no `GetResultsKMLWorker`/`GetResultsJSWorker`): adicione `<Style><IconStyle><Icon><href>data:image/png;base64,...</href></Icon></IconStyle></Style>`.
- **Via Leaflet** (no template HTML):
  ```javascript
  var customIcon = L.icon({ iconUrl: 'data:image/png;base64,...', iconSize: [32, 32] });
  marker.setIcon(customIcon);
  ```
- **Popup personalizado**: use `<description><![CDATA[...HTML...]]></description>` no KML.

## 11. Performance e limitações

- ~10k marcadores funcionam sem clustering; com `leaflet.markercluster` o limite sobe.
- `RoaringBitmap` evita queries Lucene redundantes para itens sem GPS.
- Google Maps requer **API key + internet**; Leaflet com OSM funciona se tiles estiverem em cache (ou TileServer local).
- `JavaFX WebView` é baseado em WebKit; alguns recursos modernos podem não estar disponíveis.

## 12. Convenções

- Localização: `iped.geo.localization.Messages` (separado de `iped.viewers.localization.Messages`).
- IDs de marcadores: `marker_{sourceId}_{itemId}` (use `GetResultsKMLWorker.getBaseGID(...)` para parsing).
- Geração de KML: validar contra schema do Google Earth 2.2.
- Adicione campos extras via `<ExtendedData><Data name="...">...</Data></ExtendedData>`.

## 13. Debug

- `webEngine.executeScript("console.log(...)")` — logs do JS aparecem no `System.out`.
- Para testar KML manualmente: exportar via "Export KML" e abrir no Google Earth.
- Erros de JS aparecem como `Throwable` se você instalar um listener via `setOnError` no `WebEngine`.

## 14. Checklist de PR

- [ ] Sempre executar manipulação do WebView dentro de `Platform.runLater`.
- [ ] Strings injetadas em `executeScript` devem ser **escapadas** (aspas, quebras de linha).
- [ ] Novos handlers JS → Java vão em `JSInterfaceFunctions` (e na implementação concreta).
- [ ] KML gerado é válido (passou em parser KML de referência).
- [ ] Recursos HTML/JS/CSS adicionados em `resources/iped/geo/<provider>/`.
- [ ] Não bloquear EDT — workers (`CancelableWorker`) para parsing/geração.
