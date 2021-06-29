import os, re
import heapq
from java.lang import System
import nltk
from nltk.corpus import stopwords
from nltk.stem.porter import PorterStemmer
from nltk.stem.wordnet import WordNetLemmatizer
from nltk.tokenize import RegexpTokenizer

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
        
        text = str(item.getParsedTextCache()).lower()
        #Remove numbers
        text = re.sub(r'\d+','',text)
        
        #Tokenization
        tokenizer = RegexpTokenizer(r'\w+')
        text_separed_from_words = tokenizer.tokenize(text)   
        
        #Removing imported stopwords from text
        words_without_stopwords = self.remove_stopwords(text_separed_from_words, self.stopWordsSet)
        
        #Stemming words in text
        words_without_stopwords = self.stem(words_without_stopwords)
        
        #Creating Bag of Words from stemming words
        bag_of_words = self.create_bag_of_words(words_without_stopwords)
        
        #Selection of the top 10 words by frequency
        most_freq = heapq.nlargest(10, bag_of_words, key=bag_of_words.get)
        
        #Setting most frequency words in Extra Attributes in FrequentWords
        item.setExtraAttribute('FrequentWords', most_freq) 
