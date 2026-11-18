function jumpTo(selector, backgroundColor = "#CCC", time = 900) {
    const obj = document.querySelector(selector);
    if (!obj) return;

    // Close all ancestor <details> elements
    for (let el = obj; el; el = el.parentNode) {
        if (el instanceof HTMLDetailsElement && el.tagName === 'DETAILS') {
            el.removeAttribute('open');
        }
    }

    setTimeout(() => {
        // Open all ancestor <details> elements
        for (let el = obj; el; el = el.parentNode) {
            if (el instanceof HTMLDetailsElement && el.tagName === 'DETAILS') {
                el.open = true;
            }
        }

        obj.scrollIntoView({ behavior: "smooth", block: "center" });

        if (!obj.highlight) {
            obj.highlight = true;
            const originalColor = obj.style.backgroundColor;
            obj.style.transition = `background-color ${time}ms linear`;
            obj.style.backgroundColor = backgroundColor;

            setTimeout(() => {
                obj.style.backgroundColor = originalColor;
                obj.highlight = false;
            }, time);
        }
    }, 1);
}
