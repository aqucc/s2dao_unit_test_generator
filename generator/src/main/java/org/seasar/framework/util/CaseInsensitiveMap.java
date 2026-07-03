/*
 * Copyright 2004-2015 the Seasar Foundation and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.seasar.framework.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2Framework {@code org.seasar.framework.util.CaseInsensitiveMap} の互換シムです。
 * ベンダリングした {@code SqlContextImpl} が参照する範囲(大文字小文字を無視したキー参照と
 * 挿入順の位置アクセス)を、本家と同一挙動で実装しています。
 */
public class CaseInsensitiveMap {

    private final Map map = new LinkedHashMap();

    private final List values = new ArrayList();

    public boolean containsKey(final String key) {
        return map.containsKey(convertKey(key));
    }

    public Object get(final String key) {
        return map.get(convertKey(key));
    }

    /**
     * 挿入順の位置指定で値を取得します(本家 ArrayMap 互換)。
     */
    public Object get(final int index) {
        return values.get(index);
    }

    public void put(final String key, final Object value) {
        final String k = convertKey(key);
        if (!map.containsKey(k)) {
            values.add(value);
        } else {
            final int idx = indexOfKey(k);
            if (idx >= 0) {
                values.set(idx, value);
            }
        }
        map.put(k, value);
    }

    public int size() {
        return map.size();
    }

    private int indexOfKey(final String convertedKey) {
        int i = 0;
        for (Object key : map.keySet()) {
            if (key.equals(convertedKey)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static String convertKey(final String key) {
        return key == null ? null : key.toLowerCase();
    }
}
