package iped.parsers.util;

import java.io.IOException;
import java.util.Collection;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class OmitEmptyObjectsTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {

        // We only want to apply this to Plain Old Java Objects (POJOs) and arrays
        if (!Object.class.isAssignableFrom(type.getRawType())
                && !type.getRawType().isArray() 
                && !Collection.class.isAssignableFrom(type.getRawType())
                || String.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                JsonElement tree = delegate.toJsonTree(value);

                if (tree.isJsonObject() && tree.getAsJsonObject().entrySet().stream().filter(e -> !e.getValue().isJsonNull()).count() == 0) {
                    out.nullValue();
                    return;
                } else if (tree.isJsonArray() && tree.getAsJsonArray().isEmpty()) {
                    out.nullValue();
                    return;
                }
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                return delegate.read(in);
            }
        };
    }
}