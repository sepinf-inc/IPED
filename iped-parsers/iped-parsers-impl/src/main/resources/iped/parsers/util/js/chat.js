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

document.addEventListener("DOMContentLoaded", () => {
    const conversation = document.getElementById("conversation");
    const navControls = document.getElementById("nav-controls");
    const navCounter = document.getElementById("nav-counter");
    const btnPrev = document.getElementById("btn-prev");
    const btnNext = document.getElementById("btn-next");
    const btnClear = document.getElementById("btn-clear");
    let selectedIndex = 0;

    function scrollToLine(element) {
        if (!element) return;
        const topbar = document.getElementById("topbar");
        const topbarHeight = topbar ? topbar.offsetHeight : 84;
        const elementTop = element.getBoundingClientRect().top + window.scrollY;
        window.scrollTo(0, elementTop - topbarHeight - 8);
    }

	function updateNavigation(targetLine = null) {
	    const spinner = document.getElementById("topbar-spinner");
	    const navContent = document.getElementById("nav-content");

	    if (isLoading) {
	        if (spinner) spinner.style.display = "block";
	        if (navContent) navContent.style.display = "none";
	        return;
	    }
	    if (spinner) {
	        spinner.style.display = "none";
	    }

	    const selectedLines = Array.from(document.querySelectorAll("#conversation .line.sel"));
	    const total = selectedLines.length;

	    if (total === 0) {
	        if (navContent) navContent.style.display = "none";
	        selectedIndex = 0;
	        return;
	    }

	    if (navContent) navContent.style.display = "flex";

	    if (targetLine && selectedLines.includes(targetLine)) {
	        selectedIndex = selectedLines.indexOf(targetLine);
	    } else if (selectedIndex >= total) {
	        selectedIndex = total - 1;
	    } else if (selectedIndex < 0) {
	        selectedIndex = 0;
	    }

	    document.getElementById("nav-counter").textContent = `${selectedIndex + 1} / ${total}`;
	}

    window.updateNavigation = updateNavigation;

    if (conversation) {
        conversation.addEventListener("click", (event) => {
            if (event.target.closest("a, input, button, audio, video, .check")) {
                return;
            }			
            const line = event.target.closest(".line");
            if (line) {
				if (line.id) {
                    const isSel = line.classList.toggle("sel");
                    if (window.msgApp) {
                        window.msgApp.selectMessage(line.id, isSel);
                    }
                    updateNavigation(line);
				}
            }
        });
    }
    if (btnPrev) {
        btnPrev.addEventListener("click", () => {
            const selectedLines = Array.from(document.querySelectorAll("#conversation .line.sel"));
            if (selectedLines.length === 0) return;
            selectedIndex = (selectedIndex - 1 + selectedLines.length) % selectedLines.length;
            updateNavigation();
            scrollToLine(selectedLines[selectedIndex]);
        });
    }
    if (btnNext) {
        btnNext.addEventListener("click", () => {
            const selectedLines = Array.from(document.querySelectorAll("#conversation .line.sel"));
            if (selectedLines.length === 0) return;
            selectedIndex = (selectedIndex + 1) % selectedLines.length;
            updateNavigation();
            scrollToLine(selectedLines[selectedIndex]);
        });
    }
    if (btnClear) {
        btnClear.addEventListener("click", () => {
            const selectedLines = document.querySelectorAll("#conversation .line.sel");
            selectedLines.forEach(line => {
                line.classList.remove("sel");
                if (window.msgApp && line.id) {
                    window.msgApp.selectMessage(line.id, false);
                }
            });
            updateNavigation();
        });
    }
});

let isLoading = true;
window.addEventListener('load', () => {
	isLoading = false;

    const spinner = document.getElementById('topbar-spinner');
    if (spinner) {
        spinner.style.display = 'none';
    }

    const modal = document.getElementById('loading-modal');
    if (modal) {
        modal.classList.add('fade-out');
        setTimeout(() => modal.remove(), 300);
    }

    updateNavigation();
});

const topHeight = document.getElementById('topbar').offsetHeight;
document.getElementById('conversation').style.paddingTop = `${topHeight}px`;
