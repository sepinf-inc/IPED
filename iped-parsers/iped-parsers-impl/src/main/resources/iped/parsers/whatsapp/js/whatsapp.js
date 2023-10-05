function openImage(url2, url1){
    if (navigator.userAgent.search("JavaFX") >= 0) return;
    const aux = new Image();
    aux.onload = () => window.location = url1;
    aux.onerror = () => window.location = url2;
    aux.src = url1;
}

function openAudio(url2, url1){
    openAV(url2, url1, 'audio');
}

function openVideo(url2, url1){
    openAV(url2, url1, 'video');
}

function openAV(url2, url1, type){
    if (navigator.userAgent.search("JavaFX") >= 0) return;
    const aux = document.createElement(type);
    aux.style.display = 'none';
    aux.addEventListener('loadedmetadata', () => {
        aux.remove();
        window.location = url1;
    });
    aux.addEventListener('error', () => {
        aux.remove();
        window.location = url2;
    });
    aux.src = url1;
    document.body.append(aux);
}

function openOther(url2, url1){
    if (navigator.userAgent.search("JavaFX") >= 0) return;
    window.location = url2;
}

function createMediaElement(elementType, el) {
    const mediaElement = document.createElement(elementType);
    const controls = document.createAttribute("controls");
    mediaElement.setAttributeNode(controls);
    const src1 = el.getAttribute("data-src1");
    if (src1) {
        const sourceElement = document.createElement("source");
        const srcAttr = document.createAttribute("src");
        srcAttr.value = src1;
        sourceElement.setAttributeNode(srcAttr);
        mediaElement.appendChild(sourceElement);
    }
    const src2 = el.getAttribute("data-src2");
    if (src2) {
        const sourceElement = document.createElement("source");
        const src2Attr = document.createAttribute("src");
        src2Attr.value = src2;
        sourceElement.setAttributeNode(src2Attr);
        mediaElement.appendChild(sourceElement);
    }
    return mediaElement;
}

function createMediaControls() {
    document.querySelectorAll(".iped-video, .iped-audio").forEach((el) => {
        const mediaType = el.classList.contains("iped-audio") ? "audio" : "video";
        el.replaceWith(createMediaElement(mediaType, el));
    });
    document.querySelectorAll("input.check").forEach((el) => {
        el.remove();
    });
}

function goAnchorId(id){
	var div = document.getElementById(id);
	if (div){
	    location.hash = '';
		window.scrollTo(0, div.getBoundingClientRect().top + window.scrollY);	
		location.href="#"+id;
	}else{
		return false;
	}
}

if (navigator.userAgent.search("JavaFX") < 0) {
    document.write("<style>.iped-audio, .iped-video {display: none; }</style>");
    if (document.readyState != "loading") {
        createMediaControls();
    } else {
        document.addEventListener("DOMContentLoaded", createMediaControls);
    }
}
