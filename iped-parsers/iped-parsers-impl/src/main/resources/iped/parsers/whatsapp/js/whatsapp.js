function openIfExists(url2, url1){
    if (navigator.userAgent.search("JavaFX") >= 0) return;
    const img1 = new Image();
    img1.onload = () => window.location = url1;
    img1.onerror = () => window.location = url2;
    img1.src = url1;
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

if (navigator.userAgent.search("JavaFX") < 0) {
    document.write("<style>.iped-audio, .iped-video {display: none; }</style>");
    if (document.readyState != "loading") {
        createMediaControls();
    } else {
        document.addEventListener("DOMContentLoaded", createMediaControls);
    }
}
