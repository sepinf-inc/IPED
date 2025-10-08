import requests
#need to install requests lib: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py requests
#also numpy for some reason: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py "numpy<2.0" 
import json
from bs4 import BeautifulSoup, NavigableString, Tag
import re
from datetime import datetime

# configuration properties
enableProp = 'enableAISummarization'
enableWhatsAppSummarizationProp = 'enableWhatsAppSummarization' # This is for IPED internal Parser
enableUFEDChatSummarizationProp = 'enableUFEDChatSummarization' # This is for UFED Chat Parser - x-ufed-chat-preview
minimumContentLengthProp = 'minimumContentLength' # Minimum item content length to perform summarization in characters
remoteServiceAddressProp = 'remoteServiceAddress'
configFile = 'AISummarizationConfig.txt'



def create_summaries_request(msgs: list[dict] , base_url: str = "127.0.0.1:1111"):
    """
    Sends a POST request to the /api/create_summaries_from_msgs endpoint with chat msgs.

    Args:
        msgs (list[dict]): The chat msgs to be summarized, with content, direction, sender name etc.
        base_url (str): The base URL, defaults to "http://localhost:1111" for local development.

    Returns:
        dict: The JSON response from the API, or an error message.
    """
    url = f"http://{base_url}/api/create_summaries_from_msgs"
    headers = {"Content-Type": "application/json"}
    payload = {"msgs": msgs}

    try:
        response = requests.post(url, headers=headers, data=json.dumps(payload))
        response.raise_for_status()  # Raise an exception for HTTP errors (4xx or 5xx)
        return response.json()
    except requests.exceptions.HTTPError as http_err:
        #print(f"HTTP error occurred: {http_err}")
        #print(f"Response content: {response.text}")
        return {"error": f"HTTP error: {http_err}-{response.text}"}
    except requests.exceptions.ConnectionError as conn_err:
        #print(f"Connection error occurred: {conn_err}")
        return {"error": f"Connection error: {conn_err}"}
    except requests.exceptions.Timeout as timeout_err:
        #print(f"Timeout error occurred: {timeout_err}")
        return {"error": f"Timeout error: {timeout_err}"}
    except requests.exceptions.RequestException as req_err:
        #print(f"An unexpected error occurred: {req_err}")
        return {"error": f"An unexpected error: {req_err}"}
    except json.JSONDecodeError as json_err:
        #print(f"JSON decode error: {json_err}")
        #print(f"Raw response: {response.text}")
        return {"error": f"JSON decode error: {json_err} - {response.text}"}


def _clean_timestamp(raw: str) -> str:
    """
    Normaliza carimbos como “2023-01-08 19:38:20-0300”
    para ISO 8601 (“2023-01-08T19:38:20-03:00”).
    Se não conseguir entender, devolve o texto original.
    """
    _TS_CLEAN = re.compile(r"\s+")

    if "Editada em" in raw:
        raw = raw.split("Editada em")[0].strip()
    txt = _TS_CLEAN.sub(" ", raw).strip()
    
    try:
        # 2023-01-08 19:38:20-0300
        dt = datetime.strptime(txt, "%Y-%m-%d %H:%M:%S%z")
        return dt.isoformat()
    except ValueError:
        pass
    try:
        # 2023-01-08 19:38:20-0300
        dt = datetime.strptime(txt, "%Y-%m-%d %H:%M:%S %z")
        return dt.isoformat()
    except ValueError:
        return txt

def _extract_text_nodes(tag: Tag) -> str:
    """
    Concatena somente os nós-texto “soltos” dentro de *tag*.
    Ignora <br>, <span>, etc.
    """


    parts = [t.strip() for t in tag.contents
             if isinstance(t, NavigableString) and t.strip()]
    return " ".join(parts)

def getMessagesFromChatHTML(html_text: str) -> list[dict]:

    html_text = html_text.replace('<br/>', '')
    soup = BeautifulSoup(html_text, "html.parser")
    msgs: list[dict] = []

    len_mgs_content = 0

    for block in soup.select("div.linha"):
        msg_div = block.find("div", class_=["incoming", "outgoing"])

        if msg_div is None:                 # linha de sistema / vazia
            continue

        if msg_div.find("div", class_=["systemmessage"]):
            continue

        msg_id = block.get('id')

        direction = ("received"
                     if "incoming" in msg_div["class"]
                     else "sent")

        forwarded = False
        if msg_div.find("span", class_=["fwd"]):
            forwarded = True

        name = msg_div.find("span").get_text(" ", strip=True)
        #print(msg_div.prettify())
        timestamp_span = msg_div.find("span", class_="time")
        if not timestamp_span:
            print("No timestamp found")
            timestamp = "N/A"
        else:
            timestamp_raw = timestamp_span.get_text(" ", strip=True)
            timestamp = _clean_timestamp(timestamp_raw)

        # -------------------------------------------------------------------#
        # 1) transcrição de áudio (fica em <i> … </i>)
        # -------------------------------------------------------------------#
        content = ""
        kind = ""
        i_tag = msg_div.find("i")
        if i_tag and msg_div.find("div", class_=["audioImg"]):
            content = i_tag.get_text(" ", strip=True)
            kind = "audio transcription"


        # -------------------------------------------------------------------#
        # 2) anexo (áudio / vídeo / outro)
        # -------------------------------------------------------------------#
        if not content:
            #kind = "other"

            # áudio ➜ ícone <div class="audioImg">
            if msg_div.find("div", class_="audioImg"):
                kind = "audio"
                content = f" "
            if msg_div.find("div", class_="imageImg"):
                kind = "image"
                content = f" "
            if msg_div.find("div", class_="videoImg"):
                kind = "video"
                content = f" "
            # vídeo ou imagem ➜ thumbnail <img class="thumb" … title="video|image">
            else:
                thumb = msg_div.find("img", class_="thumb")
                if thumb and thumb.get("title"):
                    title = thumb["title"].lower()
                    if "video" in title:
                        kind = "video"
                        content = f" "
                    elif "image" in title:
                        kind = "image"
                        content = f" "

            #a_tag = msg_div.find("a", href=True)
            #if a_tag:
            #    content = f" "

        # -------------------------------------------------------------------#
        # 3) texto “puro”
        # -------------------------------------------------------------------#

        if not content:
            content = _extract_text_nodes(msg_div)
            kind = "text"        
            
        # ainda vazio? provavelmente só thumbs ou attachments sem link → pula
        if not content:
            continue
        
        len_mgs_content = len_mgs_content + len(content)

        msgs.append(
            {
                "id":msg_id,
                "direction": direction,
                "name": name,
                "timestamp": timestamp,
                "content": content,
                "kind": kind,
                "forwarded": forwarded
            }
        )

    return msgs, len_mgs_content




# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class AISummarizationTask:

    enabled = False
    remoteServiceAddress = None
    enableWhatsAppSummarization = False
    enableUFEDChatSummarization = False
    minimumContentLength = None
    
    
    def isEnabled(self):
        return AISummarizationTask.enabled
    

    def __init__(self):
        return

    # Returns if this task is enabled or not. This could access options read by init() method.
    def isEnabled(self):
        return AISummarizationTask.enabled

    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]

    # Do some task initialization, like reading options, custom config files or model.
    # It is executed when application starts by each processing thread on its own class instance.
    def init(self, configuration):
        taskConfig = configuration.getTaskConfigurable(configFile)
        AISummarizationTask.enabled = taskConfig.isEnabled()

        if not AISummarizationTask.enabled:
            return
        
        extraProps = taskConfig.getConfiguration()

        AISummarizationTask.remoteServiceAddress = extraProps.getProperty(remoteServiceAddressProp)

        if not AISummarizationTask.remoteServiceAddress:
            print('[AISummarizationTask]: Error: task enabled but remoteServiceAddress not set on config file.')
            return

        AISummarizationTask.enableWhatsAppSummarization = extraProps.getProperty(enableWhatsAppSummarizationProp)
        AISummarizationTask.enableUFEDChatSummarization = extraProps.getProperty(enableUFEDChatSummarizationProp)
        AISummarizationTask.minimumContentLength = int(extraProps.getProperty(minimumContentLengthProp))

        return


    def processChat(self, item):
        
        
        from iped.properties import ExtraProperties

        chunk_summaries = item.getExtraAttribute(ExtraProperties.SUMMARIES)
        if chunk_summaries is not None:
            return
        
        inputStream = item.getBufferedInputStream()
        
        raw_bytes = inputStream.readAllBytes()
        # Ensure bytes are in valid range
        valid_bytes = bytes(b & 0xFF for b in raw_bytes)
        chatHtml = valid_bytes.decode('utf-8', errors='replace')

        inputStream.close()

        msgs, len_mgs_content =  getMessagesFromChatHTML(chatHtml)

        #print('------------------------------------------------------------------------------------------')
        #print(msgs)
        #print('------------------------------------------------------------------------------------------')

        #print(f"Num of messages: {len(msgs)}  Size of contents: {len_mgs_content}")

        if(len_mgs_content < AISummarizationTask.minimumContentLength):
            #skip 
            #print(f"Small chat - less than {AISummarizationTask.minimumContentLength} characters")
            return

        result = create_summaries_request(msgs, AISummarizationTask.remoteServiceAddress)
        #result = create_summaries_request(chatHtml, AISummarizationTask.remoteServiceAddress)

        err = result.get('error')
        if err:
            print(f"[AISummarizationTask]: {item.getName()} - {err}")
            return

        #print(result['summaries'])
        summaries = result['summaries']

        #For testing, just append some strings to the summaries
        #summaries = []
        #summaries.append('adas asda gfdfg', 'sdfs sdf sdfsda')

        item.setExtraAttribute(ExtraProperties.SUMMARIES, summaries)
        


    
    # Process an Item object. This method is executed on all case items.
    # It can access any method of Item class and store results as a new extra attribute.
    def process(self, item):
        if not AISummarizationTask.enabled:
            return

        # WhatsApp chats processed by internal IPED parser
        if AISummarizationTask.enableWhatsAppSummarization and "whatsapp-chat" in item.getMediaType().toString():
            self.processChat(item)
            return
        # Process UFED Chats 
        if AISummarizationTask.enableUFEDChatSummarization and "x-ufed-chat-preview" in item.getMediaType().toString():
            self.processChat(item)
            return
            



    # Called when task processing is finished. Can be used to cleanup resources.
    # Objects "ipedCase" and "searcher" are provided, so case can be queried for items and bookmarks can be created.
    def finish(self):
        return