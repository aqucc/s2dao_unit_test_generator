package com.example.s2daotestgen.json;

import java.io.File;
import java.io.IOException;

import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * {@link DaoMeta} を整形済み JSON として出力する。
 */
public final class JsonWriter {

    private final ObjectMapper mapper;

    public JsonWriter() {
        mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void write(final DaoMeta dao, final File out) throws IOException {
        mapper.writeValue(out, dao);
    }

    public String toJson(final DaoMeta dao) throws IOException {
        return mapper.writeValueAsString(dao);
    }
}
