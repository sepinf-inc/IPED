package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import com.drew.metadata.Tag;


import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

public class ReportGenerator {
	
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$
    private IItemSearcher searcher;
    public void setSearcher(IItemSearcher s) {
    	searcher=s;
    }
    private String format(String s) {
    	if(s==null || s.isEmpty()){
    		return "-";
    	}
    	return s;
    }
    
    public byte[] genarateContactHtml(Contact contact) throws UnsupportedEncodingException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$

        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + contact.getId() + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "</head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (contact.getAvatar() != null)
            out.println("<img src=\"data:image/jpg;base64," + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(contact.getAvatar()) //$NON-NLS-1$
                    + "\" width=\"112\"/><br>"); //$NON-NLS-1$
        out.println(Messages.getString("TelegramContact.ContactID") + contact.getId()); 
        out.println("<br>" + Messages.getString("TelegramContact.FirstName") + format(contact.getName() )); 
        out.println("<br>" + Messages.getString("TelegramContact.LastName") + format(contact.getLastName() )); 
        out.println("<br>" + Messages.getString("TelegramContact.Username") + format(contact.getUsername())); 
        out.println("<br>" + Messages.getString("TelegramContact.Phone") + format(contact.getPhone()));
        out.println("</body>\n</html>"); //$NON-NLS-1$

        out.flush();
        out.close();

        return bout.toByteArray();
    }
    
	public byte[] generateChatHtml(Chat c,int start,int end)
            throws UnsupportedEncodingException {
        

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8")); //$NON-NLS-1$
        String title=c.getName();
        if(!c.isGroup()) {
        	if(c.getC().getPhone()!=null) 
        		title+=" phone:"+c.getC().getPhone();
        	else if(c.getC().getUsername()!=null)
        		title+=" user:"+c.getC().getUsername();
        }
        
        printMessageFileHeader(out, title, c.getId()+"",c.getC().getAvatar());
        

        int currentMsg=start;
        String lastDate=null;
        if(end>c.getMessages().size()) {
        	end=c.getMessages().size();
        }
        while (currentMsg < end) {
            Message m = c.getMessages().get(currentMsg++);
            String thisDate = dateFormat.format(m.getTimeStamp());
            if (lastDate == null || !lastDate.equals(thisDate)) {
                out.println("<div class=\"linha\"><div class=\"date\">" //$NON-NLS-1$
                        + thisDate + "</div></div>"); //$NON-NLS-1$
                lastDate = thisDate;
            }
            
            printMessage(out, m, c.isGroup());
           
            
        }

        printMessageFileFooter(out);
        out.flush();

        return bout.toByteArray();
    }
	
	
	
	private void printVideo(PrintWriter out, Message message) {
		if(message.getMediaHash()!=null) {
			
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclick","app.open(\"hash:" + message.getMediaHash() + "\")");
			
			link.setAtribute("href", message.getMediaFile());
			
			byte thumb[] = message.getThumb();
            
			if (thumb == null) {
            	List<IItemBase> result = null;
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("hash:"+ message.getMediaHash(),searcher);
            	if(result != null && !result.isEmpty()) {
            		
            		thumb = result.get(0).getThumb();
            	}
            	
            	
            }
			
            
            TagHtml img=new TagHtml("img");
        	img.setAtribute("class", "iped-show");
        	if(thumb!=null) {
        		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
        	}else {
        		img.setAtribute("src",dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/video.png"));
        	}
        	img.setAtribute("width", "100");
        	img.setAtribute("height", "102");
        	img.setAtribute("title","Video");
        	link.getInner().add(img);
        	//link.getInner().add(" teste ");
        	
        	out.println(link.toString());
        	out.println("<br/>");
			
        	TagHtml video=new TagHtml("video");
			video.setAtribute("class", "iped-hide");
			video.setAtribute("controls", null);
        	video.getInner().add("<source src=\"" + message.getMediaFile() + "\"/>");
			out.println(video.toString());
			out.println("<br/>");
			
			
			
		}else {
			TagHtml img=new TagHtml("img");
						 
			 String msg="";
			 if(message.getThumb()!=null) {
				 msg="thumb"; 
				 img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(message.getThumb()));
				 
			 }else {
				 img.setAtribute("src",dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/video.png"));
			 }
			 img.setAtribute("width", "100");
			 img.setAtribute("height", "102");
			 img.setAtribute("title", "Video");
			 out.println(img.toString());
			 out.println(msg);
		}
		
	}
	
	
	private void printAudio(PrintWriter out, Message message) {
		TagHtml img=new TagHtml("img");
		
    	
    	img.setAtribute("src", dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/audio.png"));
    	
    	img.setAtribute("width", "100");
    	img.setAtribute("height", "102");
    	img.setAtribute("title","Video");
    	
		if(message.getMediaHash()!=null) {
			
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclick","app.open(\"hash:" + message.getMediaHash() + "\")");
			link.setAtribute("href", message.getMediaFile());
			           
			img.setAtribute("class", "iped-show");
        	link.getInner().add(img);
        	//link.getInner().add(" teste ");
        	
        	out.println(link.toString());
        	out.println("<br/>");
			
        	TagHtml audio=new TagHtml("audio");
        	audio.setAtribute("class", "iped-hide");
			audio.setAtribute("controls", null);
			audio.getInner().add("<source src=\"" + message.getMediaFile() + "\"/>");
			out.println(audio.toString());
			
			
			
			
		}else {
			
			out.println(img.toString());
		}
		
	}
	
	private void printImage(PrintWriter out, Message message) {
		if(message.getMediaHash()!=null) {
			TagHtml link=new TagHtml("a");
			link.setAtribute("onclick","app.open(\"hash:" + message.getMediaHash() + "\")");
			link.setAtribute("href", message.getMediaFile());
			
			byte thumb[] = message.getThumb();
	      
			if (thumb == null) {
	        	List<IItemBase> result = null;
	        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("hash:"+ message.getMediaHash(),searcher);
	        	if(result != null && !result.isEmpty()) {
	        		
	        		thumb = result.get(0).getThumb();
	        	}
	        	
	        	
	        }
			
	        
	        TagHtml img=new TagHtml("img");
	    	//img.setAtribute("class", "iped-show");
	    	if(thumb!=null) {
	    		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
	    	}
	    	img.setAtribute("width", "100");
	    	img.setAtribute("height", "102");
	    	img.setAtribute("title","Image");
	    	link.getInner().add(img);
	    	//link.getInner().add(" teste ");
	    	
	    	
	    	out.println(link.toString());
	    			
		}else {
			 TagHtml img=new TagHtml("img");
			 img.setAtribute("src", dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/image.png"));
			 img.setAtribute("width", "100");
			 img.setAtribute("height", "102");
			 img.setAtribute("title","Image");
			 out.println(img.toString());
		}
		out.println("<br/>");
		
	}
	
	private void printLink(PrintWriter out, Message message) {
		out.print("link<br/>");
		if(message.getLinkImage()!=null) {
					
			byte thumb[] = message.getLinkImage();
	      
				        
	        TagHtml img=new TagHtml("img");
	    	
	    	if(thumb!=null) {
	    		img.setAtribute("src", "data:image/jpg;base64,"+dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(thumb));
	    	}
	    	img.setAtribute("width", "100");
	    	img.setAtribute("height", "102");
	    	img.setAtribute("title","Link image");
	    		    	    	
	    	
	    	out.println(img.toString());
	    	out.println("<br/>");
	    			
		}
		
		
	}
	
	
	private void printMessage(PrintWriter out, Message message, boolean group) {
		out.println("<div class=\"linha\" id=\"" + message.getId() + "\">"); //$NON-NLS-1$
		if (message.isFromMe()) {
            out.println("<div class=\"outgoing to\">"); //$NON-NLS-1$
        } else {
            out.println("<div class=\"incoming from\">"); //$NON-NLS-1$
            Contact contact = message.getRemetente();
            if(contact!=null) {
                
                
                
                
               
                String number = contact.getPhone();
                String name = contact.getName();
                if (name == null)
                    name = "";
                if(number!=null && number.length()>0) {
                	name += " (phone: " + number + ")";
                }else {
                	name+= " (ID:"+contact.getId()+" )";
                }
                     
                out.println("<span style=\"font-family: 'Roboto-Medium'; color: #b4c74b;\">" //$NON-NLS-1$
                        + name + "</span><br/>"); //$NON-NLS-1$
                
            }
        }
		if(message.getMediaMime()!=null) {
			if(message.getMediaMime().toLowerCase().startsWith("video")) {
				printVideo(out, message);
			}
			if(message.getMediaMime().toLowerCase().startsWith("image")) {
				printImage(out, message);
				
			}
			if(message.getMediaMime().toLowerCase().startsWith("audio")) {
				printAudio(out,message);
			}
			if(message.getMediaMime().toLowerCase().startsWith("link")) {
				printLink(out,message);
			}
			
		}
		if (message.getData() != null) {
            out.print(message.getData()); //$NON-NLS-1$
        }else {
        	if(message.getType()!=null)
        		out.print(message.getType());
        }
		out.println("<br/>");
		
		out.println("<span class=\"time\">"); //$NON-NLS-1$
        out.println(timeFormat.format(message.getTimeStamp()) + " &nbsp;"); //$NON-NLS-1$ 
        out.println("</span>"); //$NON-NLS-1$
        
        out.println("</div></div>"); //$NON-NLS-1$
		
	}
	
	
	private static void printMessageFileHeader(PrintWriter out, String title, String id,byte[] avatar) {
        out.println("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "	<title>" + id + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" //$NON-NLS-1$
                + "	<meta name=\"viewport\" content=\"width=device-width\" />\n" //$NON-NLS-1$
                + "     <meta charset=\"UTF-8\" />\n" //$NON-NLS-1$
                + "	<link rel=\"shortcut icon\" href=\"" + dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/favicon.ico")+ "\" />\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "<style>\n"
                + dpf.mg.udi.gpinf.whatsappextractor.Util.readResourceAsString("css/whatsapp.css") //$NON-NLS-1$
                + "\n</style>\n"
                + "<script>\n" //$NON-NLS-1$
                + "var css = document.createElement(\"style\");\n" //$NON-NLS-1$
                + "css.type = \"text/css\";\n" //$NON-NLS-1$
                + "var inHtml = \"\";\n" //$NON-NLS-1$
                + "if (navigator.userAgent.search(\"JavaFX\") >= 0) {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: none; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: block; }\";\n" //$NON-NLS-1$
                + "} else {\n" //$NON-NLS-1$
                + "  inHtml = \".iped-hide { display: block; }\";\n" //$NON-NLS-1$
                + "  inHtml += \".iped-show { display: none; }\";\n" //$NON-NLS-1$
                + "}\n" //$NON-NLS-1$
                + "css.innerHTML = inHtml;\n" //$NON-NLS-1$
                + "document.head.appendChild(css);\n" //$NON-NLS-1$
                + "function openIfExists(url1, url2){\r\n" + "    var img1 = new Image();\r\n"
                + "    img1.onload = () => window.location = url1;\r\n"
                + "    img1.onerror = () => window.location = url2;\r\n" + "    img1.src = url1;\r\n" + "}\r\n"
                + "</script>\n" //$NON-NLS-1$
                + dpf.mg.udi.gpinf.vcardparser.VCardParser.HTML_STYLE + "</head>\n" //$NON-NLS-1$
                + "<style>.check {vertical-align: top;}</style>"
                + "<body style='background-image:url("+dpf.mg.udi.gpinf.whatsappextractor.Util.getImageResourceAsEmbedded("img/telegramwallpaper.jpg")+")'>\n" //$NON-NLS-1$
                + "<div id=\"topbar\" class='telegram'>\n" //$NON-NLS-1$
                + "	<span class=\"left\">" //$NON-NLS-1$
                + " &nbsp; "); //$NON-NLS-1$
        
        if (avatar != null) {
            out.println("<img src=\"data:image/jpg;base64," + dpf.mg.udi.gpinf.whatsappextractor.Util.encodeBase64(avatar) //$NON-NLS-1$
                    + "\" width=\"40\" height=\"40\"/>"); //$NON-NLS-1$
            
        }
        out.println(title + "</span>\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"conversation\">\n" //$NON-NLS-1$
                + "<br/><br/><br/>"); //$NON-NLS-1$
    }

    private static void printMessageFileFooter(PrintWriter out) {
        out.println("	<br /><br /><br />\n" //$NON-NLS-1$
                + "</div>\n" //$NON-NLS-1$
                + "<div id=\"lastmsg\">&nbsp;</div>\n" //$NON-NLS-1$
                + "</body>\n" //$NON-NLS-1$
                + "</html>"); //$NON-NLS-1$
    }

}
