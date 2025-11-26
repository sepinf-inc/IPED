# Python task script example. It must be installed in TaskInstaller.xml to be executed.
# On Linux, you need to install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.
# see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules

import requests
import logging
import os
import json
from sys import argv, stderr, exit
import random


'''
Description
- Translate text from items in defined categories via LibreTranslate
- Translation is attached as a SubItem
- a LibreTranslate server must be running. Installation and configuration: https://github.com/LibreTranslate/LibreTranslate
 - to create the API key DB, first run libretranslate --api-key
- translated items can be filtered with translated:true
Changelog
2024-04-21
    - first Release
2024-04-23
    - prevent infinite loops if the translation delivers a text that is not actually in the target language
    - setExtraAttribute "translatedAll" (True/False) as an indication that the translation was stopped due to the configuration
    - some more info & error logging
2024-04-25
    - use of more than one translation server
    - if more than one server is used for translation, the script uses a random server alternately
'''

configFile = 'translatetext.json'
#enableProp = 'enableTranslateText'

logging.basicConfig(format='%(asctime)s [%(levelname)s] [TranslateTextTask.py] %(message)s', level=logging.DEBUG)
# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class TranslateTextTask:

    config = None
    #enabled = False

    def isEnabled(self):
        return True
        #return TranslateTextTask.enabled

    # Returns an optional list of configurable objects that can load/save parameters from/to config files.
    def getConfigurables(self):
        return []

    # Do some task initialization, like reading options, custom config files or model.
    # It is executed when application starts by each processing thread on its class instance.
    # @Params
    # configuration:    configuration manager by which configurables can be retrieved after populated.
    def init(self, configuration):
        if TranslateTextTask.config is not None:
            return
        from java.lang import System
        ipedRoot = System.getProperty('iped.root')
        with open(os.path.join(ipedRoot, 'conf', configFile)) as f:
            TranslateTextTask.config = json.load(f)
        return


    # Finish method run after processing all items in case, e.g. to clean resources.
    # It is executed by each processing thread on its class instance.
    # Objects "ipedCase" and "searcher" are provided, so case can be queried for items and bookmarks can be created, for example.
    # TODO: document methods of those objects.
    def finish(self):
        return


    # Process an Item object.

    def process(self, item):
        subItemID = 0
        item_name = item.getName()
        hash = item.getHash()
        if (hash is None) or (len(hash) < 1):
            return
        media_type = item.getMediaType().toString()
        target_language = TranslateTextTask.config.get('target_language')
        maxChars = TranslateTextTask.config.get('maxChars')
        minChars = TranslateTextTask.config.get('minChars')
        categories = TranslateTextTask.config.get('categories')


        headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json'
        }
        meta_data = item.getMetadata()
        HashDB_Status = item.getExtraAttribute('hashDb:status')
        # set HashDB_Status as list with empty element
        # to prevent error in if statement
        if HashDB_Status is None:
            HashDB_Status = ['']

        if not item.getExtraAttribute("language:detected_1") is None:
            src_lang = item.getExtraAttribute("language:detected_1")
        else:
            src_lang= "??"
        # Only processing, if item in allowed categories, source lang not target lang and not already translated and not known by hash
        #print('vor übersetzung\nKategorie %s\nTextsprache %s\nHAshDB-Status %s' % (str(item.getCategories()), str(src_lang), str(HashDB_Status)))
        if item.getCategories() in categories and src_lang != target_language and 'known' not in HashDB_Status and not item.getExtraAttribute("translatedAutomatically"):
            TranslationServers = TranslateTextTask.config.get('server')
            if len(TranslationServers) > 1:
                # more than one Translationserver, choose one randomly
                serverid = random.randint(0,len(TranslationServers)-1)
                host = TranslateTextTask.config.get('server')[serverid].get('host')
                port = TranslateTextTask.config.get('server')[serverid].get('port')
                api_key = TranslateTextTask.config.get('server')[serverid].get('api_key')
            else:
                host = TranslateTextTask.config.get('server')[0].get('host')
                port = TranslateTextTask.config.get('server')[0].get('port')
                api_key = TranslateTextTask.config.get('server')[0].get('api_key')
            url = '%s:%s/translate' % ( host, port)
            if item.getParsedTextCache() is not None:
                if  len(item.getParsedTextCache()) > minChars:
                    if maxChars > 0:
                        Text2Translate = item.getParsedTextCache()[0:maxChars]
                    else:
                        Text2Translate = item.getParsedTextCache()
                    data = {
                        'q' : Text2Translate,
                        'source': 'auto',
                        'target': target_language,
                        'format' : 'text',
                        'api_key': api_key
                        }
                    try:
                        response = requests.post(url=url, headers=headers, data=data)
                        if response.status_code == 200:
                            US_TMP = eval(response.text)
                            if len(item.getParsedTextCache()) > maxChars and maxChars > 0:
                                UEBERSETZUNG = '%s\n\n%s' % ( str(US_TMP.get('translatedText')) , 'HINT: Translation stopped in case of configured limit')
                                fullTranslation = False
                            else:
                                UEBERSETZUNG = str(US_TMP.get('translatedText'))
                                fullTranslation = True
                            item.setExtraAttribute("translated", True)
                            item.setExtraAttribute("translatedAll", fullTranslation)
                            item.setExtraAttribute("translatedText", UEBERSETZUNG)
                            logging.info("Text translated from item %s of media type %s with hash %s", item_name, media_type, hash)
                            logging.info("set new SubItem for item %s" , item_name)
                            newSubItem(self, item, UEBERSETZUNG, subItemID)
                            subItemID += 1
                        else:
                            logging.error("Error: Text not translated from item %s of media type %s with hash %s", item_name, media_type, hash)
                            item.setExtraAttribute("translated", False)
                            item.setExtraAttribute("translation_error", response.text)
                    except Exception as err:
                        item.setExtraAttribute("translated", False)
                        item.setExtraAttribute("translation_error", str(err))
                        logging.error("Error: Text not translated from item %s: %s", item_name, str(err))
                else:
                    logging.info("Text not translated from item %s in case of to few characters", item_name)
                    item.setExtraAttribute("translated", False)

def newSubItem(self, item, text, subItemID):
    from iped.engine.data import Item
    from iped.engine.task import ExportFileTask
    from org.apache.commons.lang3 import StringUtils
    from java.io import ByteArrayInputStream
    from iped.engine.core import Statistics

    newItem = Item()
    newItem.setParent(item)
    newItem.setName('TranslatetedText_' + str(subItemID))
    newItem.setPath(item.getPath() + ">>" + newItem.getName())
    newItem.setSubItem(True)
    newItem.setSubitemId(subItemID)
    # Set this attribute to prevent infinite loops
    # if the translation delivers a text that is not actually in the target language
    newItem.setExtraAttribute("translatedAutomatically", True)
    newItem.setSumVolume(False);
    try:
        # export item content to case storage
        exporter = ExportFileTask();
        exporter.setWorker(worker);
        bytes = StringUtils.getBytes(text, 'UTF-8')
        dataStream = ByteArrayInputStream(bytes);
        exporter.extractFile(dataStream, newItem, item.getLength());
        Statistics.get().incSubitemsDiscovered();
        worker.processNewItem(newItem);
    except Exception as err:
        logging.error("set new SubItem for item %s failed" , item_name)
