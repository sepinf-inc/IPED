/*
 * Javascript processing task that ignores items by file extension (.bin and .asset).
 * It must be installed in TaskInstaller.xml to be executed.
 * Ignored items are excluded from processing and from the case.
 */

function getName() {
	return "IgnoreFilesByExtTask";
}

function getConfigurables() {}

function init(configuration) {}

function finish() {}

function process(e) {
	var ext = e.getExt();
	if (ext != null && /^(bin|asset)$/i.test(ext)) {
		e.setToIgnore(true);
	}
}
