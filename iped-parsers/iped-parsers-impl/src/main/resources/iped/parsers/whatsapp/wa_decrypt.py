'''
Thin glue between IPED's WhatsAppCryptoParser (java) and wa-crypt-tools
(https://github.com/ElDavoo/wa-crypt-tools).

This module is loaded through JEP by iped.parsers.whatsapp.WhatsAppCryptoParser,
which extracts it from the classpath to a temporary folder before importing it.
It never spawns wa-crypt-tools command line tools, it calls its python API directly.

wa-crypt-tools requires third party modules to be installed, they are listed in
the requirements.txt shipped with it in the tools folder. See
https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
'''

import logging
import os
import sys
import zlib

# wa-crypt-tools logs this (it does not raise) when the AES-GCM authentication tag
# does not verify, which is the reliable way of telling that the key does not match
_KEY_MISMATCH = 'Authentication tag mismatch'

_initialized = False


def init(wa_crypt_tools_folder):
    '''
    Adds the wa-crypt-tools sources to sys.path. Must be called once per interpreter
    before any other function of this module.

    Parameters:
        wa_crypt_tools_folder: str
            folder where the wa-crypt-tools release was unpacked (it must contain a 'src' subfolder)

    Raises:
        ImportError: if wa-crypt-tools or one of its dependencies is not available
    '''
    global _initialized
    if _initialized:
        return
    src_folder = os.path.join(wa_crypt_tools_folder, 'src')
    if not os.path.isdir(src_folder):
        raise ImportError('wa-crypt-tools sources not found in ' + str(src_folder))
    if src_folder not in sys.path:
        sys.path.append(src_folder)

    # fail fast (and with a clear message) if a dependency is missing
    import wa_crypt_tools.lib.key.keyfactory
    import wa_crypt_tools.lib.db.dbfactory

    # wa-crypt-tools is quite verbose and writes to stderr by default,
    # errors are collected by _LogCollector below and returned to java instead
    logging.getLogger('wa_crypt_tools').setLevel(logging.ERROR)

    _initialized = True


class _LogCollector(logging.Handler):
    '''Collects error messages logged by wa-crypt-tools so they can be reported back to java.'''

    def __init__(self):
        logging.Handler.__init__(self, level=logging.ERROR)
        self.messages = []

    def emit(self, record):
        try:
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

    def find(self, text):
        '''Returns the first collected message containing the given text, or None.'''
        for message in self.messages:
            if text in message:
                return message
        return None


def _decompress(data):
    '''Decompresses the decrypted data, if it is compressed.'''
    try:
        z_obj = zlib.decompressobj()
        decompressed = z_obj.decompress(data)
        if not z_obj.eof:
            # truncated/damaged backup, but the recovered prefix may still be useful
            logging.getLogger('wa_crypt_tools').error('The encrypted backup file is truncated (damaged).')
        return decompressed
    except zlib.error:
        # not compressed: multi file backups and non database files (stickers,
        # backup_settings.json...) are stored as is
        return data


def get_info(encrypted_path):
    '''
    Returns a dict of strings with information read from the encrypted backup header,
    or an empty dict if the header could not be parsed. No key is needed for this.
    '''
    from wa_crypt_tools.lib.db.dbfactory import DatabaseFactory
    info = {}
    with _LogCollector():
        try:
            with open(encrypted_path, 'rb') as encrypted:
                db = DatabaseFactory.from_file(encrypted)
                if db is None:
                    return info
                # Database12 / Database14 / Database15
                info['format'] = type(db).__name__.replace('Database', 'crypt')
                props = getattr(getattr(db, 'props', None), 'props', None)
                if props is not None:
                    if getattr(props, 'app_version', None):
                        info['appVersion'] = str(props.app_version)
                    if getattr(props, 'jidSuffix', None):
                        info['jidSuffix'] = str(props.jidSuffix)
        except Exception:
            pass
    return info


def decrypt(key_path, encrypted_path, output_path):
    '''
    Decrypts a WhatsApp crypt12/crypt14/crypt15 backup.

    Parameters:
        key_path: str
            path to the WhatsApp key file ('key' or 'encrypted_backup.key') or the 64 char hex key
        encrypted_path: str
            path to the encrypted backup
        output_path: str
            path where the decrypted content will be written. It is only written on success.

    Returns:
        None if the backup was successfully decrypted, an error message otherwise.
    '''
    from wa_crypt_tools.lib.key.keyfactory import KeyFactory
    from wa_crypt_tools.lib.db.dbfactory import DatabaseFactory

    with _LogCollector() as log:
        try:
            key = KeyFactory.new(key_path)
        except Exception as e:
            return 'Could not load the key: {}'.format(e)
        if key is None:
            return log.last() or 'Could not load the key'

        try:
            with open(encrypted_path, 'rb') as encrypted:
                db = DatabaseFactory.from_file(encrypted)
                if db is None:
                    return log.last() or 'Unsupported or corrupted encrypted backup'
                decrypted = db.decrypt(key, encrypted.read())
        except Exception as e:
            return 'Decryption failed: {}'.format(e)

        # The AES-GCM authentication tag is what tells a wrong key from a good one.
        # The content itself is not checked, so any backed up file is accepted, not
        # only databases.
        mismatch = log.find(_KEY_MISMATCH)
        if mismatch:
            return mismatch

        decrypted = _decompress(decrypted)
        if not decrypted:
            return 'Decrypted data is empty'

        with open(output_path, 'wb') as out:
            out.write(decrypted)
        return None
