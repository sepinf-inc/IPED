import requests, time
#need to install requests lib: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py requests
#also numpy for some reason: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py "numpy<2.0" 
import json
from bs4 import BeautifulSoup, NavigableString, Tag
import re
from datetime import datetime
from typing import List, Any, Dict, Tuple, Optional

# configuration properties
enableProp = 'enableAISummarization'
enableWhatsAppSummarizationProp = 'enableWhatsAppSummarization' # This is for IPED internal Parser
enableUFEDChatSummarizationProp = 'enableUFEDChatSummarization' # This is for UFED Chat Parser - x-ufed-chat-preview
minimumContentLengthProp = 'minimumContentLength' # Minimum item content length to perform summarization in characters
remoteServiceAddressProp = 'remoteServiceAddress'
configFile = 'AISummarizationConfig.txt'

#--------------------------------------------------------
# Helper functions for the remote service error handling
#-------------------------------------------------------

def _normalize(resp: requests.Response) -> Dict[str, Any]:
    """Standardize into {ok, code, http_status, data?, message?, request_id?}."""
    status = resp.status_code
    try:
        body = resp.json()
    except Exception:
        body = None

    # Pass-through if server already uses the standard contract
    if isinstance(body, dict) and "ok" in body and "http_status" in body:
        return body

    if resp.ok:
        return {
            "ok": True,
            "code": "OK",
            "http_status": status,
            "data": (body if isinstance(body, dict) else {"raw": body}),
        }

    # Ensure message is never None for logs
    msg = None
    if isinstance(body, dict):
        # Common places where a message may live
        msg = (
            body.get("message")
            or (body.get("data") or {}).get("message")
            or (body.get("error") or {}).get("message")
            or (body.get("data") or {}).get("detail")
        )
    if not msg:
        msg = resp.text or f"HTTP {status}"

    return {"ok": False, "code": "HTTP_ERROR", "http_status": status, "message": msg}


def _fmt_error(res: Dict[str, Any]) -> str:
    """
    Build a human-friendly one-liner from a normalized error dict.
    Never returns 'None' for the message; includes request_id when provided.
    """
    code = (res.get("code") or "UNKNOWN").upper()
    http_status = res.get("http_status")
    request_id = res.get("request_id") or (res.get("data") or {}).get("request_id")

    # pick the most informative message available and trim it a bit
    message = (
        res.get("message")
        or (res.get("data") or {}).get("message")
        or (res.get("error") or {}).get("message")
        or (res.get("data") or {}).get("detail")
        or (f"HTTP {http_status}" if http_status else "No message available")
    )
    msg = str(message).strip()
    if len(msg) > 500:
        msg = msg[:497] + "..."

    parts = [code]
    if http_status is not None:
        parts.append(f"({http_status})")
    parts.append(f': "{msg}"')
    if request_id:
        parts.append(f"[request_id={request_id}]")
    return " ".join(parts)


#--------------------------------------------------------
# Remote service communication
#--------------------------------------------------------

def create_summaries_request(
    msgs: list[dict],
    base_url: str = "127.0.0.1:1111",
    *,
    BUSY_SLEEP: float = 1.0,           # fixed sleep for BUSY retries
    NONBUSY_SLEEP: float = 1.0,        # fixed sleep for non-BUSY retries
    MAX_ATTEMPTS_NONBUSY: int = 10,    # max retries for non-BUSY errors
) -> Dict[str, Any]:
    """
    - BUSY (code == 'BUSY'): retry forever, sleeping BUSY_SLEEP each time.
    - Other errors: retry up to MAX_ATTEMPTS_NONBUSY with NONBUSY_SLEEP between attempts.

    NOTE: Logging is improved; logic is unchanged.
    """
    url = f"http://{base_url}/api/create_summaries_from_msgs"
    attempts_other = 0

    while True:
        try:
            resp = requests.post(url, json={"msgs": msgs})
            res = _normalize(resp)
        except requests.exceptions.Timeout:
            res = {
                "ok": False,
                "code": "TIMEOUT",
                "http_status": 408,
                "message": "Request timed out.",
            }
        except requests.exceptions.ConnectionError as e:
            res = {
                "ok": False,
                "code": "CONNECTION_ERROR",
                "http_status": 503,
                "message": str(e),
            }
        except requests.exceptions.RequestException as e:
            res = {
                "ok": False,
                "code": "CLIENT_ERROR",
                "http_status": 500,
                "message": str(e),
            }

        if res.get("ok"):
            return res

        code = (res.get("code") or "").upper()

        # Only BUSY loops forever (unchanged)
        if code == "BUSY":
            logger.info(f"[AISummarizationTask]: BUSY - retrying in {max(0.1, BUSY_SLEEP)}s.")
            time.sleep(max(0.1, BUSY_SLEEP))
            continue

        # Non-BUSY: bounded retries (unchanged)
        attempts_other += 1
        if attempts_other >= MAX_ATTEMPTS_NONBUSY:
            # Final error log with nice formatting
            logger.error(f"[AISummarizationTask]: Error - {_fmt_error(res)}")
            return res

        # Intermediate warning with nice formatting
        logger.warn(f"[AISummarizationTask]: Warning - {_fmt_error(res)}")
        time.sleep(max(0.1, NONBUSY_SLEEP))


#--------------------------------------------------------
# Helper functions for the chat parsing
#--------------------------------------------------------


def _clean_timestamp(raw: str) -> str:
    """
    Normaliza carimbos como "2023-01-08 19:38:20-0300"
    para ISO 8601 ("2023-01-08T19:38:20-03:00").
    Se não conseguir entender, devolve o texto original.
    """
    # First extract just the timestamp part
    #timestamp_pattern = r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} ?[+\-]\d{2}:\d{2}'
    timestamp_pattern = r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?: ?(?:[+\-]\d{2}:?\d{2}|Z))'
    match = re.search(timestamp_pattern, raw)
    
    if match:
        txt = match.group()
    else:
        logger.warn(f'[AISummarizationTask]: Warning - No timestamp found in {raw}')
        return raw
    
    # Now clean the extracted timestamp
    try:
        # "2023-01-08 19:38:20Z"
        dt = datetime.strptime(txt, "%Y-%m-%d %H:%M:%S%z")
        return dt.isoformat()
    except ValueError:
        pass
    try:
        # "2023-01-08 19:38:20 -03:00"
        dt = datetime.strptime(txt, "%Y-%m-%d %H:%M:%S %z")
        return dt.isoformat()
    except ValueError:
        pass
    try:
        # "2023-01-08 19:38:20-0300"
        # normalize "-0300" → "-03:00"
        txt_norm = re.sub(r'([+\-]\d{2})(\d{2})$', r'\1:\2', txt)
        dt = datetime.strptime(txt_norm, "%Y-%m-%d %H:%M:%S%z")
        return dt.isoformat()
    except ValueError:
        logger.warn(f'[AISummarizationTask]: Could not parse timestamp {txt}')
        return txt


def _extract_text_nodes(tag: Tag) -> str:
    """
    Concatena somente os nós-texto “soltos” dentro de *tag*.
    Ignora <br>, <span>, etc.
    """


    parts = [t.strip() for t in tag.contents
             if isinstance(t, NavigableString) and t.strip()]
    return " ".join(parts)

def getMessagesFromChatHTML(html_text: str) -> Tuple[List[Dict], int]:

    html_text = html_text.replace('<br/>', '')
    soup = BeautifulSoup(html_text, "html.parser")
    msgs: list[dict] = []

    len_mgs_content = 0

    for block in soup.select("div.linha"):
        msg_div = block.find("div", class_=["incoming", "outgoing"])

        if msg_div is None:  # linha de sistema / vazia
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
            logger.warn(f"[AISummarizationTask]: Warning - No span timestamp in {msg_div.prettify()}")
            timestamp = None
        else:
            timestamp_raw = timestamp_span.get_text(" ", strip=True)
            timestamp = _clean_timestamp(timestamp_raw) if timestamp_raw else None

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
                "content": content,
                "timestamp": timestamp,
                "direction": direction,
                "name": name,
                "forwarded": forwarded,
                "kind": kind
            }
        )

    return msgs, len_mgs_content




# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class AISummarizationTask:


    def __init__(self):
        self.enabled = False
        self.remoteServiceAddress = None
        self.enableWhatsAppSummarization = False
        self.enableUFEDChatSummarization = False
        self.minimumContentLength = 0
        return

    # Returns if this task is enabled or not. This could access options read by init() method.
    def isEnabled(self):
        return self.enabled

    # Returns an optional list of configurable objects that can load/save parameters from/to config files. 
    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]

    # Do some task initialization, like reading options, custom config files or model.
    # It is executed when application starts by each processing thread on its own class instance.
    def init(self, configuration):
        taskConfig = configuration.getTaskConfigurable(configFile)
        self.enabled = taskConfig.isEnabled()
        if not self.enabled:
            return
        extraProps = taskConfig.getConfiguration()
        self.remoteServiceAddress = extraProps.getProperty(remoteServiceAddressProp)
        if not self.remoteServiceAddress:
            logger.error('[AISummarizationTask]: Error: Task enabled but remoteServiceAddress not set on config file.')
            self.enabled = False
            return
        self.enableWhatsAppSummarization = bool(extraProps.getProperty(enableWhatsAppSummarizationProp))
        self.enableUFEDChatSummarization = bool(extraProps.getProperty(enableUFEDChatSummarizationProp))
        self.minimumContentLength = int(extraProps.getProperty(minimumContentLengthProp) or 0)

        return


    def processChat(self, item):
        from iped.properties import ExtraProperties
        if item.getExtraAttribute(ExtraProperties.SUMMARIES) is not None:
            return

        inputStream = item.getBufferedInputStream()
        try:
            raw_bytes = inputStream.readAllBytes()
        finally:
            inputStream.close()

        chatHtml = bytes(b & 0xFF for b in raw_bytes).decode('utf-8', errors='replace')
        msgs, total_len = getMessagesFromChatHTML(chatHtml)
        if len(msgs) == 0 or total_len < self.minimumContentLength:
            return

        res = create_summaries_request(msgs, self.remoteServiceAddress)
        if not res["ok"]:
            logger.error(f"[AISummarizationTask]: Error {item.getName()} - {res['code']} ({res['http_status']}): {res.get('message')}")
            # Exit - server connection problem
            raise Exception(f"[AISummarizationTask]: Error {item.getName()} - {res['code']} ({res['http_status']}): {res.get('message')}")
            #return

        summaries = res["data"]["summaries"] 

        if len(summaries) == 0:
            logger.error(f"[AISummarizationTask]: Error - No summaries returned for {item.getName()}, this should not happen as we send only messages with content")
            return

        item.setExtraAttribute(ExtraProperties.SUMMARIES, summaries)
        


    
    # Process an Item object. This method is executed on all case items.
    # It can access any method of Item class and store results as a new extra attribute.
    def process(self, item):
        if not self.enabled:
            return

        # WhatsApp chats processed by internal IPED parser
        if self.enableWhatsAppSummarization and "whatsapp-chat" in item.getMediaType().toString():
            self.processChat(item)
            return
        # Process UFED Chats 
        if self.enableUFEDChatSummarization and "x-ufed-chat-preview" in item.getMediaType().toString():
            self.processChat(item)
            return
            



    # Called when task processing is finished. Can be used to cleanup resources.
    # Objects "ipedCase" and "searcher" are provided, so case can be queried for items and bookmarks can be created.
    def finish(self):
        return