import os, re
import heapq
from java.lang import System
import nltk
from nltk.corpus import stopwords
from nltk.stem.porter import PorterStemmer
from nltk.stem.wordnet import WordNetLemmatizer
from nltk.tokenize import RegexpTokenizer
import time
import threading
from org.apache.commons.lang3 import StringUtils

t0 = 0
t1 = 0
t2 = 0
t3 = 0
t4 = 0
t0Lock = threading.Lock()
t1Lock = threading.Lock()
t2Lock = threading.Lock()
t3Lock = threading.Lock()
t4Lock = threading.Lock()
statsLogged = False

class FrequentWordsTask:
    
    enabled = False
    configDir = None
    stopWordsSet = None
    stemmer = None
    
    def isEnabled(self):
        return True
    
    def getConfigurables(self):
        return
        
    def init(self, configManager):
        #Import stopwords from language settings in LocalConfig.txt 
        language = System.getProperty('iped-locale')
        if language == 'pt-BR':
           self.stopWordsSet = set(stopwords.words('portuguese'))
        else:
           self.stopWordsSet = set(stopwords.words('english'))
        self.stemmer = PorterStemmer()
        return
    
    def finish(self):
        global statsLogged
        if not statsLogged:
           logger.info('to lower case time: ' + str(t0))
           logger.info('tokenization time: ' + str(t1))
           logger.info('remove stopwords time: ' + str(t2))
           logger.info('bag of words time: ' + str(t3))
           logger.info('select top words time: ' + str(t4))
           statsLogged = True
        return
        
    #Function to remove stopwords from text
    def remove_stopwords(self, words, stopwords):
       #Remove stopwords, small words and laughs as "kk"
       words_without_stopwords = []
       for item in words:
          if item not in stopwords and len(item) >= 3 and 'kk' not in item:
             words_without_stopwords.append(item)
       return words_without_stopwords
       
    #Function to create Bag of Words
    def create_bag_of_words(self, words):
       wordfreq = {}
       for item in words:
          if item not in wordfreq.keys():
             wordfreq[item] = 1
          else:
             wordfreq[item] += 1     
       return wordfreq
    
    def process(self, item):
        
        #Just Chats and Email
        #categories = item.getCategorySet().toString()
        #if not ("Chats" in categories or "Emails" in categories):
        #   return 
        
        ti = time.time()
        
        text = StringUtils.lowerCase(item.getParsedTextCache())
        
        tj = time.time()
        with t0Lock:
            global t0
            t0 += tj - ti
        ti = tj
        
        #Tokenization
        tokenizer = RegexpTokenizer(r'\w+')
        text_separed_from_words = tokenizer.tokenize(text)
        
        tj = time.time()
        with t1Lock:
            global t1
            t1 += tj - ti
        ti = tj
        
        #Removing imported stopwords from text
        words_without_stopwords = self.remove_stopwords(text_separed_from_words, self.stopWordsSet)
        
        tj = time.time()
        with t2Lock:
            global t2
            t2 += tj - ti
        ti = tj
        
        #Creating Bag of Words from stemming words
        bag_of_words = self.create_bag_of_words(words_without_stopwords)
        
        tj = time.time()
        with t3Lock:
            global t3
            t3 += tj - ti
        ti = tj
        
        #Selection of the top 10 words by frequency
        most_freq = heapq.nlargest(10, bag_of_words, key=bag_of_words.get)
        
        tj = time.time()
        with t4Lock:
            global t4
            t4 += tj - ti
        
        #Setting most frequency words in Extra Attributes in FrequentWords
        item.setExtraAttribute('FrequentWords', most_freq) 
