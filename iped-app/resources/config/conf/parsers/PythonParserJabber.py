'''
Python parser example.
To use python parsers, first you must install JEP, see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
Save your parser in 'parsers' folder. The class name must be equal to the script name without the extension. 
For more info about general parser api, see https://github.com/sepinf-inc/IPED/wiki/Contributing
'''

from org.apache.tika.sax import XHTMLContentHandler
from org.apache.tika.io import TikaInputStream
from org.apache.tika.io import TemporaryResources
from org.apache.tika.metadata import Metadata, Message
from org.apache.tika.exception import TikaException
from org.apache.tika.extractor import EmbeddedDocumentExtractor
from org.apache.tika.sax import EmbeddedContentHandler
from org.apache.tika.parser.html import HtmlParser
from iped3.util import ExtraProperties
from iped3.util import BasicProps
from dpf.sp.gpinf.indexer.parsers import IndexerDefaultParser
from dpf.sp.gpinf.indexer.util import EmptyInputStream
from dpf.mg.udi.gpinf.whatsappextractor import Util
from dpf.sp.gpinf.indexer.parsers.util import IndentityHtmlParser
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
        <span style="font-family: 'Roboto-Medium'; color: #b4c74b;">PYTHON_PARSER_SENDER</span><br/>
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
            origFileName = metadata.get(Metadata.RESOURCE_NAME_KEY)
            
            # extract text from html chat to be indexed, searched for regexes and so on...
            HtmlParser().parse(tis, EmbeddedContentHandler(xhtml), metadata, context)

            soup = BeautifulSoup(open(tmpFilePath,'r'), "html.parser")
            body = soup.find("body")
            title =soup.find('title').text
            temp_to_date = title.split(" on ",1)[0]
            temp_client_app = title.split(" on ",1)[1].split()
            app = temp_client_app[1].strip("(").strip(")")
            client = temp_client_app[0].strip("/")
            host_system = "sistema_%s_%s"%(app,client)
            participants = [client, temp_to_date.split(" at ",1)[0].split("with ",1)[1]]
            # message_day = temp_to_date.split(" at ",1)[1].split(" ",1)[0]
            message_day = re.search("\d{2}/\d{2}/\d{4}",temp_to_date).group(0)
            filedate = os.path.basename(origFileName).replace(".html","").replace("BRT","")
            filedate_tz = datetime.strptime(filedate, '%Y-%m-%d.%f%z').tzinfo
            host_system_messages = ["arquivo","envio"]
            messages_list=[]

            '''
            files from jabber 2017 look like:
            <html><head><meta http-equiv="content-type" content="text/html; charset=UTF-8"><title>Conversation with alice@xmpp.al at 20/03/2017 08:50:25 on bob@xmpp.bo (jabber)</title></head><body><h3>Conversation with alice@xmpp.al at 20/03/2017 08:50:25 on bob@xmpp.bo (jabber)</h3>
            <font color="#16569E"><font size="2">(08:50:30)</font> <b>bob@xmpp.bo/333327081514908999591234:</b></font> hello<br/>
            <font color="#A82F2F"><font size="2">(08:50:48)</font> <b>remote_nickname:</b></font> world 	  	
            ''' 

            # There are at least two possible formats for the messages
            possible_tags = ["span","font"]
            title_rep = body.next_element
            assert title_rep.name in ["h1", "h2", "h3"]
            new_line = title_rep.next_sibling
            # assert new_line == "\n" or new_line.name == "p"
            if new_line.name == "p":
                first_msg = new_line.next_element.next_element
            elif new_line == "\n":
                first_msg = new_line.next_sibling
            else:
                raise "Tag not found exception"
            assert first_msg.name in possible_tags
            all_messages = [first_msg] + [x for x in first_msg.find_next_siblings(first_msg.name)] 

            for idx, html_message in enumerate(all_messages):
                idict={}
                curr_tag = html_message.name

                # If the format "dd/mm/aaaa" is not present, then this means that the message content is itself
                # a span sibling. So it is necessary to recover the previous span tag, as it contains the metadata
                # for the message 

                '''
                <span style="color: #A82F2F"><span style="font-size: smaller">(17:58:02)</span> <b>alice@jabber.at:</b></span> sim com certeza<br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:02:46)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> man acho q peguei algo aqui nesse js, q baixa la no pc<br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:02:50)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> vi um dominio aqui <br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:02:56)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> rastreei ele é aqui q ta hospedado<br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:02:57)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> <span style='color: #5A5A5A;'><span style='font-family: Lato, Helvetica, sans-serif;'><a href="http://www.dominio1.com">www.dominio1.com</a></span></span><br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:03:25)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> <a href="http://prntscr.com/aaaaaaaa">http://prntscr.com/aaaaaaaa</a><br>
                <span style="color: #16569E"><span style="font-size: smaller">(18:03:29)</span> <b>bob1@jabber.ru/7433774929690123456:</b></span> resto ta no raiodenuvem<br>
                '''
                if not html_message.find(text=re.compile("\d{2}:\d{2}:\d{2}")):
                    curr_msg = html_message
                    curr_metadata = html_message.find_previous_sibling("span")
                else:
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
                elif isinstance(curr_msg, str):
                    curr_msg_text = curr_msg

                if curr_msg_text in ["", " "]:
                    block_msg = ""
                    while curr_msg.name not in [curr_tag]:
                    # while curr_msg.next_sibling.name != curr_tag and not isinstance(curr_msg.next_sibling, NavigableString):
                        if isinstance(curr_msg, NavigableString):
                            block_msg +=curr_msg.string
                        else:
                            block_msg +=curr_msg.text
                        curr_msg = curr_msg.next_sibling
                    curr_msg_text = block_msg

                assert isinstance(curr_metadata, Tag)
                assert isinstance(curr_msg_text, str)

                message_sender = curr_metadata.find("b")
                if message_sender:
                    message_sender = message_sender.text.rsplit("/",1)[0].strip(":")
                else:
                    '''
                    it could be a system message, such as:
                    <html><head><meta http-equiv="content-type" content="text/html; charset=UTF-8"><title>Conversation with alice@dukgo.com at 05/02/2017 16:32:47 on bob@xmpp.cm/ (jabber)</title></head><body><h3>Conversation with alice@dukgo.com at 05/02/2017 16:32:47 on bob@xmpp.cm/ (jabber)</h3>
                    <font size="2">(16:32:51)</font><b> Tentando iniciar uma conversa privada com alice@dukgo.com...</b><br/>
                    <font size="2">(16:32:52)</font><b> alice@dukgo.com ainda não foi autenticado.  Você deve <a href="https://otr-help.cypherpunks.ca/4.0.2/authenticate.php?lang=pt_BR">autenticar</a> este amigo.</b><br/>
                    '''
                    if any(x for x in participants if x in curr_msg_text) or any(x for x in host_system_messages for x in curr_msg_text):
                        message_sender = host_system
                    else:
                        raise "host_system_message not registered: There are not any strings from %s in %s"%(curr_msg, host_system_messages)

                message_time = re.search("\d{2}:\d{2}:\d{2}",curr_metadata.text).group(0)
                dateobj = datetime.strptime("%sT%s"%(message_day,message_time),"%d/%m/%YT%H:%M:%S")
                idict["message_date"] = dateobj.replace(tzinfo = filedate_tz).isoformat()
                idict["message_sender"] = message_sender
                idict["message_text"] = curr_msg_text
                
                assert " "  not in  idict.values()
                messages_list.append(idict)

            new_messages_list = []
            msg_num = 0
            msg_name_prefix = "Jabber chat message "
            sorted_msgs_list = sorted(messages_list, key=lambda k: k['message_date'])
            for m in sorted_msgs_list:
                iped_date = m["message_date"]
                iped_sender = m["message_sender"]
                if iped_sender == host_system:
                    iped_receiver = client
                else:
                    # remote participant sent the message
                    iped_receiver = [x for x in participants if x not in [iped_sender,host_system]][-1]
                iped_text = m["message_text"]
                
                if client in iped_sender:
                    iped_direction = "outgoing to"
                else:
                    iped_direction = "incoming from"
                
                meta = Metadata()
                meta.set(BasicProps.LENGTH, "")
                meta.set(Metadata.RESOURCE_NAME_KEY, msg_name_prefix + str(msg_num))
                meta.set(Message.MESSAGE_FROM, iped_sender)
                meta.set(Message.MESSAGE_TO, iped_receiver)
                meta.set(ExtraProperties.MESSAGE_DATE,iped_date)
                meta.set(ExtraProperties.MESSAGE_BODY,iped_text)
                meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, self.instant_message_mime)
                extractor.parseEmbedded(EmptyInputStream(), handler, meta, False)

                new_messages_list.append({"sender":iped_sender, "receiver":iped_receiver, "date":iped_date, "msg":iped_text, "direction":iped_direction})
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
                   