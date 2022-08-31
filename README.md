# IPED Digital Forensic Tool

IPED is an open source software that can be used to process and analyze digital evidence, often seized at crime scenes by law enforcement or in a corporate investigation by private examiners.

## Introduction

IPED - Digital Evidence Processor and Indexer (translated from Portuguese) is a tool implemented in java and originally and still developed by digital forensic experts from Brazilian Federal Police since 2012. Although it was always open source, only in 2019 its code was officially published.

Since the beginning, the goal of the tool was efficient data processing and stability. Some key characteristics of the tool are:

- Command line data processing for batch case creation
- Multiplatform support, tested on Windows and Linux systems
- Portable cases without installation, you can run them from removable drives
- Integrated and intuitive analysis interface
- High multithread performance and support for large cases: up to 400GB/h processing speed using modern hardware and 135 million items in a (multi) case as of 12/12/2019

Currently IPED uses the [Sleuthkit Library](https://github.com/sleuthkit/sleuthkit) only to decode disk images and file systems, so the same image formats are supported: RAW/DD, E01, ISO9660, AFF, VHD, VMDK. There is also support for EX01, VHDX, UDF(ISO), AD1 (AccessData) and UFDR (Cellebrite) formats.

If you are new to the tool, please refer to the [Beginner's Start Guide](https://github.com/lfcnassif/IPED/wiki/Beginner's-Start-Guide).

## Building

To build from source, you need git, maven and Java JDK 11 + JavaFX (e.g. Liberica OpenJDK 11 Full JDK) installed. Set JAVA_HOME environment var to your java 11 installation folder, then run:
```
git clone https://github.com/sepinf-inc/IPED.git
cd IPED
mvn clean install
```
It will generate an snapshot version of IPED in target/release folder.

<b>Attention:</b> the default master branch is the development one and is unstable. If you want to build a stable version, checkout some of the release tags after the clone step.

On Linux you also must build The Sleuthkit and additional dependencies. Please refer to [Linux Section](https://github.com/sepinf-inc/IPED/wiki/Linux)

If you want to contribute to the project, refer to [Contributing](https://github.com/lfcnassif/IPED/wiki/Contributing)

## Features

Some of IPED several features are listed below:

- Supported hashes: md5, sha-1, sha-256, sha-512 and edonkey. PhotoDNA is also available **for law enforcement** (please contact sepinf dot inc dot ditec at pf dot gov dot br)
- Supported hash sets: NIST NSRL, NIST CAID, ProjectVIC, Interpol ICSE, standard CSV format
- Fast hash deduplication 
- Signature analysis
- Categorization by file type and properties
- Recursive container expansion of dozens of file formats
- Embedded forensic/virtual disks expansion: supports splitted or single segment DD, E01, EX01, VHD, VHDX, VMDK (differential VMDKs are also supported) 
- Image and video gallery for hundreds of formats
- Georeferencing of GPS data, using Google Maps, Bing or OpenStreetMaps
- Regex searches with optional script validation for credit cards, emails, urls, ip & mac addresses, money values, bitcoin, ethereum, monero, ripple wallets and more...
- Embedded hex, unicode text, metadata and native viewers
- File content and metadata indexing and fast searching, including unknown files and unallocated space
- Efficient data carving engine (takes < 10% processing time) that scans much more than unallocated, with support for +40 file formats, including videos, extensible by scripting
- Optical Character Recognition powered by tesseract 5
- Encryption detection for known formats and using entropy test
- Processing profiles: forensic, pedo (csam), triage, fastmode (preview) and blind (for automatic data extraction)
- Detection for +70 languages
- Named Entity Recognition (needs Stanford CoreNLP models to be downloaded)
- Customizable filters based on any file metadata
- Similar document search with configurable threshold
- Similar image search, using internal or external image
- Similar face recognition, optimized to run without GPU, with configurable threshold
- Unified table timeline view and event filtering for timeline analysis
- Powerful file grouping (clustering) based on ANY metadata
- Support for multicases up to 135 million items
- Extensible with javascript and python (including cpython extensions) scripts
- External command line tools integration for file decoding
- Browser history for IE, Edge, Firefox, Chrome and Safari
- Custom parsers for Emule, Shareaza, Ares, WhatsApp, Skype, Telegram, Bittorrent, ActivitiesCache, and more...
- Fast nudity detection for images and videos using random forests algorithm (thanks to its author @tc-wleite)
- Nudity detection using Yahoo open-nsfw deeplearning model (needs keras and tensorflow)
- Audio Transcription, local and remote implementations with Azure and Google Cloud services
- Graph analysis for communications (calls, emails, instant messages...)
- Stable processing with out-of-process file system decoding and file parsing
- Resuming or restarting stopped or aborted processing (--continue/--restart options)
- Web API for searching remote cases, get file metadata, raw content, decoded text, thumbnails and posting bookmarks
- Creation of bookmarks/tags for interesting data
- HTML, CSV reports and portable cases with tagged data
