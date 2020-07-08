function openIfExists(url1, url2){
    var img1 = new Image();
    img1.onload = () => window.location = url1;
    img1.onerror = () => window.location = url2;
    img1.src = url1
}

function createMediaElement(elementType, imgElement) {
    var mediaElement = document.createElement(elementType);
    var controls = document.createAttribute("controls");
    mediaElement.setAttributeNode(controls);
    var src1 = imgElement.getAttribute("data-src1");
    if (src1) {
        var src1Attr = document.createAttribute("src");
        src1Attr.value = src1;
        mediaElement.setAttributeNode(src1Attr); 
    }
    var src2 = imgElement.getAttribute("data-src2");
    if (src2) {
        var sourceElement = document.createElement("source");
        var src2Attr = document.createAttribute("src");
        src2Attr.value = src2;
        sourceElement.setAttributeNode(src2Attr);
        mediaElement.appendChild(sourceElement);
    }
    return mediaElement;
}

function createMediaControls() {
    document.querySelectorAll(".iped-video, .iped-audio").forEach((el) => {
        var mediaType = "video";
        if (el.className.search("iped-audio") >= 0) {
            mediaType = "audio";
        }
        var mediaElement = createMediaElement(mediaType, el);
        el.replaceWith(mediaElement);
    });
    document.querySelectorAll("input.check").forEach((el) => {
        el.remove();
    });
}

if (navigator.userAgent.search("JavaFX") < 0) {
    document.write("<style>.iped-audio, .iped-video {display: none; }</style>");
    if (document.readyState != "loading") {
        createMediaControls();
    } else {
        document.addEventListener("DOMContentLoaded", createMediaControls);
    }
}
