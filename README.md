# IPED Digital Forensic Tool

IPED is an open source software that can be used to process and analyse digital evidence, often seized at crime scenes by law enforcement or in a corporate investigation by private examiners.

## INTRODUCTION

IPED - Digital Evidence Processor and Indexer (translated from portuguese) is a tool implemented in java and originally and still developed by digital forensic experts from Brazilian Federal Police since 2012. Although it was always open source, only in 2019 its code was officialy published.

Since the beginning, the goal of the tool was efficient data processing and stability. Some key characteristics of the tool are:

- Command line data processing for batch case creation
- Multiplatform support, tested on Windows and Linux systems
- Portable cases without installation, you can run them from removable drives
- Integrated and intuitive analysis interface
- High multithread performance and support for large cases: up to 135 millions of items as of 12/12/2019

Currently IPED uses the [Sleuthkit Library](https://github.com/sleuthkit/sleuthkit) only to decode disk images and file systems, so the same image formats are supported: RAW/DD, E01, ISO9660, AFF, VHD, VMDK. Also there is support for UDF(ISO), AD1 (AccessData) and UFDR (Cellebrite) formats. Recently support for APFS was added, thanks to BlackBag implementation for Sleuthkit.

If you are new to the tool, please refer to the [Beginner's Start Guide](https://github.com/lfcnassif/IPED/wiki/Beginner's-Start-Guide).

## Features

Some of IPED several features are listed below:

- Supported hashes: md5, sha-1, sha-256, sha-512, edonkey and photoDNA (only for law enforcement, please contact iped@dpf.gov.br)
- Fast hash deduplication and NIST NSRL hash lookup
- Signature analysis
- Categorization by file type and properties
- Recursive container expansion of dozens of file formats
- Image and video gallery for hundreds of formats
- Georeferencing of GPS data (needs Google Maps Javascript API key)
- Regex searches with optional script validation for credit cards, emails, urls, money values, bitcoin wallets...
- Embedded hex, unicode text, metadata and native viewers
- File content and metadata indexing and fast searching, including unknown files and unallocated space
- Efficient data carving engine (takes < 10% processing time) that scans much more than unallocated, with support for +40 file formats, including videos, extensible by scripting
- Optical Character Recognition powered by tesseract 4
- Encryption detection for known formats and using entropy test
- Processing profiles: forensic, pedo (csam), triage, fastmode (preview) and blind (for automatic data extraction)
- Detection for +70 languages
- Named Entity Recognition (needs Stanford CoreNLP models to be downloaded)
- Customizable filters based on any file metadata
- Similar document search with configurable threshold
- Powerful file grouping (clustering) based on ANY metadata
- Support for multicases up to 135 millions of items
- Extensible with javascript and python (including cpython extensions) scripts
- External command line tools integration for file decoding
- Browser history for Edge, Firefox, Chrome and Safari
- Custom parsers for LNK, Emule, Shareaza, Ares, WhatsApp, Skype artifacts.
- Fast nudity detection using random forests algorithm (thanks to its author Wladimir Leite)
- Nudity detection using Yahoo open-nsfw deeplearning model (needs keras and jep)
- Stable processing with out-of-process file system reading and file parsing
- Web API for searching remote cases, get file metadata, raw content, decoded text, thumbnails and posting bookmarks
- Creation of bookmarks/tags for interesting data
- HTML, CSV reports and portable cases with tagged data