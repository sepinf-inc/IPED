import requests
#need to install requests lib: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py requests
#also numpy for some reason: .\target\release\iped-4.3.0-snapshot\python\python.exe .\target\release\iped-4.3.0-snapshot\python\get-pip.py "numpy<2.0" 
import json



# configuration properties
enableProp = 'enableAISummarization'
enableWhatsAppSummarizationProp = 'enableWhatsAppSummarization' # This is for IPED internal Parser
enableUFEDChatSummarizationProp = 'enableUFEDChatSummarization' # This is for UFED Chat Parser - x-ufed-chat-preview
configFile = 'AISummarizationConfig.txt'
remoteServiceAddressProp = 'remoteServiceAddress'


def create_summaries_request(chat_content: str, base_url: str = "127.0.0.1:1111"):
    """
    Sends a POST request to the /api/create_summaries endpoint with chat content.

    Args:
        chat_content (str): The chat content to be summarized.
        base_url (str): The base URL, defaults to "http://localhost:1111" for local development.

    Returns:
        dict: The JSON response from the API, or an error message.
    """
    url = f"http://{base_url}/api/create_summaries"
    headers = {"Content-Type": "application/json"}
    payload = {"chat_content": chat_content}

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




# The main class name must be equal to the script file name without .py extension
# One instance of this class is created by each processing thread and each thread calls the implemented methods of its own object.
class AISummarizationTask:

    enabled = False
    remoteServiceAddress = None
    enableWhatsAppSummarization = False
    enableUFEDChatSummarization = False
    
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
        
        result = create_summaries_request(chatHtml, AISummarizationTask.remoteServiceAddress)
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