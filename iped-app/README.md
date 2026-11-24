# Analysis application

## Word reports

Highlight one or more rows in the results table and click **Word report** in the main toolbar. You can also open the context menu and choose **Send items to Word report...** (in Portuguese, **Enviar itens para relatório Word...**). This uses highlighted rows, independently of case bookmarks. Button names below refer to the English interface; the Portuguese interface uses localized labels.

### Document and evidence workflow

The document, evidence list, insertion position and preview stay together in a single resizable screen. The list contains only unsaved evidence, not an insertion history. Selecting a row shows that item's image, existing text/transcription and file information beside it; the video preview reuses a cached frame or thumbnail and generates a single frame when neither is available. Word pages appear only after **Review result**. Selecting another item or changing the report options returns to the evidence preview. Closing it retains pending evidence for the current application session; restarting IPED restores the document selection, not unsaved evidence or edits.

1. **Document:** choose a local `.docx`, an HTTPS download link or a saved template, then **Load document**. The document stays loaded when this screen is closed and reopened for the same case. After restarting IPED, that case's last local document or template is restored and loaded automatically. Local selections are stored in case-specific hashed files under `~/.iped/word-report`; shared links stay in memory only. A different case keeps its own document selection.
2. **Evidence:** highlight one or more results in IPED and use **Add IPED selection**, click **Word report** in the main toolbar, or reopen **Send items to Word report...** from the results context menu. The existing report window is reused for the same case, preserving its document and position. Non-chat items already listed are not duplicated. Chat excerpts are independent entries, allowing different crops from the same conversation. Check the items to include and choose text/transcription and one or two ordinary evidence frames per row. Chat and video layouts are handled separately. File information follows the reference report. Media types are identified automatically. This action uses existing images and transcriptions. Video frames are reused when a sequence of at least 20 exists; otherwise the original video is read through IPED's existing video generator in a private temporary folder.
3. **Position and preview:** choose the section and a readable insertion position, then **Review result**. Empty paragraphs, image-only blocks and table-of-contents entries are not offered as text positions. The preview shows the actual inserted images, metadata and selected text and attempts to locate the first inserted item by its text anchor. Page fitting, zoom and navigation remain available. Word fonts and field updates can affect pagination.
4. **Continue the report:** save the reviewed result, highlight more items in IPED and add them to the same screen. See the saving rules below.

### Saving and opening

| Action | Behavior |
| --- | --- |
| **Save report** with a local document | Updates that selected file with the exact prepared DOCX bytes, after checking for external changes. It does not create a new file for each batch. |
| First **Save report** from a template or shared link | Asks for a local filename, preserving the template or remote document. Later saves update the selected local report. |
| **Save as...** | Creates and selects a separate copy under a new filename. Existing destinations are refused. It also works without pending evidence, to copy the loaded document. |
| **Open report** | Opens the selected local file in its default application on the computer running IPED. It shows the saved version; pending changes must be saved first. |

After saving, exported items are removed from the pending list; unchecked unsaved items remain. The next insertion position follows that batch. To insert a saved item elsewhere, select it in IPED and add it again. A local document restored for the same case after restart continues saving in place.

Saving does not launch an editor or ask whether to open one. **Open report** is available for a loaded local report, and after the first save from a template or shared link.

Changing evidence, options or position disables saving pending evidence until the prepared result is refreshed. Loading, extraction, insertion, conversion and saving show a central activity panel. An indeterminate progress bar accompanies the stage description; evidence preparation also reports item counts. Controls are disabled while work is running. If page rendering fails after DOCX preparation, saving the prepared Word document and retrying the preview remain available.

Insertion positions refer to document body blocks, not fixed Word page numbers. The page preview helps review the result; the final editor's fonts and field updates can change pagination.

### Document excerpts

For a document, display the desired page in the preview (or the desired lines in the text viewer), then choose **Word report**. Keep the visible area or drag to crop it before continuing. The report includes the approved pixels and the original file's hash, name and path. To place another excerpt beside it, scroll to the next passage, repeat the capture and choose the side-by-side option.

### Chat excerpts

For chats, first open the desired excerpt in IPED's **Preview** panel and position it on screen. **Word report** captures only that visible HTML viewport; it opens **Choose the chat excerpt** before the report screen. Keep the entire visible excerpt or drag a smaller crop over the image. There is no generated message-selection list. The side panel lets the analyst include or omit each visible audio transcription independently. To enlarge an image sent in the chat, select its indexed file under **Image to enlarge beside the excerpt**. The choices follow the crop and show paths to distinguish copies with the same hash in the same evidence source. **Use this excerpt and continue** opens the document/insertion screen. Cancel leaves the report unchanged. Select one chat at a time; multiple approved excerpts of the same chat can be added to the same report. No action scrolls through or exports the complete chat. Cropping does not change case bookmarks.

When the previous pending or inserted chat excerpt has an available column, choose **Place beside previous excerpt** or **Keep as a separate excerpt**. Pairing uses two distinct images with each source's own metadata, and retains approved audio text below its corresponding image. When pairing a previously saved excerpt, its embedded image supplies the original aspect ratio. An already paired frame cannot receive a third crop.

After placing two plain conversation crops side by side, adding a third or fourth crop with the same nonempty source hash offers a two-by-two layout at the same insertion point. This also works after saving and reopening the document. A completed group starts on its own page. Each crop fits its column proportionally, using the original embedded PNG. Identical file information is shown once below the group. Different names or paths retain separate file-information cells. Crops with selected transcriptions or enlarged attachments keep their existing layouts. Use Review result to check pagination in the selected document.

When adding one ordinary image at a time, an available preceding image frame offers **Place beside previous image** or **Keep as a separate image**. This also works after saving and closing/reopening the report screen. Each column keeps its own image and file information; a complete pair cannot receive a third image. Choosing separate placement overrides the two-column default. Multi-image selections still use the selected one/two-frame layout. Image frames are paired only with other image frames; chat pairing keeps its own rules.

### Evidence layout

Image frames contain an image/text area, **Informações do arquivo** strip (DDD9C3) and a gray information row (EEEEEE). The fields are **Hash**, **Nome** and **Caminho**, in this order, using Courier New 9 pt with highlighted bold labels. These report labels remain in Portuguese regardless of the application locale. No Evidence heading or visible image-processing caption is added.

A chat crop without a selected attachment or audio transcription uses one full-width column, with no empty second column or repeated information. Chats with audio transcription follow the reference's two-column screenshot/transcription layout: the approved crop retains the displayed bubbles, sender labels, timestamps and existing visual context; **Transcrição do áudio** appears beside it when the approved crop contains transcription text. No additional audio hash is printed in the transcription column or crop-approval text; the selected chat's Hash remains in the file-information block below. Click a thumbnail in the crop dialog to choose the image to enlarge. When an attachment is selected, its image appears in the right column below any selected transcription. Both file-information cells show the selected chat's **Hash**, **Nome** and **Caminho**, including when the right column contains an enlarged attachment, an audio transcription, or both. A frame containing an enlarged attachment already occupies both columns.

The screenshot is not labeled as a new source evidence file; the source chat's hash is preserved. Cropping does not correct or infer a transcription, speaker, arrow or highlighted interpretation. Only visible transcription characters whose centers lie in the approved crop are copied; omitted messages and offscreen text cannot enter through an indexed-text fallback.

Other message items retain the numbered four-column table: an unlabeled number column, **Interlocutores**, **Conteúdo da mensagem** and **Informações do arquivo**.

For non-chat items, text is read from the transcription extra attribute when present, otherwise from nonempty transcription metadata, then message-body metadata, then the item text reader. No new OCR or transcription is performed. Mixed selections retain their order and automatically switch between these table forms.

The neutral starter document has a cover, table of contents, identification, methodology, evidence, analysis, conclusion and annex sections, with unfilled fields. It is generated from code; no user-supplied document or artwork is bundled. Only the selected local report is updated by Save report. Saved templates and case evidence are not modified.

### Templates and shared links

**Add saved template** validates and copies a local `.docx` to `~/.iped/word-report/templates`. Templates persist between sessions; an existing template is never overwritten. To remove one, delete its own copy from that folder. User-supplied templates remain local and must not be added to the source repository or program distribution. The generated starter document contains no images, personal properties, comments or custom XML.

HTTPS imports accept direct DOCX downloads up to 300 MiB, with timeouts and a maximum of five HTTPS redirects. Login pages and non-DOCX responses are rejected. For authenticated Word/SharePoint/OneDrive sharing links, use **Open link in browser**, sign in normally, download the `.docx`, and select **Local file**. This feature does not store credentials, reuse browser cookies, or upload changes to the shared document.

### Video, audio and other extracted content

The report includes only the three file-information fields from the reference; IPED IDs, dates, MIME types and technical metadata such as ICC curves are not appended. The existing hash is labeled **Hash**, without assuming an algorithm. Selected extracted text and existing transcription are included in full, without a character-limit control. Unavailable ordinary images are reported in the interface without inserting processing notices into the report. Video-generation failures can stop preparation and require a retry. Video files and audio binaries are not embedded.

Videos use the reference's three-column composition: six enlarged **Fragmentos do vídeo** on the left, a five-by-four contact sheet captioned **Vídeo separado em frames** on the right, and one full-width **Informações do arquivo** block below. Existing video transcription appears under **Áudio constante no vídeo**; standalone audio uses **Transcrição do áudio**.

Frame extraction reuses `VideoThumbsMaker` and `ImageUtil.getFrames`, with bounded process waits, keeping its output under the report's private working directory. Generating new frames requires access to the original video and the configured `mplayer` executable. New portable cases include `tools/mplayer` so the same fallback remains available in the generated analysis distribution. It does not update the evidence database or infer speakers, narrative, timestamps or analyst highlights. Small sequences are never padded by repeating frames in the report.

### Document handling and preview

Macros, signed packages, password-protected documents and legacy `.doc` files are unsupported. The exporter reads OOXML with external entities disabled and a 32 MiB limit for each parsed XML part. Existing package parts are copied unchanged except `word/document.xml`, its relationships, content types and the field-update setting, plus newly embedded images. It does not round-trip the source report through LibreOffice: LibreOffice renders a temporary output copy only.

Closing the report screen hides it, retaining its document, position and pending crops for the current case. Temporary report files and previews are removed when another case replaces the screen or IPED exits normally. Abrupt termination can leave temporary files behind. No preview service or evidence upload is used.

**Preview requirements and performance:** Page preview requires a usable LibreOffice installation. The report dialog keeps a private Office worker ready after the document is loaded, reusing IPED's existing NOA/LibreOffice integration. Each prepared DOCX is opened hidden and read-only, with macros disabled, exported to a private PDF with lossless image compression and no reduction of image resolution, then closed without saving back. This changes only the temporary preview PDF; the reviewed DOCX and its embedded source images remain untouched. The worker has its own Office profile and communicates with IPED through anonymous process pipes; no server or network endpoint is configured by the report feature. It is closed with the report session when the case changes or IPED exits. If this integration is unavailable, bounded command-line conversion is attempted using LibreOffice's PDF defaults. The explicit lossless-image settings apply to the reusable worker path.

Repeating **Review result** without changing the selection, location, layout or transcription option reuses the exact prepared document after checking the source hash. Changing any of these invalidates it. PDF text is read only up to the matching insertion anchor, and the last three rendered page images are cached for navigation. The saved Word bytes and the 90 dpi preview are unchanged by these caches.

Updates are staged beside the selected report and replace it atomically where supported. Source fingerprints are checked before writing and again after staging; an externally changed source is refused. If atomic replacement is unavailable, the writer falls back to ordinary replacement. A file locked by the document editor may need to be closed before retrying. No automatic backup history is created.

Implementation follows the application's Java 11, Swing dialog/worker and external localization conventions. `WordReportDialog` coordinates selection and background work; `WordReportEvidence` reads existing IPED items; `WordReportDocument` handles insertion and package preservation; `WordReportFiles` handles templates and HTTPS imports; `WordReportPreview` handles optional pagination and page caches; `WordReportOffice` owns the reusable conversion worker. `HtmlViewerCapture` reads one JavaFX WebView snapshot and visible text positions without changing the DOM; `WordReportChatSelection` limits pixels/transcriptions to the selection, and `WordReportChatCropDialog` handles the approval step. The feature reuses existing Java libraries; page preview and on-demand video frames still require their respective external tools. No preview server or evidence-upload service is configured. `WordReportVideo` handles private video-frame preparation; `WordReportChat` provides existing-HTML parsing helpers rather than replacing viewport captures with reconstructed conversations.

### Remote Desktop and platform setup

File paths and the default DOCX editor belong to the computer running IPED. In a remote session, **Local file** browses the remote computer and redirected folders; **Open report** launches that computer's default application. It does not launch Word on the client computer.

To read or save client-side reports, redirect a dedicated writable report folder and select its redirected path in IPED. Keep the connection active during file access. Clipboard file transfer is not implemented by the report feature.

Word export uses already processed items and does not itself require reprocessing. Opening an older processed case with another IPED version may still upgrade its database; use a working copy when changing versions.

The `macos-aarch64` Maven profile activates on macOS/aarch64, adding FFmpeg/JavaCPP native classifiers and selecting `zstd-jni` 1.5.7-28. Other platforms retain the existing default version. `-DskipSleuthkitDlls=true` skips the Windows DLL unpack step; its default is `false`. The runtime option `-Diped.skipLibreOfficeDetection=true` skips automatic Office detection; it does not install Office. These options do not establish complete macOS forensic-format support.

### Verification

From the repository root, using the configured Java 11/JavaFX and Maven environment:

```sh
mvn -pl iped-app -am \
    -Dtest=WordReportDocumentTest,WordReportChatSelectionTest,WordReportPreviewTest,WordReportDialogTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
```

On macOS, add `-DskipSleuthkitDlls=true` when the Windows DLL download is unavailable. This override does not provide Windows native libraries. Inspect `iped-app/target/surefire-reports` as well: this project's application module is configured to ignore test failures by default. The tests use synthetic evidence only and cover saving the reviewed bytes, restoring document selections without persisting sharing links, locating repeated exports, insertion after tables, existing-part preservation, embedded-image relationships, repeated export, Unicode text, input changes, repeated saves to the same file, Save as continuation without changing the earlier file, failed-write preservation, overwrite protection for copies, external-entity rejection, macros and URL validation. Headless dialog tests cover pending-row removal, saved pairing anchors, available columns and evidence/report preview routing. Crop tests also cover untouched viewport pixels, lower-level message-selection/removal logic, source order and transcription exclusion outside the crop. This lower-level coverage does not mean the current crop interface has message checkboxes. They do not open an investigation database.

For manual verification, use a disposable processed case: select several items and confirm row clicks show the individual evidence without rendering Word pages. Click Review result to show the report, save and confirm saved rows disappear while unchecked pending rows remain. Save one image, close/reopen and add another; exercise both side-by-side and separate placement, including a two-column layout. Then open a chat in Preview, scroll, click Word report, verify only that viewport is offered without a message list, crop an audio excerpt and confirm that its screenshot and only its visible transcript appear side by side with the original chat metadata. Cancel/reopen, add a second crop from the same chat and choose separate placement and verify both independent entries. Repeat choosing side-by-side placement, including a previously saved single chat, and verify one distinct image and its own Hash, Nome and Caminho per column. Close/reopen the screen and confirm that the same case's document remains loaded without using Load document. Also highlight an image, audio, video or text item; load a document, choose a position, preview and use Save report; add another selection in the same screen and verify both batches are saved in the same local file without a filename dialog. Use Save as, check that it selects the new file and that later saves leave the prior file unchanged. Load the built-in template and verify only the first save asks for a filename, saves show no opening prompt, and Open report is disabled until that first save. Use Open report to open the result in the default DOCX application on the machine running IPED and confirm the chosen insertion point, image, metadata and table of contents. Update the table of contents in Word after editing the report. Validate authenticated sharing links in the institution's browser and review the real Windows layout separately from local fixture tests.

Fixture tests, a successful build, installation and live functional checks are separate validation stages. None alone establishes completion of the others.

### CPU-only Whisper processing tests

Whisper transcription runs during evidence processing, before report export. On a virtual host that exposes many virtual sockets, the optional JVM property `-Diped.whisper.cpuProcesses=2` limits CPU model instances and divides the JVM's available processor threads among them. The value must be between one and the available processor count. Without this property, the existing automatic CPU selection remains in effect. GPU device selection, model size and decoding parameters are unchanged. This setting controls processing resources; it does not create or alter transcriptions while composing a Word report.
