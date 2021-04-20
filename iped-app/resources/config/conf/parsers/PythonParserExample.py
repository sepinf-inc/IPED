'''
Python parser example.
For more information, read the documentation at https://github.com/sepinf-inc/IPED/wiki/Contributing
'''

from org.apache.tika.mime import MediaType
from org.apache.tika.sax import XHTMLContentHandler

class PythonParserExample:
    '''
    Example of a python parser. This must be thread safe i.e. do not create instance attributes.
    '''
    
    def getSupportedTypes(self, context):
        '''
        Returns:
            list of supported media types handled by this parser
        '''
        
        return [MediaType.parse("aplication/xxxxxx")]
    
    def parse(self, stream, handler, metadata, context):
        '''
         Parses each item found in case of the supported types.
         
         Parameters:
            stream: java.io.InputStream
                the raw (binary) content of the file
            handler: org.xml.sax.ContentHandler
                the content handler where you should output parsed content
            metadata: org.apache.tika.metadata.Metadata
                the metadata object from where you can get and store new parsed properties
            context: org.apache.tika.parser.ParseContext
                the parsing context from where you can get parsing configuration
        '''
        
        xhtml = XHTMLContentHandler(handler, metadata);
        xhtml.startDocument()
        try:
            # write the output of your parser to a xhtml handler
            xhtml.startElement("p")
            xhtml.characters("parsed content example")
            xhtml.endElement("p")
            
            # populate metadata to be shown as new item properties 
            metadata.add("propertyName", "propertyValue")
            
        finally:
            xhtml.endDocument()