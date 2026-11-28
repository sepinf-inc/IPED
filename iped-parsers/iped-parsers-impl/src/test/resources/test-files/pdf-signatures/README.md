# Signed PDF test corpus

Synthetic signed PDFs used by the PDF signature extraction tests. Every key,
certificate and document was generated for the corpus; no real person or
document is involved. No private keys are included.

- Source: https://github.com/robertolocatelli81-dev/pdf-signature-test-corpus
- Tag `v1.0.0`, commit `4493fbc8f985ed82ad52984ec4b33c7b6f5d1e1d`
- License: CC0-1.0 (see `LICENSE`)
- Offered for IPED tests in https://github.com/sepinf-inc/IPED/issues/3016

Contents copied unchanged from the tag:

- `pdf/`: the 17 test cases
- `certs/`: the synthetic signer, root and TSA certificates
- `EXPECTED.json`: expected values per case and per signature, measured by
  pyHanko and by PDFBox + BouncyCastle 1.70, with each field marked as agreed
  or as a disagreement between the validators
- `SHA256SUMS`: checksums of the files above, taken from the corpus

To check the copy: `sha256sum -c SHA256SUMS` in this directory.
