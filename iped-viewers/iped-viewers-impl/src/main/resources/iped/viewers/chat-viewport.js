(function () {
    var width = Math.min(window.innerWidth, document.documentElement.clientWidth || window.innerWidth);
    var height = Math.min(window.innerHeight, document.documentElement.clientHeight || window.innerHeight);
    var result = { width: width, height: height, messages: [] };
    function visible(rect) {
        return rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0
            && rect.left < width && rect.top < height;
    }
    function bounds(rect) {
        return [rect.left, rect.top, rect.width, rect.height];
    }
    var bubbles = document.querySelectorAll('.bbl, .bbr, .bbls, .bbrs, .systemmessage, .linha .incoming, .linha .outgoing');
    for (var m = 0; m < bubbles.length; m++) {
        var bubble = bubbles[m];
        if (bubble.parentElement.closest('.bbl, .bbr, .bbls, .bbrs, .systemmessage')
                || !visible(bubble.getBoundingClientRect())) continue;
        var name = bubble.querySelector('.name, span[style*="#b4c74b"]');
        var time = bubble.querySelector('.time');
        var label = name && visible(name.getBoundingClientRect()) ? name.textContent.trim() : '';
        if (time && visible(time.getBoundingClientRect())) label += ' ' + time.textContent.trim();
        var message = { bounds: bounds(bubble.getBoundingClientRect()), label: label.trim(), transcripts: [], images: [] };
        var images = bubble.querySelectorAll('a img:not(.iped-video), a .imageImg, img.thumb:not(.iped-video)');
        for (var j = 0; j < images.length; j++) {
            var img = images[j];
            var box = img.getBoundingClientRect();
            if (!visible(box) || /^(audio|video)$/i.test(img.title)) continue;
            var link = img.closest('a');
            var action = link && link.getAttribute('onclick') || '';
            var match = /app\.open\(\s*['"]hash:([a-f0-9]{32,128})['"]\s*\)/i.exec(action);
            var check = link && link.previousElementSibling;
            var hash = match ? match[1] : '';
            if (!hash && /app\.open\(/.test(action) && check && check.matches('input.check')) {
                hash = check.getAttribute('name') || '';
            }
            if (!/^[a-f0-9]{32,128}$/i.test(hash)) hash = '';
            var thumbnail = img.getAttribute('src') || '';
            if (!img.matches('img.thumb') || /^(application|document|text)$/i.test(img.title)
                    || !/^data:image\/(?:png|jpe?g|webp|gif);base64,/i.test(thumbnail)
                    || thumbnail.length > 8 * 1024 * 1024) thumbnail = '';
            if (hash || thumbnail) message.images.push({hash: hash.toLowerCase(), thumbnail: thumbnail, bounds: bounds(box)});
        }
        if (bubble.querySelector('.audioImg, .iped-audio, audio')) {
            var italics = bubble.querySelectorAll('i');
            for (var t = 0; t < italics.length; t++) {
                var italic = italics[t];
                if (!visible(italic.getBoundingClientRect())) continue;
                var prefix = document.createRange();
                prefix.setStart(bubble, 0);
                prefix.setEndBefore(italic);
                if (!/(transcri[çc][aã]o|transcription|transkription)[^:]*:\s*$/i.test(prefix.toString().slice(-200))) continue;
                var transcript = { hash: '', characters: [] };
                var checks = bubble.querySelectorAll('input.check');
                for (var h = 0; h < checks.length; h++) {
                    if (checks[h].compareDocumentPosition(italic) & 4) {
                        var hash = checks[h].getAttribute('name') || '';
                        if (/^[a-f0-9]{32,128}$/i.test(hash)) transcript.hash = hash;
                    }
                }
                var walker = document.createTreeWalker(italic, NodeFilter.SHOW_TEXT, null, false);
                var node;
                while ((node = walker.nextNode())) {
                    for (var c = 0; c < node.textContent.length; c++) {
                        var range = document.createRange();
                        range.setStart(node, c);
                        range.setEnd(node, c + 1);
                        var rect = range.getBoundingClientRect();
                        if (visible(rect) && rect.left + rect.width / 2 >= 0 && rect.left + rect.width / 2 < width
                                && rect.top + rect.height / 2 >= 0 && rect.top + rect.height / 2 < height) {
                            transcript.characters.push({ text: node.textContent.charAt(c), bounds: bounds(rect) });
                        }
                    }
                }
                if (transcript.characters.length) message.transcripts.push(transcript);
            }
        }
        result.messages.push(message);
    }
    return result;
})()
