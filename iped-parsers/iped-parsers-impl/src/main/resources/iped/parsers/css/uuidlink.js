function clickUID(uid) {
    var obj = document.querySelector("details[treeuid='"+uid+"']");
    var a = obj;
    var b=obj;
    
    while (a) {
        if(a instanceof HTMLElement){
            a.removeAttribute('open');
        }
        b = a;
        a = a.parentNode;
    }
    
    setTimeout(function(){
        a = obj;
        while (a) {
            if(a instanceof HTMLElement){
                a.open = 'true';
            }
            b = a;
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
    
    },1);
    
}

window.containsNavigableTree=true;

