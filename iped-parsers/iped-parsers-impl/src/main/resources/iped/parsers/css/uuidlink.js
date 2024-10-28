function clickUID(uid) {
    var obj = document.querySelector("details[treeuid='"+uid+"']");
    var a = obj;
    while (a) {
        a.open = 'true';
        a = a.parentNode;
    }
    obj.scrollIntoView();

    if(!obj.highlight){
        obj.highlight=true;
        bcolor = obj.style.backgroundColor;
        obj.style.backgroundColor="#AAAAAA";
        setTimeout(function(){
            obj.style.backgroundColor=bcolor;
            obj.highlight=false;},1000);
    }

}

window.containsNavigableTree=true;

