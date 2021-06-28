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
        return
    
    def finish(self):      
        return 
        
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
        Text_separed_from_words = tokenizer.tokenize(text)   
        
        #Function to remove stopwords from text
        def remove_stopwords(words, stopwords):
           #Remove stopwords, small words and laughs as "kk"
           words_without_stopwords = []
           for item in words:
              if item not in stopwords and len(item) >= 3 and 'kk' not in item:
                 words_without_stopwords.append(item)
           return words_without_stopwords
        
        #Function to create Bag of Words
        def Create_bag_of_words(words):
           wordfreq ={}
           for item in words:
              if item not in wordfreq.keys():
                 wordfreq[item] = 1
              else:
                 wordfreq[item] += 1     
           return wordfreq
        
        #Removing imported stopwords from text
        words_without_stopwords = remove_stopwords(Text_separed_from_words, self.stopWordsSet)
        
        #Stemming function - Stemming is the process of reducing a word to its word stem that affixes to suffixes and prefixes or to the roots of words known as a lemma.
        def stemming(words):
            stem = PorterStemmer()
            for item in words:
               item = stem.stem(item)
            return words
        
        #Stemming words in text
        words_without_stopwords = stemming(words_without_stopwords)
        
        #Creating Bag of Words from stemming words
        Bag_of_words = Create_bag_of_words(words_without_stopwords)
        
        #Selection of the top 10 words by frequency
        most_freq = heapq.nlargest(10, Bag_of_words, key=Bag_of_words.get)
        
        #Setting most frequency words in Extra Attributes in FrequentWords
        item.setExtraAttribute('FrequentWords', most_freq) 
