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
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.seasar.framework.beans.BeanDesc;
import org.seasar.framework.beans.PropertyDesc;

/**
 * リフレクションでプロパティ情報を解決する {@link BeanDesc} 実装(互換シム)です。
 * JavaBeans の getter と public フィールドの双方に対応します。
 */
public class BeanDescImpl implements BeanDesc {

    private final Map propertyDescs = new HashMap();

    public BeanDescImpl(final Class beanClass) {
        setupPropertyDescsByField(beanClass);
        setupPropertyDescsByMethod(beanClass);
    }

    private void setupPropertyDescsByField(final Class beanClass) {
        final Field[] fields = beanClass.getFields();
        for (int i = 0; i < fields.length; i++) {
            final Field field = fields[i];
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final String name = field.getName();
            propertyDescs.put(name, new PropertyDescImpl(name,
                    field.getType(), null, field));
        }
    }

    private void setupPropertyDescsByMethod(final Class beanClass) {
        final Method[] methods = beanClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            final String methodName = method.getName();
            String propertyName = null;
            if (methodName.startsWith("get") && methodName.length() > 3
                    && !method.getReturnType().equals(void.class)) {
                propertyName = decapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2
                    && (method.getReturnType().equals(boolean.class) || method
                            .getReturnType().equals(Boolean.class))) {
                propertyName = decapitalize(methodName.substring(2));
            }
            if (propertyName == null || "class".equals(propertyName)) {
                continue;
            }
            propertyDescs.put(propertyName, new PropertyDescImpl(propertyName,
                    method.getReturnType(), method, null));
        }
    }

    private static String decapitalize(final String name) {
        if (name.length() >= 2 && Character.isUpperCase(name.charAt(0))
                && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        final char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    public boolean hasPropertyDesc(final String propertyName) {
        return propertyDescs.containsKey(propertyName);
    }

    public PropertyDesc getPropertyDesc(final String propertyName) {
        final PropertyDesc pd = (PropertyDesc) propertyDescs.get(propertyName);
        if (pd == null) {
            throw new RuntimeException("property not found: " + propertyName);
        }
        return pd;
    }
}
