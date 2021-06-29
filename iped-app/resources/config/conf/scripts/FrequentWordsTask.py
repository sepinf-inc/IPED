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

timeLock = threading.Lock()
statsLogged = False
t0 = 0
t1 = 0
t2 = 0
t3 = 0
t4 = 0
t5 = 0

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
           logger.info('remove numbers time: ' + str(t0))
           logger.info('tokenization time: ' + str(t1))
           logger.info('remove stopwords time: ' + str(t2))
           logger.info('stemming time: ' + str(t3))
           logger.info('bag of words time: ' + str(t4))
           logger.info('select top words time: ' + str(t5))
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
       
    #Stemming function - Stemming is the process of reducing a word to its word stem that affixes to suffixes and prefixes or to the roots of words known as a lemma.
    def stem(self, words):
        for item in words:
           item = self.stemmer.stem(item)
        return words
    
    def process(self, item):
        
        #Just Chats and Email
        categories = item.getCategorySet().toString()
        if not ("Chats" in categories or "Emails" in categories):
           return 
        
        ti = time.time()
        
        text = str(item.getParsedTextCache()).lower()
        #Remove numbers
        #text = re.sub(r'\d+','',text)
        
        tj = time.time()
        with timeLock:
            global t0
            t0 += tj - ti
        
        #Tokenization
        tokenizer = RegexpTokenizer(r'\w+')
        text_separed_from_words = tokenizer.tokenize(text)
        
        ti = time.time()
        with timeLock:
            global t1
            t1 += ti - tj
        
        #Removing imported stopwords from text
        words_without_stopwords = self.remove_stopwords(text_separed_from_words, self.stopWordsSet)
        
        tj = time.time()
        with timeLock:
            global t2
            t2 += tj - ti
        
        #Stemming words in text
        words_without_stopwords = self.stem(words_without_stopwords)
        
        ti = time.time()
        with timeLock:
            global t3
            t3 += ti - tj
        
        #Creating Bag of Words from stemming words
        bag_of_words = self.create_bag_of_words(words_without_stopwords)
        
        tj = time.time()
        with timeLock:
            global t4
            t4 += tj - ti
        
        #Selection of the top 10 words by frequency
        most_freq = heapq.nlargest(10, bag_of_words, key=bag_of_words.get)
        
        ti = time.time()
        with timeLock:
            global t5
            t5 += ti - tj
        
        #Setting most frequency words in Extra Attributes in FrequentWords
        item.setExtraAttribute('FrequentWords', most_freq) 
