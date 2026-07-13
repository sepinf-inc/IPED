package iped.parsers.util;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class OmitEmptyArraysTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {

        // We only want to apply this to arrays and collections
        if (!type.getRawType().isArray() && !Collection.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null //
                        || value instanceof Collection && ((Collection<?>) value).isEmpty() //
                        || value.getClass().isArray() && Array.getLength(value) == 0) {
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