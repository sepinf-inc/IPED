package iped.engine.dictionary;

import java.nio.file.Path;
import java.util.Collections;

import iped.dictionary.PasswordDictionaryFactory;
import iped.engine.config.Configuration;

public class PasswordDictionaryFactoryImpl implements PasswordDictionaryFactory {

    @Override
    public Iterable<String> createPasswordDictionary() {
        return new PasswordDictionary(
                Path.of(Configuration.getInstance().appRoot, "dictionaries"),
                Collections.emptyList());
    }
}
