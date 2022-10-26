function decodeUserAssist(keyValueName){
	return keyValueName.replace(/([A-Z]|[a-z])/g, function() {
		if(arguments[1].charAt(0)>='a'){
			if(arguments[1].charAt(0)>'m'){
				return String.fromCharCode(arguments[1].charCodeAt(0)-13);
			}else{
				return String.fromCharCode(arguments[1].charCodeAt(0)+13);
			}
		}else{
			if(arguments[1].charAt(0)>'M'){
				return String.fromCharCode(arguments[1].charCodeAt(0)-13);
			}else{
				return String.fromCharCode(arguments[1].charCodeAt(0)+13);
			}
		}
    });
}