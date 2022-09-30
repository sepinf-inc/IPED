function hextostring(keyValueName){
	var antes = -1;
	
	return keyValueName.replace(/([0-9A-Fa-f]{2})/g, function() {
		if(antes != -1){
			var tmpantes = antes;
			antes=-1;
			return decodeURIComponent(escape(String.fromCharCode.apply(null, [tmpantes, parseInt(arguments[1], 16)])));
		}else{
			if(parseInt(arguments[1], 16)>128){
				antes = parseInt(arguments[1], 16);
				return '';
			}else{
				antes=-1;
				return String.fromCharCode(parseInt(arguments[1], 16));
			}
		}
    });
}

function hexDatatostring(keyValue){	
	return hextostring(keyValue.getValueDataAsString());
}

