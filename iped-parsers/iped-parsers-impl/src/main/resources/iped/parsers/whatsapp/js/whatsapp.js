function openImage(url2, url1) {
    if (navigator.userAgent.search("JavaFX") >= 0) return;
    const aux = new Image();
    aux.onload = () => window.location = url1;
    aux.onerror = () => window.location = url2;
    aux.src = url1;
}

function openAudio(url2, url1) {
    openAV(url2, url1, 'audio');
}

function openVideo(url2, url1) {
    openAV(url2, url1, 'video');
}

function openAV(url2, url1, type) {
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

function openOther(url2, url1) {
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

function showMessage(message) {
    var fragMessageClose = document.getElementById('fragMessageClose').value;
    show_prompt("", message, false, fragMessageClose);
    return false;
}

function goToAnchorId(id) {
    var scroll_padding_top = document.getElementById('topbar').getBoundingClientRect().height;
    var r = document.querySelector(':root');
    var rs = getComputedStyle(r);
    if (rs.getPropertyValue('--scroll-padding-top') != scroll_padding_top + 'px')
        r.style.setProperty('--scroll-padding-top', scroll_padding_top + 'px');
    var div = document.getElementById(id);
    var scrollY = window.scrollY;
    if (div) {
        var top = div.getBoundingClientRect().top;
        location.hash = '';
        window.scrollTo(0, top + scrollY);
        location.href = "#" + id;
        if (top > scroll_padding_top) {
            window.scrollTo(0, scrollY);
        }
    } else { // Id not found, can be in previous pages
        var input;
        var fragMessageChat = document.getElementById('fragMessageChat').value;
        var fragMessageId = document.getElementById('fragMessageId').value;
        var fragMessageClose = document.getElementById('fragMessageClose').value;
        var i = 0;
        while (true) {
            input = document.getElementById('frag' + i);
            if (input) {
                if (id <= input.value) {
                    show_prompt("", fragMessageChat + " " + i + "</br> " + fragMessageId + " " + id, false, fragMessageClose);
                    break;
                } else {
                    i = i + 1;
                }
            } else {
                break;
            }
        }
        return false;
    }
}

/* Author: https://github.com/lecoa/Vanilla-JS-Prompt */
function show_prompt(title, message, pribtnHide = false, pribtnLabel = "Close", secBtnLabel = '', secBtnAction = '') {
    document.getElementById("modal-alert-title").innerHTML = title;
    document.getElementById("modal-alert-content").innerHTML = message;
    if (pribtnHide) {
        document.getElementById("modal-button-primary").style.display = "none";
    } else {
        document.getElementById("modal-button-primary").style.display = "inline-block";
    }
    document.getElementById("modal-button-primary").innerText = pribtnLabel;
    if (secBtnLabel != '' && secBtnAction != '') {
        document.getElementById("modal-button-secondary").innerText = secBtnLabel;
        document.getElementById("modal-button-secondary").setAttribute('onclick', secBtnAction);
        document.getElementById("modal-button-secondary").style.display = "inline-block";
        document.getElementById("modal-button-primary").classList.add("default");
    } else {
        document.getElementById("modal-button-secondary").style.display = "none";
        document.getElementById("modal-button-primary").classList.remove("default");
    }
    document.getElementById("modal-alert").style.display = "block";
}

if (navigator.userAgent.search("JavaFX") < 0) {
    document.write("<style>.iped-audio, .iped-video {display: none; }</style>");
    if (document.readyState != "loading") {
        createMediaControls();
    } else {
        document.addEventListener("DOMContentLoaded", createMediaControls);
    }
}

const topHeight = document.getElementById('topbar').offsetHeight;
document.getElementById('conversation').style.paddingTop = `${topHeight}px`;

window.addEventListener('load', () => {
    const modal = document.getElementById('loading-modal');
    if (modal) {
        modal.classList.add('fade-out');
        setTimeout(() => modal.remove(), 300);
    }
});
