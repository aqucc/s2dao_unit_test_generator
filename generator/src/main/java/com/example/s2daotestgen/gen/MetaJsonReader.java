package com.example.s2daotestgen.gen;

import java.io.File;
import java.io.IOException;

import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * フェーズ1 が出力した {@code <Dao>.meta.json} を {@link DaoMeta} に読み戻す。
 */
public final class MetaJsonReader {

    private final ObjectMapper mapper;

    public MetaJsonReader() {
        mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public DaoMeta read(File f) throws IOException {
        return mapper.readValue(f, DaoMeta.class);
    }

    /** {@code <Entity>.entity.json} を {@link EntityMeta} に読み戻す。 */
    public EntityMeta readEntity(File f) throws IOException {
        return mapper.readValue(f, EntityMeta.class);
    }
}
