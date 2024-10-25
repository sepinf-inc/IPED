function clickUUID() {
    const source = event.target || event.srcElement;
    window.app.open('bplist\\:embeddedID:"'+source.getAttribute("uuid")+'"');
}

window.containsNavigableTree=true;

