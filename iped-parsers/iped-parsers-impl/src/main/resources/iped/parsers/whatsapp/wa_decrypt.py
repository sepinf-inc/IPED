'''
Thin glue between IPED's WhatsAppCryptoParser (java) and wa-crypt-tools
(https://github.com/ElDavoo/wa-crypt-tools).

This module is loaded through JEP by iped.parsers.whatsapp.WhatsAppCryptoParser,
which extracts it from the classpath to a temporary folder before importing it.
It never spawns wa-crypt-tools command line tools, it calls its python API directly.

The backup is decrypted in chunks pushed by java (begin, update..., finish), so
neither the encrypted nor the decrypted content is ever fully loaded in memory and
the encrypted backup does not need to be a file. wa-crypt-tools is used to load the
key and to parse the backup header, the decryption loop is done here because its
API only decrypts a backup fully loaded in memory.

wa-crypt-tools is a python package that must be installed in the python used by
IPED, with 'pip install wa-crypt-tools==0.1.0'. See
https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
'''

import io
import logging
import threading
import zlib

# the encrypted data is followed by the AES-GCM authentication tag (16 bytes), the
# MD5 of everything before it (16 bytes) and, in crypt12 only, a footer with the end
# of the phone number (4 bytes). Java never sends the last _TRAILER_SIZE bytes of the
# backup to update(), they are given to finish().
_TAG_SIZE = 16
_CHECKSUM_SIZE = 16
_CRYPT12_FOOTER_SIZE = 4
_TRAILER_SIZE = _TAG_SIZE + _CHECKSUM_SIZE + _CRYPT12_FOOTER_SIZE

_initialized = False

# Each java thread decrypts one backup at a time, but the interpreters of all
# threads share the modules, so the decryption in progress is kept per thread.
_local = threading.local()


def init():
    '''
    Checks that wa-crypt-tools can be loaded. Must be called once per interpreter
    before any other function of this module.

    Raises:
        ImportError: if wa-crypt-tools or one of its dependencies is not installed
    '''
    global _initialized
    if _initialized:
        return

    # fail fast (and with a clear message) if it or a dependency is missing
    import wa_crypt_tools.lib.key.keyfactory
    import wa_crypt_tools.lib.db.dbfactory

    # wa-crypt-tools is quite verbose and writes to stderr by default,
    # errors are collected by _LogCollector below and reported to java instead
    logging.getLogger('wa_crypt_tools').setLevel(logging.ERROR)

    _initialized = True


def get_version():
    '''Returns the version of the installed wa-crypt-tools package, or None if unknown.'''
    try:
        from importlib.metadata import version
        return version('wa-crypt-tools')
    except Exception:
        return None


def get_trailer_size():
    '''Returns how many bytes of the end of the backup must be given to finish().'''
    return _TRAILER_SIZE


class _LogCollector(logging.Handler):
    '''
    Collects the error messages logged by wa-crypt-tools in the current thread, so
    they can be reported back to java. The logger is shared by all threads.
    '''

    def __init__(self):
        logging.Handler.__init__(self, level=logging.ERROR)
        self.thread = threading.get_ident()
        self.messages = []

    def emit(self, record):
        try:
            if record.thread == self.thread:
                self.messages.append(record.getMessage().replace('\n', ' ').strip())
        except Exception:
            pass

    def __enter__(self):
        logging.getLogger('wa_crypt_tools').addHandler(self)
        return self

    def __exit__(self, *args):
        logging.getLogger('wa_crypt_tools').removeHandler(self)
        return False

    def last(self):
        return self.messages[-1] if self.messages else None


def _parse_header(head, size):
    '''
    Parses the header of an encrypted backup from its first bytes.

    Returns:
        the wa-crypt-tools database object, or None if the header could not be
        parsed, and the size of the header.
    '''
    from wa_crypt_tools.lib.db.dbfactory import DatabaseFactory
    # buffered because wa-crypt-tools peeks the stream
    stream = io.BufferedReader(io.BytesIO(bytes(memoryview(head)[:size])))
    db = DatabaseFactory.from_file(stream)
    return db, stream.tell()


def get_info(head, size):
    '''
    Returns a dict of strings with information read from the encrypted backup header,
    or an empty dict if the header could not be parsed. No key is needed for this.

    Parameters:
        head: buffer
            the first bytes of the encrypted backup
        size: int
            how many bytes of the buffer are valid
    '''
    info = {}
    with _LogCollector():
        try:
            db, _ = _parse_header(head, size)
            if db is None:
                return info
            # Database12 / Database14 / Database15
            info['format'] = type(db).__name__.replace('Database', 'crypt')
            props = getattr(getattr(db, 'props', None), 'props', None)
            if props is not None:
                if getattr(props, 'app_version', None):
                    info['appVersion'] = str(props.app_version)
        except Exception:
            pass
    return info


class _Decryption:
    '''State of the decryption in progress in a thread.'''

    def __init__(self, cipher, file_hash, has_footer):
        self.cipher = cipher
        # MD5 of the encrypted backup, already updated with its header
        self.file_hash = file_hash
        self.has_footer = has_footer
        self.z_obj = zlib.decompressobj()
        # None while it is not known yet
        self.compressed = None
        # decrypted data waiting for enough bytes to tell if it is compressed
        self.pending = b''

    def decrypt(self, encrypted):
        self.file_hash.update(encrypted)
        return self.decompress(self.cipher.decrypt(encrypted))

    def decompress(self, data):
        if self.compressed is None:
            self.pending += data
            if len(self.pending) < 2:
                # the zlib header has 2 bytes
                return b''
            data, self.pending = self.pending, b''
            try:
                data = self.z_obj.decompress(data)
                self.compressed = True
            except zlib.error:
                # not compressed: multi file backups and non database files
                # (stickers, backup_settings.json...) are stored as is
                self.compressed = False
            return data
        if self.compressed:
            try:
                return self.z_obj.decompress(data)
            except zlib.error as e:
                raise ValueError('The decrypted data is corrupted: {}'.format(e))
        return data


def begin(key_path, head, size):
    '''
    Starts the decryption of a WhatsApp crypt12/crypt14/crypt15 backup in the current
    thread, discarding any previous one not finished.

    Parameters:
        key_path: str
            path to the WhatsApp key file ('key' or 'encrypted_backup.key') or the 64 char hex key
        head: buffer
            the first bytes of the encrypted backup, enough to hold its header
        size: int
            how many bytes of the buffer are valid

    Returns:
        the size of the header: the bytes after it are the first ones to be given to update()

    Raises:
        ValueError: if the key could not be loaded or the header could not be parsed
    '''
    from wa_crypt_tools.lib.key.keyfactory import KeyFactory
    from wa_crypt_tools.lib.db.db12 import Database12
    # same import done by wa-crypt-tools, to be sure it is the same implementation
    from wa_crypt_tools.lib.db.db14 import AES

    _local.decryption = None
    with _LogCollector() as log:
        try:
            key = KeyFactory.new(key_path)
        except Exception as e:
            raise ValueError('Could not load the key: {}'.format(e))
        if key is None:
            raise ValueError(log.last() or 'Could not load the key')

        try:
            db, header_size = _parse_header(head, size)
        except Exception as e:
            raise ValueError('Could not parse the encrypted backup header: {}'.format(log.last() or e))
        if db is None:
            raise ValueError(log.last() or 'Unsupported or corrupted encrypted backup')

        try:
            cipher = AES.new(key.get(), AES.MODE_GCM, db.get_iv())
        except Exception as e:
            raise ValueError('Could not create the cipher: {}'.format(e))

    _local.decryption = _Decryption(cipher, db.file_hash, isinstance(db, Database12))
    return header_size


def update(encrypted, size):
    '''
    Decrypts the next chunk of the backup started by begin(). The last
    get_trailer_size() bytes of the backup must not be given to this function.

    Parameters:
        encrypted: buffer
            the next bytes of the encrypted backup
        size: int
            how many bytes of the buffer are valid

    Returns:
        the decrypted (and decompressed) bytes, possibly none

    Raises:
        ValueError: if the decrypted data is corrupted
    '''
    return _local.decryption.decrypt(memoryview(encrypted)[:size])


def finish(trailer, size):
    '''
    Finishes the decryption started by begin(), checking the authentication tag of
    the backup, which is what tells a wrong key from a good one. The content itself
    is not checked, so any backed up file is accepted, not only databases.

    Parameters:
        trailer: buffer
            the last get_trailer_size() bytes of the encrypted backup
        size: int
            how many bytes of the buffer are valid

    Returns:
        a list with the last decrypted bytes, possibly none, and a warning message
        or None

    Raises:
        ValueError: if the key does not match or the backup is corrupted
    '''
    decryption = _local.decryption
    _local.decryption = None

    trailer = bytes(memoryview(trailer)[:size])
    if decryption.has_footer:
        trailer = trailer[:-_CRYPT12_FOOTER_SIZE]
    if len(trailer) < _TAG_SIZE + _CHECKSUM_SIZE:
        raise ValueError('The encrypted backup is too small')

    # crypt14/crypt15 have no footer, so the trailer starts with encrypted data
    encrypted = trailer[:-(_TAG_SIZE + _CHECKSUM_SIZE)]
    tag = trailer[-(_TAG_SIZE + _CHECKSUM_SIZE):-_CHECKSUM_SIZE]
    checksum = trailer[-_CHECKSUM_SIZE:]

    decrypted = decryption.decrypt(encrypted) if encrypted else b''

    file_hash = decryption.file_hash.copy()
    file_hash.update(tag)
    try:
        if file_hash.digest() == checksum:
            decryption.cipher.verify(tag)
        else:
            # Multi file backups have no checksum: what would be the tag are the last
            # bytes of the encrypted data and what would be the checksum is the tag
            decrypted += decryption.decrypt(tag)
            decryption.cipher.verify(checksum)
    except ValueError as e:
        raise ValueError('Authentication tag mismatch: {}. The key does not match or the backup is corrupted'.format(e))

    # too small to tell if it was compressed, so it was not
    decrypted += decryption.pending

    warning = None
    if decryption.compressed and not decryption.z_obj.eof:
        # but the recovered prefix may still be useful
        warning = 'The encrypted backup is truncated (damaged)'

    return [decrypted, warning]
