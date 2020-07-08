
var css = document.createElement("style");
css.type = "text/css";
var inHtml = "";
if (navigator.userAgent.search("JavaFX") >= 0) {
    inHtml = ".iped-hide { display: none; }";
    inHtml += ".iped-show { display: block; }";
} else {
    inHtml = ".iped-hide { display: block; }";
    inHtml += ".iped-show { display: none; }";
}
css.innerHTML = inHtml;
document.head.appendChild(css);

function openIfExists(url1, url2){
    var img1 = new Image();
    img1.onload = () => window.location = url1;
    img1.onerror = () => window.location = url2;
    img1.src = url1
}
