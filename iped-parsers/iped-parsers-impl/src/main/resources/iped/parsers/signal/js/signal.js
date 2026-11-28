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

// From a reply to the message it quotes
function goToMessage(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.scrollIntoView({block: "center"});
    el.classList.add("highlight");
    setTimeout(() => el.classList.remove("highlight"), 1500);
}

// A long conversation is split into several items: a reply may quote a message that
// is in another one, which this page cannot jump to
function unlinkMissingQuotes() {
    document.querySelectorAll(".quote[data-target]").forEach((el) => {
        if (!document.getElementById(el.getAttribute("data-target"))) {
            el.removeAttribute("onclick");
            el.title = "The quoted message is in another part of this conversation";
        }
    });
}

if (document.readyState != "loading") {
    unlinkMissingQuotes();
} else {
    document.addEventListener("DOMContentLoaded", unlinkMissingQuotes);
}

// Bookmark checkboxes only work inside the application
function removeChecks() {
    document.querySelectorAll("input.check").forEach((el) => {
        el.remove();
    });
}

if (navigator.userAgent.search("JavaFX") < 0) {
    if (document.readyState != "loading") {
        removeChecks();
    } else {
        document.addEventListener("DOMContentLoaded", removeChecks);
    }
}
