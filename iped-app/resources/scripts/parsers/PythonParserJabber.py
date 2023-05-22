'''
Python parser example.
To use python parsers, first you must install JEP, see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
Save your parser in 'parsers' folder. The class name must be equal to the script name without the extension. 
For more info about general parser api, see https://github.com/sepinf-inc/IPED/wiki/Contributing
'''

from org.apache.tika.sax import XHTMLContentHandler
from org.apache.tika.io import TikaInputStream
from org.apache.tika.io import TemporaryResources
from org.apache.tika.metadata import Metadata, Message, TikaCoreProperties
from org.apache.tika.exception import TikaException
from org.apache.tika.extractor import EmbeddedDocumentExtractor
from org.apache.tika.sax import EmbeddedContentHandler
from org.apache.tika.parser.html import HtmlParser
from iped.properties import ExtraProperties
from iped.properties import BasicProps
from iped.parsers.standard import StandardParser
from iped.utils import EmptyInputStream
from iped.parsers.whatsapp import Util
from iped.parsers.util import IndentityHtmlParser
from org.apache.commons.codec.binary import StringUtils
from java.io import ByteArrayInputStream
import os
import re
import sys
from bs4 import BeautifulSoup
from bs4.element import NavigableString, Tag
from datetime import datetime, tzinfo

class PythonParserJabber:
    
    instant_message_mime = "message/x-jabber-message"

    '''
    Example of a python parser. This class must be thread safe.
    One way of achieving this is creating an immutable class i.e. do not create instance attributes.
    '''
    
    def getSupportedTypes(self, context):
        '''
        Returns:
            list of supported media types handled by this parser
        '''
        return ["application/x-jabber-chat"]

        # return ["application/xxxxxx"]


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
            stream: java.io.InputStreareadResourceAsStringm
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
        
        MSG_TEMPLATE = """
        <div class="linha" id="PYTHON_PARSER_MSG_ID">
        <div class="PYTHON_PARSER_DIRECTION">
        <span style="font-family: Arial; color: #b4c74b;">PYTHON_PARSER_SENDER</span><br/>
        PYTHON_PARSER_MSG<br/><span class="time">
        PYTHON_PARSER_DATE
        </span>
        </div></div>
        """

        xhtml = XHTMLContentHandler(handler, metadata);
        xhtml.startDocument()
        
        # uncomment if used below
        tmpResources = TemporaryResources()
        try:
            '''
            Read file contents from the stream using the java.io.InputStream API.
            Do not hold too much data in memory. If you neUtil.readResourceAsString("wachat-html-template.txt")ed, you can spool to file
            using the following code to create a temp file with the contents:
            
            from org.apache.tika.io import TikaInputStream
            tis = TikaInputStream.get(stream, tmpResources)
            tmpFilePath = tis.getFile().getAbsolutePath()
            '''

            extractor = context.get(EmbeddedDocumentExtractor)
            tis = TikaInputStream.get(stream, tmpResources)
            tmpFilePath = tis.getFile().getAbsolutePath()
            origFileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY)
            
            # extract text from html chat to be indexed, searched for regexes and so on...
            HtmlParser().parse(tis, EmbeddedContentHandler(xhtml), metadata, context)

            soup_list = [BeautifulSoup(x,"html.parser") for x in open(tmpFilePath,'rb').readlines()]
            body = soup_list[0].find("body")
            title = soup_list[0].find('title').text
            temp_to_date = title.split(" on ",1)[0]
            temp_client_app = title.split(" on ",1)[1].split()
            app = temp_client_app[1].strip("(").strip(")")
            client = temp_client_app[0].strip("/")
            #host_system = "sistema_%s_%s"%(app,client)
            host_system = "System"
            participants = [client, temp_to_date.split(" at ",1)[0].split("with ",1)[1]]
            message_day = re.search("\d{2}/\d{2}/\d{4}",temp_to_date).group(0)
            filedate = os.path.basename(origFileName).replace(".html","").replace("BRT","")
            filedate_tz = datetime.strptime(filedate, '%Y-%m-%d.%f%z').tzinfo
            # host_system_messages = ["arquivo","envio"]
            messages_list = []

            curr_tag = None
            possible_tags = ["span", "font"]
            for tag in possible_tags:
                temp = soup_list[1].find(tag)
                if temp:
                    curr_tag = tag
            assert curr_tag is not None
            title_rep = body.next_element
            assert title_rep.name in ["h1", "h2", "h3"]
            soup_messages = [x.find(curr_tag) for x in soup_list if x.find(curr_tag)]
            nicknames_set = set()
            for idx, html_message in enumerate(soup_messages):
                idict={}
                curr_tag = html_message.name
                curr_msg = html_message.next_sibling
                curr_metadata = html_message

                '''
                Found some cases in which the message is contained within further tags
                <font color="#A82F2F"><font size="2">(16:10:56)</font> <b>FIDEL:</b></font> <body>Já tá promto</body><br/>
                '''
                if curr_msg == ' ':
                    curr_msg = curr_msg.next_sibling

                # Some messages are html formated (check line 5 of previous html code)
                if isinstance(curr_msg, Tag):
                    curr_msg_text = curr_msg.text
                elif isinstance(curr_msg, NavigableString):
                    curr_msg_text = curr_msg.string
                    block_msg = ""
                    while curr_msg.name not in [curr_tag]:
                        if curr_msg.name == "br":
                            block_msg+="\n"
                        else:
                            if isinstance(curr_msg, NavigableString):
                                text_to_add = curr_msg.string
                            else:
                                text_to_add = curr_msg.text
                            block_msg+=text_to_add
                        curr_msg = curr_msg.next_sibling
                        if not curr_msg:
                            break
                    curr_msg_text = block_msg
                elif isinstance(curr_msg, str):
                    curr_msg_text = curr_msg

                assert isinstance(curr_metadata, Tag)
                assert isinstance(curr_msg_text, str)
                
                message_sender = curr_metadata.find("b")
                if message_sender:
                    message_sender = message_sender.text.rsplit("/",1)[0].strip(":")
                    nicknames_set.add(message_sender)
                else:
                    '''
                    it could be a system message, such as:
                    <html><head><meta http-equiv="content-type" content="text/html; charset=UTF-8"><title>Conversation with alice@dukgo.com at 05/02/2017 16:32:47 on bob@xmpp.cm/ (jabber)</title></head><body><h3>Conversation with alice@dukgo.com at 05/02/2017 16:32:47 on bob@xmpp.cm/ (jabber)</h3>
                    <font size="2">(16:32:51)</font><b> Tentando iniciar uma conversa privada com alice@dukgo.com...</b><br/>
                    <font size="2">(16:32:52)</font><b> alice@dukgo.com ainda não foi autenticado.  Você deve <a href="https://otr-help.cypherpunks.ca/4.0.2/authenticate.php?lang=pt_BR">autenticar</a> este amigo.</b><br/>
                    '''
                    message_sender = host_system

                assert message_sender
                message_time = re.search("\d{2}:\d{2}:\d{2}",curr_metadata.text).group(0)
                dateobj = datetime.strptime("%sT%s"%(message_day,message_time),"%d/%m/%YT%H:%M:%S")
                idict["message_date"] = dateobj.replace(tzinfo = filedate_tz).isoformat()
                idict["message_sender"] = message_sender
                idict["message_text"] = curr_msg_text
                
                assert " " not in idict.values()
                messages_list.append(idict)

            #new_messages_list = []
            msg_num = 0
            msg_name_prefix = "Jabber chat message "
            sorted_msgs_list = sorted(messages_list, key=lambda k: k['message_date'])

            # only one message was sent
            if len(nicknames_set) == 1: 
                other_participants = [x for x in participants if x not in nicknames_set]
                nicknames_set.update(other_participants)
                
            for m in sorted_msgs_list:
                iped_date = m["message_date"]
                iped_sender = m["message_sender"]
                iped_receiver = None
                if iped_sender != host_system:
                    iped_receiver = [x for x in nicknames_set if x !=iped_sender][-1]
                iped_text = m["message_text"]
                '''
                if client in iped_sender:
                    iped_direction = "outgoing to"
                else:
                    iped_direction = "incoming from"
                '''
                meta = Metadata()
                meta.set(BasicProps.LENGTH, "")
                meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, msg_name_prefix + str(msg_num))
                meta.set(Message.MESSAGE_FROM, iped_sender)
                meta.set(Message.MESSAGE_TO, iped_receiver)
                meta.set(ExtraProperties.MESSAGE_DATE,iped_date)
                meta.set(ExtraProperties.MESSAGE_BODY,iped_text)
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, self.instant_message_mime)
                extractor.parseEmbedded(EmptyInputStream(), handler, meta, False)

                #new_messages_list.append({"sender":iped_sender, "receiver":iped_receiver, "date":iped_date, "msg":iped_text, "direction":iped_direction})
                msg_num += 1

            # Code below generates html in whatsapp format. For now we are giving
            # preference to original jabber html, so this is commented out.
            ''' 
            formatted_msgs = []
            for res in new_messages_list:
                if not isinstance(res["msg"], str):
                    res["msg"] = res["msg"].text
                msg_template = MSG_TEMPLATE
                formatted_msg = msg_template.replace("PYTHON_PARSER_SENDER", res["sender"])\
                                                .replace("PYTHON_PARSER_MSG_ID", "0")\
                                                .replace("PYTHON_PARSER_MSG", res["msg"])\
                                                .replace("PYTHON_PARSER_DATE", res["date"])\
                                                .replace("PYTHON_PARSER_DIRECTION", res["direction"])
                                                                                            
                formatted_msgs.append(formatted_msg)


            formatted_text = "\n".join(formatted_msgs)
            util = Util()
            html_chat_template = util.readResourceAsString("wachat-html-template.txt")
            css_template = util.readResourceAsString("css/whatsapp.css") 
            js_template = util.readResourceAsString("js/whatsapp.js")
            avatar = util.getImageResourceAsEmbedded("img/avatar.png")
            favicon = util.getImageResourceAsEmbedded("img/favicon.ico")


            final_html = html_chat_template.replace("${messages}", formatted_text).replace("${title}",title)\
                            .replace("${css}",css_template).replace("${javascript}",js_template)\
                            .replace("${favicon}",favicon).replace("${avatar}",avatar)\
                            .replace("${id}",os.path.split(tmpFilePath)[1])
            
            byteInputStream = ByteArrayInputStream(StringUtils.getBytesUtf8(final_html))
            IndentityHtmlParser().parse(byteInputStream, context, xhtml)
            '''

        except Exception as exc:
            raise exc


        finally:

            xhtml.endDocument()
#            if tmpResources is used above you must close itEmbeddedDocumentExtractor.class
            tmpResources.close()
                   