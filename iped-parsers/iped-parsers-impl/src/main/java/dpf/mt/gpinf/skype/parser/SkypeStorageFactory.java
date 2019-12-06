package dpf.mt.gpinf.skype.parser;

import java.io.File;

import dpf.mt.gpinf.skype.parser.v8.SkypeSqliteV12;

public class SkypeStorageFactory {
	static SkypeStorage createFromFile(File file, String mainDbPath) {
		if(file.getName().contains("main.db") || mainDbPath.contains("main.db")) {
			return new SkypeSqlite(file, mainDbPath);
		}

		if(file.getName().startsWith("s4l-") || mainDbPath.contains("s4l-")) {
			return new SkypeSqliteV12(file, mainDbPath);
		}

		return null;
	}

	public static SkypeStorage createFromMediaType(String string, File file, String mainDbPath) {
		if(string.contentEquals(SkypeParser.SKYPE_MIME.toString())) {
			return new SkypeSqlite(file, mainDbPath);
		}
		if(string.contentEquals(SkypeParser.SKYPE_MIME_V12.toString())) {
			return new SkypeSqliteV12(file, mainDbPath);
		}
		return null;
	}
}