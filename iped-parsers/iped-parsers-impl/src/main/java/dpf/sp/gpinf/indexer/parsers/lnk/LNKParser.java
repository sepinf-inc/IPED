package dpf.sp.gpinf.indexer.parsers.lnk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class LNKParser {
	
	private static int MAX_SIZE = 1 << 20;
	 
	public static LNKShortcut parseFromStream (InputStream stream) throws Exception {

    	LNKShortcut lnkObj = new LNKShortcut();
    	
        try {
            byte[] block = new byte[MAX_SIZE];
            int read = 0, len = 0;
            while (read != -1 && len < MAX_SIZE) {
            	len += read;
                read = stream.read(block, len, MAX_SIZE - len);
            }
            if(len == MAX_SIZE && read != -1)
            	throw new IOException("File is larger than expected"); //$NON-NLS-1$
            
            byte[] b = new byte[len];
            System.arraycopy(block, 0, b, 0, len);
            
            parseHeader(b, lnkObj); // 0 ao 76
            
            int offset = 0;
            if (lnkObj.hasTargetIDList()) {
                // HasTargetIDList
            	offset = parseTargetIDList(b, lnkObj);
            } else {
            	offset = 76;
            }
            
            // HasLinkInfo
            if (lnkObj.hasLinkLocation()) {
            	offset = parseLinkLocation(b, offset, lnkObj);
            }

            // HasName  HasRelativePath   HasWorkingDir  HasArguments    HasIconLocation
            if (lnkObj.hasName() || lnkObj.hasRelativePath() || lnkObj.hasWorkingDir() || lnkObj.hasArguments() || lnkObj.hasIconLocation()) {
            	offset = parseDataStrings(b, offset, lnkObj);
            }           
            
            // Extra data blocks - "distributed link tracker properties"
            offset = parseExtraData(b, offset, lnkObj);

            // TODO - verificar outros blocos de possível interesse na seção "Extra Data" 

            
		} catch (Exception e) {
			//e.printStackTrace();
			throw e;
		}
        return lnkObj;
	}

	private static void parseHeader(byte[] b, LNKShortcut lnkObj) throws Exception {
		int sizeHeader = toInt(b, 0);
        if (sizeHeader != 0X4c) {
        	// erro no arquivo de atalho
        	throw new Exception("Header size (" + sizeHeader + ") unsupported."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        lnkObj.setHeaderSize(sizeHeader);

        //String lnkClsId = toHex(b, 4, 16);
		lnkObj.setCreateDate(toLong(b, 28));
		lnkObj.setAccessDate(toLong(b, 36));
		lnkObj.setModifiedDate(toLong(b, 44));
		lnkObj.setFileSize(toLongInt(b, 52));
	    
		lnkObj.setFileAttributeFlags(toInt(b, 24));
		lnkObj.setDataLinkFlags(toInt(b, 20));  
		lnkObj.setIconIndex(toInt(b, 56));
		lnkObj.setShowWindow(toInt(b, 60));
		lnkObj.setHotKey(toSmall(b, 64));
	    
        //Segundo a documentação, o espaço do header que vai do offset 66 ao 76 consta como RESERVED  
        //66  2 0 Reserved
        //68  4 0 Reserved
        //72  4 0 Reserved
	}
    
	private static int parseTargetIDList(byte[] b, LNKShortcut lnkObj) throws Exception {
        int sizeBlock = toSmall(b, 76);

        int posBlock = 78;
        if (sizeBlock + posBlock > b.length) {
        	// erro no arquivo de atalho
        	throw new Exception("TargetID list size exceeds lnk size"); //$NON-NLS-1$
        }

        int endBlock = 78 + sizeBlock;
        while (posBlock < endBlock) {
        	boolean unparsed = false;
        	
        	int sizeItem = toSmall(b, posBlock);
        	if (sizeItem <= 0) {
        		posBlock+=2;
        		break; // terminal identifier = 0
        	}
        	
        	LNKShellItem objItem = new LNKShellItem();
        	
        	// lendo o tipo do item
        	String tipStr = toHex(b, posBlock+2, 1);
        	byte btInt = (byte) Integer.parseInt(tipStr, 16); //Byte.decode("0x" + tipStr);
        	objItem.setType(btInt);
        	String nmItem = null, vlItem = null;
        	if (btInt == 0X1F) {
        		// CLSID_ShellDesktop - Root folder shell item
            	String guidStr = toGUID(b, posBlock+4);
            	nmItem = "Root Folder Shell - CLSID"; //$NON-NLS-1$
            	vlItem = guidStr;
       		} else if ((btInt & 0X70) == 0X20) {
        		// 0x20 – 0x2f : CLSID_MyComputer - Volume shell item
        		// verificando flag Has Name
       			if ((btInt & 0X01) == 1) {
       				// has name
       				String hexStr = toStr(b, posBlock+3, sizeItem-3, (byte) 0X00);
       				nmItem = "Volume Shell - \'Has Name\'"; //$NON-NLS-1$
       				vlItem = hexStr;
       			} else if ((btInt == 0X2e) && sizeItem > 20) {
   					// delegate shell item - nao ficou claro na documentação se este item pode estar aí
       				nmItem = "Volume Shell - Delegate CLSID"; //$NON-NLS-1$
      				       					
   					int posTmp = posBlock+4; // tamanho (2), tipo (1) e unknown (1)
   					int delegSize = toSmall(b, posTmp);
   					
   					posTmp += (delegSize+2);
   					
   					// delegate item identifier
   					objItem.addValue(toGUID(b, posTmp));
   					// shell foder identifier
   					vlItem = toGUID(b, posTmp+16);
       			} else {
       				if (sizeItem == 20) {
       					String hexStr = toGUID(b, posBlock+4);
       					nmItem = "Volume Shell - CLSID"; //$NON-NLS-1$
       					vlItem = hexStr;
       				} else {
       					String hexStr = toHex(b, posBlock+3, sizeItem-3);
       					nmItem = "Volume Shell - Unparsed"; //$NON-NLS-1$
       					vlItem = hexStr;
       					unparsed = true;
       				}
       			}	        	
       		} else if ((btInt & 0X70) == 0X30) {
        		// 0x30 – 0x3f : CLSID_ShellFSFolder - File entry shell item
       			boolean hasUnicode = ((btInt & 0X04) != 0);  // flag: has unicode strings
       				
       			int posTmp = posBlock+4;
       			long flSize = toLongInt(b, posTmp);
       			posTmp += 4;
       			long modDate = toLong(b, posTmp);
       			posTmp += 4;
       			int flAtt = toSmall(b, posTmp);
       			posTmp += 2;
       			int fimNome = findEndStr(b, posTmp, sizeItem-14, hasUnicode);
       			String pName = ""; //$NON-NLS-1$
       			if (fimNome > 0) {
       				if (hasUnicode) {
       					pName = toStr(b, posTmp, fimNome, "UTF-16LE"); //$NON-NLS-1$
       					fimNome++; // tem um byte zero adicional no final da string pois é 16-bit aligned
       				} else {
       					pName = toStr(b, posTmp, fimNome);
       	       			if (pName.length() % 2 == 0) {
       	       				fimNome++; // tem um byte zero adicional no final da string pois é 16-bit aligned
       	       			}
       				}
       			}
       			objItem.setFileEntry(flSize, modDate, flAtt, pName);
  				posTmp += (fimNome+1); 

       			// verificando por extension block 
       			posTmp += 4;
       			String hexSig = toHex(b, posTmp, 4);
       			
       			if (hexSig.equals("0400efbe")) { //$NON-NLS-1$
       				parseExtensionBlock(b, posTmp, objItem);
       				//if (!fEntry.hasClassID()) {
       					// possiveis outras extensoes 0xbeef0005, 0xbeef0006 and 0xbeef001a
       				//} else {
       					// extension 0xbeef0003
       				//}
       			} else { // SolidWorks ou Pre XP ? ou outro tipo nao tratado
       				posTmp -= 4;
       				String strUnparsed = toHex(b, posTmp, sizeItem-posTmp); 
       				LNKShellItemFileEntry fEntry = objItem.getFileEntry();
       				fEntry.setUnknown(strUnparsed);
       			}
       			
        		String hexStr = toHex(b, posBlock+3, sizeItem-3); 
        		nmItem = "File Entry Shell"; //$NON-NLS-1$
        		vlItem = hexStr;
	        	
       		} else if ((btInt & 0X70) == 0X40) {
        		// 0x40 – 0x4f: CLSID_NetworkRoot CLSID_NetworkPlaces -  Network location shell item
       			int offSet = 4; // tamanho (2), tipo (1) e unknown (1)
       			
        		// verificando flag Has Name
       			LNKShellItemNetwork netLoc = new LNKShellItemNetwork();
       			netLoc.setTypeClass(btInt);
       			
       			byte netFlags = b[posBlock+offSet];
       			
       			netLoc.setFlags(netFlags);
       			offSet++;
       			String strLoc = toStr(b, posBlock+offSet, sizeItem-offSet, (byte) 0X00);
       			if (strLoc != null) {
       				offSet += strLoc.length() + 1;
       				netLoc.setLocation(strLoc);
       			}
       			if ((netFlags & 0X80) == 0X80) {
       				// has description
       				String strDesc = toStr(b, posBlock+offSet, sizeItem-offSet, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
           			if (strDesc != null) {
           				netLoc.setDescription(strDesc);
           				offSet += strDesc.length() + 1;
           			}       				
       			} 
           		if ((netFlags & 0X40) == 0X40) {
       				// has comments
       				String strComm = toStr(b, posBlock+offSet, sizeItem-offSet, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
       				netLoc.setComments(strComm);
       			}
       			objItem.setNetworkLocation(netLoc);
       			
        		String hexStr = toHex(b, posBlock+3, sizeItem-3); 
        		nmItem = "Network Location Shell"; //$NON-NLS-1$
        		vlItem = hexStr;
	        	
       		} else if (btInt == 0X52) {
       			// Compressed folder shell item
       			// TODO
        		String hexStr = toHex(b, posBlock+3, sizeItem-3); 
        		nmItem = "Compressed Folder Shell - Unparsed"; //$NON-NLS-1$
        		vlItem = hexStr;
				unparsed = true;

       		} else if (btInt == 0X61) {
        		// CLSID_Internet -  URI shell item
       			int offSet = 3; // tamanho (2), tipo (1)
       			
       			byte urlFlags = b[posBlock+offSet];
       			boolean unicode = ((urlFlags & 0X80) == 0X80); // URI in Unicode
       			offSet++;
       			
       			int sizeData = toSmall(b, posBlock + offSet);
       			offSet += 2;       			
       			if (sizeData > 0) {
           			offSet += 36; //4(Unknown) + 4(Unknown) + 8(Unknown Timestamp) + 4(Unknown) + 12(Unknown) + 4(Unknown)       			

           			// Tres blocos com informacoes de strings, conforme:
           			// 4   StringN data size - Value in bytes 
           			// ... StringN data ASCII ou UTF16le
           			
           			for (int s=1; s<4; s++) {
           				// lendo cada uma das 3 strings 
           				int sizeStr = toInt(b, posBlock + offSet);
           				offSet+=4;
           				if (sizeStr > 0) {
	               			int fimStr = findEndStr(b, posBlock+offSet, sizeItem-offSet, unicode);
	           				String sName;
	           				if (unicode) {
	           					sName = toStr(b, posBlock+offSet, fimStr, "UTF-16LE"); //$NON-NLS-1$
	           				} else {
	           					sName = toStr(b, posBlock+offSet, fimStr);
	           				}
	           				if (sName != null && !sName.trim().equals("")) //$NON-NLS-1$
	           					objItem.addValue(sName);
           				}
               			offSet += sizeStr;       			
           			}
       			} else {
           			offSet += 2; // possivel tamanho ?
       			}
       			
       			int fimStr = findEndStr(b, posBlock+offSet, sizeItem-offSet, unicode);
       			String uName;
   				if (unicode) {
   					uName = toStr(b, posBlock+offSet, fimStr, "UTF-16LE"); //$NON-NLS-1$
   					fimStr++; // tem um byte zero adicional no final da string pois é 16-bit aligned
   				} else {
   					uName = toStr(b, posBlock+offSet, fimStr);
   	       			if (uName.length() % 2 == 0) {
   	       				fimStr++; // tem um byte zero adicional no final da string pois é 16-bit aligned
   	       			}
   				}
   				// Possível Extension block 0xbeef0014 - Parser não efetuado para o bloco
   				offSet += (fimStr+1);
   				
        		nmItem = "URL Shell"; //$NON-NLS-1$
        		vlItem = uName;
        		
       		} else if (btInt == 0X74) {
	        	// UsersFilesFolder descrição desconhecida, mas procurando extensão 0xbeef0004
       			nmItem = "Unknown Shell - Data (0x74)"; //$NON-NLS-1$
       			String hexStr = toHex(b, posBlock+3, sizeItem-3);
       			int posExt = hexStr.indexOf("0400efbe");  //$NON-NLS-1$
       			if (posExt > 0) {
       				posExt = posBlock + 3 + (posExt / 2);
       				parseExtensionBlock(b, posExt, objItem);
       			}
       			
       		} else {
        		// outro tipo de item
        		String hexStr = toHex(b, posBlock+3, sizeItem-3); // tamanho do item inclui o proprio tamanho do item (2 bytes) e o tipo do item (1 byte)
        		nmItem = "Unknown Shell - Unparsed (0x" + tipStr + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        		vlItem = hexStr;
				unparsed = true;
        	}
        	posBlock = posBlock + sizeItem;
        	objItem.setName(nmItem);
        	objItem.addValue(vlItem);
        	objItem.setUnparsed(unparsed);
        	lnkObj.addShellTargetID(objItem);
        }
		return posBlock; // offset
	}

	private static int parseLinkLocation(byte[] b, int offset, LNKShortcut lnkObj) throws Exception {
		int offtmp = offset;
		
        int sizeBlock = toInt(b, offtmp);
        offtmp+=4;
        int sizeHeader = toInt(b, offtmp);
        if (sizeBlock + offset > b.length) 
        	throw new Exception("LinkLocation section size (" + sizeBlock + ") exceeds lnk size (" + b.length + ")."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        if (sizeHeader < 28) 
        	throw new Exception("LinkLocation section header size (" + sizeHeader + ") unsupported."); //$NON-NLS-1$ //$NON-NLS-2$
        
        LNKLinkLocation objItem = new LNKLinkLocation();
        
        offtmp+=4;
        int locFlags = toInt(b, offtmp);
        objItem.setFlagsLocation(locFlags);

        if ((locFlags & 0x0001) == 0x0001) { // Flag 0x0001 - VolumeIDAndLocalBasePath - The linked file is on a volume. If set the volume information and the local path contain data
	        // Volume Information
			offtmp+=4;
	        int iniVol = toInt(b, offtmp); // offset to volume information
	        if (iniVol > 0 && iniVol < sizeBlock) {
		        iniVol += offset;
		        int sizeTmp = toInt(b, iniVol);
		        if (sizeTmp > sizeBlock) 
		        	throw new Exception("LinkLocation Volume Information size (" + sizeTmp + ") exceeds section size (" + sizeBlock + ")."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		        objItem.setDriveType(toInt(b, iniVol+4));
		        objItem.setDriveSerial(toHexLE(b, iniVol+8, 4));
		        int offVolLabel = toInt(b, iniVol+12);
		        if (offVolLabel > 16) {
		        	int offVolLabelUni = toInt(b, iniVol+16);
		        	// lendo o volume label em unicode
	       			int fimStr = findEndStr(b, iniVol + offVolLabelUni, sizeTmp - 20, true);
	       			if (fimStr > 0) {
		       			String uName = toStr(b, iniVol + offVolLabelUni, fimStr, "UTF-16LE"); //$NON-NLS-1$
			        	objItem.setVolumeLabelUnicode(uName);
	       			}
		        }
				String strTmp = toStr(b, iniVol + offVolLabel, sizeTmp - 16, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
				if (strTmp != null) {
					objItem.setVolumeLabel(strTmp);
				}
	        }
	        
	        // Local Path
	        offtmp+=4;
	        int iniLocPath = toInt(b, offtmp); // offset to local path
			String strTmp = toStr(b, offset + iniLocPath, sizeBlock - iniLocPath, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
			if (strTmp != null) {
				objItem.setLocalPath(strTmp);
			}
        } else {
        	offtmp+=8;
        }

        // Network Share Information
        offtmp+=4;
        int iniNet = toInt(b, offtmp); // offset to Network Share Information
        if (iniNet > 0 && iniNet < sizeBlock) {
        	iniNet += offset;
	        int sizeTmp = toInt(b, iniNet);
	        if (sizeTmp > sizeBlock) 
	        	throw new Exception("LinkLocation Network Share Information size (" + sizeTmp + ") exceeds section size (" + sizeBlock + ")."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	        int netFlags = toInt(b, iniNet+4);

	        int offNetShare = toInt(b, iniNet+8);
	        if (offNetShare < sizeTmp) {
				String strTmp = toStr(b, iniNet + offNetShare, sizeTmp - 20, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
				if (strTmp != null) {
					objItem.setNetShare(strTmp);
				}
	        }
			
	        if ((netFlags & 0x00000001) == 0x00000001) { // Flag 0x0001 - ValidDevice - If set the device name contains data
		        int offNetDevice = toInt(b, iniNet+12);
		        if (offNetDevice < sizeTmp) {
					String strTmp = toStr(b, iniNet + offNetDevice, sizeTmp - 21, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
					if (strTmp != null) {
						objItem.setNetDevName(strTmp);
					}
		        }
	        }
	        
	        if ((netFlags & 0x00000002) == 0x00000002) { // Flag 0x0002 - ValidNetType - If set the network provider type contains data
	        	objItem.setNetProviderType(toInt(b, iniNet+16));
	        }
	        
	        if (offNetShare > 20) {
	        	// offset do share name em unicode
	        	offNetShare = toInt(b, iniNet+20);
	        	if (offNetShare > 0) {
		        	// lendo o share name em unicode
	       			int fimStr = findEndStr(b, iniNet + offNetShare, sizeTmp - 28, true);
	       			if (fimStr > 0) {
		       			String uName = toStr(b, iniNet + offNetShare, fimStr, "UTF-16LE"); //$NON-NLS-1$
			        	objItem.setNetShareUnicode(uName);
	       			}
	        	}
	        	
	        	// offset do device name em unicode
	        	int offNetDevice = toInt(b, iniNet+24);
	        	if (offNetDevice > 0) {
		        	// lendo o device name em unicode
	       			int fimStr = findEndStr(b, iniNet + offNetDevice, sizeTmp - 28, true);
	       			if (fimStr > 0) {
		       			String uName = toStr(b, iniNet + offNetDevice, fimStr, "UTF-16LE"); //$NON-NLS-1$
			        	objItem.setNetDevNameUnicode(uName);
	       			}
	        	}	        	
	        }
        }
        
        // Common Path
        offtmp+=4;
        int iniCom = toInt(b, offtmp); // offset to Common Path
		String strTmp = toStr(b, offset + iniCom, sizeBlock - iniCom, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
		if (strTmp != null) {
			objItem.setCommonPath(strTmp);
		}
        
    	// Unicode local path 
        if (sizeHeader > 28 && ((locFlags & 0x0001) == 0x0001)) { 
        	// Offset to the Unicode local path
        	offtmp+=4;
        	if ((locFlags & 0x0001) == 0x0001) { //Flag 0x0001 - VolumeIDAndLocalBasePath - The linked file is on a volume. If set the volume information and the local path contain data
	            int iniLocPath = toInt(b, offtmp); // offset to Unicode local path
	   			int fimStr = findEndStr(b, offset + iniLocPath, sizeBlock - iniLocPath, true);
	   			if (fimStr > 0) {
	       			String uName = toStr(b, offset + iniLocPath, fimStr, "UTF-16LE"); //$NON-NLS-1$
		        	objItem.setLocalPathUnicode(uName);
	   			}
        	}
        }
        //Unicode common path
        if (sizeHeader > 32) {
        	// Offset to the Unicode common path
        	offtmp+=4;
        	iniCom = toInt(b, offtmp); // offset to Unicode common path
   			int fimStr = findEndStr(b, offset + iniCom, sizeBlock - iniCom, true);
   			if (fimStr > 0) {
       			String uName = toStr(b, offset + iniCom, fimStr, "UTF-16LE"); //$NON-NLS-1$
	        	objItem.setCommonPathUnicode(uName);
   			}
        }
        
        lnkObj.setLinkLocation(objItem);
        
        return offset + sizeBlock; // offset
	}

	private static int parseDataStrings(byte[] b, int offset, LNKShortcut lnkObj) throws Exception {
		int offtmp = offset;
		boolean unicode = lnkObj.isUnicode();
		int sizeStr = 0;
		
		if (lnkObj.hasName()) {
			sizeStr = toSmall(b, offtmp);
			offtmp+=2;
   			String strName = toStr(b, offtmp, sizeStr, unicode);
			if (unicode) {
				offtmp+=sizeStr*2;
			} else {
				offtmp+=sizeStr;
			}
			lnkObj.setDescription(strName);
		}

		if (lnkObj.hasRelativePath()) {
			sizeStr = toSmall(b, offtmp);
			offtmp+=2;
   			String strName = toStr(b, offtmp, sizeStr, unicode);
			if (unicode) {
				offtmp+=sizeStr*2;
			} else {
				offtmp+=sizeStr;
			}
			lnkObj.setRelativePath(strName);
		}

		if (lnkObj.hasWorkingDir()) {
			sizeStr = toSmall(b, offtmp);
			offtmp+=2;
   			String strName = toStr(b, offtmp, sizeStr, unicode);
			if (unicode) {
				offtmp+=sizeStr*2;
			} else {
				offtmp+=sizeStr;
			}
			lnkObj.setWorkingDir(strName);
		}

		if (lnkObj.hasArguments()) {
			sizeStr = toSmall(b, offtmp);
			offtmp+=2;
   			String strName = toStr(b, offtmp, sizeStr, unicode);
			if (unicode) {
				offtmp+=sizeStr*2;
			} else {
				offtmp+=sizeStr;
			}
			lnkObj.setCommandLineArgs(strName);
		}

		if (lnkObj.hasIconLocation()) {
			sizeStr = toSmall(b, offtmp);
			offtmp+=2;
   			String strName = toStr(b, offtmp, sizeStr, unicode);
			if (unicode) {
				offtmp+=sizeStr*2;
			} else {
				offtmp+=sizeStr;
			}
			lnkObj.setIconLocation(strName);
		}
		
        return offtmp; // offset
	}

	private static int parseExtraData(byte[] b, int offset, LNKShortcut lnkObj) throws Exception {
		int offtmp = offset;
		
		String hexStr = toHex(b, offset, b.length - offset - 87);
		if (hexStr == null) 
			return offtmp;
		
		int posExt = hexStr.indexOf("60000000030000a058000000"); // tamanho da secao (96), assinatura e tamanho dos dados (88) - distributed link tracker properties //$NON-NLS-1$
		while (posExt > 0) {
			offtmp = parseLinkTrackerProperties(b, offset+(posExt/2), lnkObj);
			posExt = hexStr.indexOf("60000000030000a058000000", offset + posExt + 12);  //$NON-NLS-1$
		}
		
		return offtmp;
	}

	private static int parseLinkTrackerProperties(byte[] b, int offset, LNKShortcut lnkObj) throws Exception {
		int offtmp = offset;
		
		int sizeBlock = toInt(b, offtmp);
		String hexSig = toHex(b, offtmp+4, 4);
		int sizeTmp = toInt(b, offtmp+8);
		int verBlock = toInt(b, offtmp+12);
		if (sizeBlock != 96 || hexSig == null || !(hexSig.equals("030000a0")) || sizeTmp != 88 || verBlock != 0) //$NON-NLS-1$
			return offtmp; // bloco incorreto

		LNKLinkTracker lnkTracker = new LNKLinkTracker();
		offtmp+=16; // inicio dos dados
		
		// Machine identifier string - ASCII string terminated by an end-of-string character - Unused bytes are set to 0
		String strName = toStr(b, offtmp, 16, (byte) 0X00, "ISO8859_1"); //$NON-NLS-1$
		lnkTracker.setMachineId(strName);
		
		// Droid volume identifier (GUID)
		offtmp+=16;
		lnkTracker.setDroidVolumeId(toGUID(b, offtmp));
		
		// Droid file identifier  (GUID)
		offtmp+=16;
		lnkTracker.setDroidFileId(toGUID(b, offtmp));
		
		// Birth droid volume identifier (GUID)
		offtmp+=16;
		lnkTracker.setBirthDroidVolumeId(toGUID(b, offtmp));
		
		// Birth droid file identifier (GUID)
		offtmp+=16;
		lnkTracker.setBirthDroidFileId(toGUID(b, offtmp));
		
		lnkObj.setLinkTracker(lnkTracker);
		return offtmp;
	}
	
	public static void parseExtensionBlock(byte[] b, int posSig, LNKShellItem objItem) throws Exception {
		int sizeExt = toSmall(b, posSig - 4);
		
		int verExt = toSmall(b, posSig - 2); // 3: Win XP or 2003; 7: Win Vista (SP0); 8: Win 2008, 7, 8.0; 9: Win 8.1, 10
		String hexSig = toHex(b, posSig, 4);
		if (hexSig.equals("0400efbe")) { //$NON-NLS-1$
		
			LNKShellItemFileEntry fEntry = objItem.getFileEntry();
	
			// extension block 0xbeef0004
			fEntry.addExtensionSig("0xbeef0004 (ver " + verExt + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			int posTmp = posSig + 4;
			
			// creation date: FAT date time em UTC
			long crDate = toLong(b, posTmp);
			fEntry.setCreateDate(crDate);
			
			posTmp += 4;
			// last access date: FAT date time em UTC
			long acDate = toLong(b, posTmp);
			fEntry.setAccessDate(acDate);
			
			posTmp += 6; // last access (4) e unknown (2)  
			
			if (verExt >= 7) {
				posTmp += 2; // 2(unknown)
				long indMft = toInt6(b, posTmp);
				int seqNum = toSmall(b, posTmp+6);
				if (indMft > 0) {
					fEntry.setNtfsRef("MFT Entry Idx " + String.valueOf(indMft) + " - Seq.Numb. " + String.valueOf(seqNum)); //$NON-NLS-1$ //$NON-NLS-2$
				}
				posTmp += 16; // 8(NTFS file reference) + 8(unknown)
			}
			
			int lngStrSize = 0;
			if (verExt >= 3) {
				lngStrSize = toSmall(b,posTmp);
				posTmp += 2; // long string size
			}
	
			if (verExt >= 9)
				posTmp += 4; // bytes desconsiderados: 4(unknown)
			
			if (verExt >= 8)
				posTmp += 4; // bytes desconsiderados: 4(unknown)
	
			if (verExt >= 3) {
				int fimNomeSec = findEndStr(b, posTmp, sizeExt - (posTmp - posSig), true);
	  			String pNameSec = ""; //$NON-NLS-1$
	  			if (fimNomeSec >= 0) {
					pNameSec = toStr(b, posTmp, fimNomeSec, "UTF-16LE"); //$NON-NLS-1$
	  			}
	  			fEntry.setSecondaryName(pNameSec);
				posTmp += (fimNomeSec+2);
				
				if (lngStrSize > 0) {
	  				if (verExt >= 7) {
	  					// localized name UTF-16 LE
	       				String locStr = toStrUTF16(b, posTmp, sizeExt - (posTmp - posSig));
	       				if (locStr != null) {
	       					fEntry.addLocalizedName(locStr);
	       				}
	  				} else {
	  					// localized name ASCII
	       				String locStr = toStr(b, posTmp, sizeExt - (posTmp - posSig), (byte) 0X00);
	       				if (locStr != null) {
	       					fEntry.addLocalizedName(locStr);
	       					posTmp += (locStr.length() + 1);
	       	       			if (locStr.length() % 2 == 0) {
		       					posTmp++; // tem um byte zero adicional no final da string pois é 16-bit aligned
	       	       			}
	       				}
	  				}
				}
			}
		}
	}
	
	private static final int toSmall(byte[] b, int offset) {
		if (offset + 1 >= b.length) return -1;
		return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8);
	}
	
	private static final int toInt(byte[] b, int offset) {
		if (offset + 3 >= b.length) return -1;
		return (b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8) | ((b[offset + 2] & 0XFF) << 16) | ((b[offset + 3] & 0XFF) << 24);
	}

	private static final long toInt6(byte[] b, int offset) {
		if (offset + 5 >= b.length) return -1;
		return (b[offset] & 0XFFL) | ((b[offset + 1] & 0XFFL) << 8) | ((b[offset + 2] & 0XFFL) << 16) | ((b[offset + 3] & 0XFFL) << 24) | ((b[offset + 4] & 0XFFL) << 32) | ((b[offset + 5] & 0XFFL) << 40);
	}
	
	private static final long toLongInt(byte[] b, int offset) {
		if (offset + 3 >= b.length) return -1;
		return ((b[offset] & 0XFF) | ((b[offset + 1] & 0XFF) << 8) | ((b[offset + 2] & 0XFF) << 16) | ((b[offset + 3] & 0XFF) << 24)) & 0xFFFFFFFFL;
	}
	
	private static final long toLong(byte[] b, int offset) {
		if (offset + 7 >= b.length) return -1;
		return (b[offset] & 0XFFL) | ((b[offset + 1] & 0XFFL) << 8) | ((b[offset + 2] & 0XFFL) << 16) | ((b[offset + 3] & 0XFFL) << 24) | ((b[offset + 4] & 0XFFL) << 32) | ((b[offset + 5] & 0XFFL) << 40) | ((b[offset + 6] & 0XFFL) << 48) | ((b[offset + 7] & 0XFFL) << 56);
	}

	private static final String toHex(byte[] b, int offset, int length) {
		if (offset + length >= b.length) return null;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		return sb.toString();
	}

	private static final String toHexLE(byte[] b, int offset, int length) {
		if (offset + length >= b.length) return null;
		StringBuilder sb = new StringBuilder();
		for (int i = length; i > 0; i--) {
			String s = Integer.toHexString(b[offset + i - 1] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		return sb.toString();
	}
	
	private static final String toStr(byte[] b, int offset, int length) {
		if (offset + length >= b.length) return null;
		return new String(b, offset, length, Charset.forName("UTF-8")); //$NON-NLS-1$
	}

	private static final String toStr(byte[] b, int offset, int maxlength, byte delim) {
		return toStr(b, offset, maxlength, delim, "UTF-8"); //"ISO8859_1" "UTF-8" //$NON-NLS-1$
	}

	private static final String toStr(byte[] b, int offset, int maxlength, byte delim, String charSet) {
		if (offset + maxlength >= b.length || maxlength < 0) return null;
		// procurando o delimitador
		int i = 0;
		for (i = 0; i <= maxlength; i++) 
			if ( b[offset+i] == delim ) 
				break;
		return new String(b, offset, i, Charset.forName(charSet)); // "ISO8859_1" "UTF-8"
	}
	
	private static final String toStrUTF16(byte[] b, int offset, int maxlength) {
		if (offset + maxlength >= b.length || maxlength < 0) return null;
		// procurando o delimitador
		int i = 0;
		for (i = 0; i < maxlength; i+=2) 
			if ( b[offset+i] == 0X00 && b[offset+i+1] == 0X00) 
				break;
		return new String(b, offset, i, Charset.forName("UTF-16LE")); //$NON-NLS-1$
	}
	
	private static final String toStr(byte[] b, int offset, int length, String charSet) {
		if (offset + length >= b.length || length < 0) return null;
		return new String(b, offset, length, Charset.forName(charSet));
	}

	private static final String toStr(byte[] b, int offset, int length, boolean utf16) {
		String charSet;
		if (utf16) {
			length = length*2;
			charSet = "UTF-16LE"; //$NON-NLS-1$
		} else {
			charSet = "ISO8859_1"; //$NON-NLS-1$
		}
		if (offset + length >= b.length || length < 0) return null;
		return new String(b, offset, length, Charset.forName(charSet));
	}
	
	private static final int findEndStr(byte[] b, int offset, int maxlength, boolean utf16) {
		if (offset + maxlength >= b.length || maxlength < 0) return -1;
		// procurando o delimitador
		int i = 0;
		if (utf16) {
			for (i = 0; i < maxlength; i+=2) 
				if ( b[offset+i] == 0X00 && b[offset+i+1] == 0X00) 
					break;
		} else {
			for (i = 0; i <= maxlength; i++) 
				if ( b[offset+i] == 0X00) 
					break;
		}
		return i;
	}
	
	
	private static final String toGUID(byte[] b, int offset) {
		// Where {%GUID%} is a GUID in the form: {00000000-0000-0000-0000-000000000000}.
		// 16 bytes
		if (offset + 15 >= b.length) return null;

		StringBuilder sb = new StringBuilder();
		for (int i = 3; i >= 0; i--) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		sb.append("-"); //$NON-NLS-1$
		for (int i = 5; i > 3; i--) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		sb.append("-"); //$NON-NLS-1$
		for (int i = 7; i > 5; i--) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		sb.append("-"); //$NON-NLS-1$
		for (int i = 8; i < 10; i++) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		sb.append("-"); //$NON-NLS-1$
		for (int i = 10; i < 16; i++) {
			String s = Integer.toHexString(b[offset + i] & 0XFF);
			if (s.length() == 1) sb.append('0');
			sb.append(s);
		}
		return sb.toString();
	}
}