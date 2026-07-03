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
package org.seasar.framework.beans.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.seasar.framework.beans.PropertyDesc;

/**
 * リフレクションでプロパティ値を取得する {@link PropertyDesc} 実装(互換シム)です。
 * getter を優先し、無ければ public フィールドから取得します。
 */
public class PropertyDescImpl implements PropertyDesc {

    private final String propertyName;

    private final Class propertyType;

    private final Method readMethod;

    private final Field field;

    public PropertyDescImpl(final String propertyName,
            final Class propertyType, final Method readMethod, final Field field) {
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.readMethod = readMethod;
        this.field = field;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Class getPropertyType() {
        return propertyType;
    }

    public Object getValue(final Object target) {
        try {
            if (readMethod != null) {
                return readMethod.invoke(target, (Object[]) null);
            }
            if (field != null) {
                return field.get(target);
            }
            return null;
        } catch (final Exception e) {
            throw new RuntimeException("failed to read property '"
                    + propertyName + "'", e);
        }
    }
}
