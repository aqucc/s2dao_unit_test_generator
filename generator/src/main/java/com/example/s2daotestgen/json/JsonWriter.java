package com.example.s2daotestgen.json;

import java.io.File;
import java.io.IOException;

import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
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

    /**
     * スタンドアロンのエンティティメタを {@code <Entity>.entity.json} として出力する。
     * DAO 由来の {@code <Dao>.meta.json} とは別ファイル・別拡張子で衝突を避け、
     * generate 側の「テーブル逆引き辞書」補完に用いる。
     */
    public void writeEntity(final EntityMeta entity, final File out) throws IOException {
        mapper.writeValue(out, entity);
    }
}
