'''
Python parser example.
To use python parsers, first you must install JEP, see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
Save your parser in 'parsers' folder. The class name must be equal to the script name without the extension. 
For more info about general parser api, see https://github.com/sepinf-inc/IPED/wiki/Contributing
'''

from org.apache.tika.sax import XHTMLContentHandler
from org.apache.tika.io import TemporaryResources

class PythonParserExample:
    '''
    Example of a python parser. This class must be thread safe.
    One way of achieving this is creating an immutable class i.e. do not create instance attributes.
    '''
    
    def getSupportedTypes(self, context):
        '''
        Returns:
            list of supported media types handled by this parser
        '''
        return ["application/xxxxxx"]
    
    
    # def getSupportedTypesQueueOrder(self):
        '''
        This method is optional. You only need to implement it if your parser
        needs to access other case items different from the item being processed.
        E.G. you want to access case multimedia files while processing a chat database
        to insert the attachments in the chats being decoded. To work, you need to
        process the database mediaType after the attachments, in a later queue,
        so the attachments will be already indexed and ready to be searched for.
        Default queue number is 0 (first queue) if not defined for a mediaType.
        
        Returns:
            dictionary mapping mediaTypes to a queue number
        '''    
        # return {"application/xxxxxx" : 1}
    
    
    def parse(self, stream, handler, metadata, context):
        '''
         Parses each item found in case of the supported types.
         
         Parameters:
            stream: java.io.InputStream
                the raw (binary) content of the file
            handler: org.xml.sax.ContentHandler
                the content handler where you should output parsed content
            metadata: org.apache.tika.metadata.Metadata
                the metadata object from where you can get basic properties and store new parsed ones.
            context: org.apache.tika.parser.ParseContext
                the parsing context from where you can get parsing configuration
                
        Raises
            IOException: java.io.IOException
                if the file stream being read throws an IOException
            SAXException: org.xml.sax.SAXException
                if there is an error when writing the parser output to the handler.
            TikaException: org.apache.tika.exception.TikaException
                you can throw a TikaException if the file being parsed is corrupted or not supported.
        '''
        
        xhtml = XHTMLContentHandler(handler, metadata);
        xhtml.startDocument()
        
        # uncomment if used below
        # tmpResources = TemporaryResources()
        try:
            '''
            Read file contents from the stream using the java.io.InputStream API.
            Do not hold too much data in memory. If you need, you can spool to file
            using the following code to create a temp file with the contents:
            
            from org.apache.tika.io import TikaInputStream
            tis = TikaInputStream.get(stream, tmpResources)
            tmpFilePath = tis.getFile().getAbsolutePath()
            '''
            
            # decode file contents and write the output of your parser to a xhtml document
            xhtml.startElement("p")
            xhtml.characters("parsed content example")
            xhtml.endElement("p")
            
            # populate parsed metadata to be shown as new item properties 
            metadata.add("propertyName", "propertyValue")
            
        finally:
            xhtml.endDocument()
            # if tmpResources is used above you must close it
            # tmpResources.close()