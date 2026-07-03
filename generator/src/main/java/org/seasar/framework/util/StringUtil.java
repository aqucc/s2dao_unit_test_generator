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
import java.util.List;
import java.util.StringTokenizer;

/**
 * S2Framework {@code org.seasar.framework.util.StringUtil} の互換シムです。
 *
 * <p>
 * ベンダリングした 2-way SQL パーサ ({@code org.seasar.extension.sql}) が参照する最小限の
 * メソッドのみを、本家 Seasar2 と同一の挙動で実装しています。挙動同一性は
 * {@code SqlParserConformanceTest} で担保しています。
 * </p>
 */
public final class StringUtil {

    private StringUtil() {
    }

    public static boolean isEmpty(final String text) {
        return text == null || text.length() == 0;
    }

    public static boolean isNotEmpty(final String text) {
        return !isEmpty(text);
    }

    public static boolean isBlank(final String str) {
        if (str == null || str.length() == 0) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(final String str) {
        return !isBlank(str);
    }

    /**
     * 本家 Seasar2 と同じく、区切り文字群 {@code delim} に含まれる各文字を
     * デリミタとして分割します(空要素は含みません)。
     */
    public static String[] split(final String str, final String delim) {
        if (isEmpty(str)) {
            return new String[0];
        }
        final List list = new ArrayList();
        final StringTokenizer st = new StringTokenizer(str, delim);
        while (st.hasMoreElements()) {
            list.add(st.nextElement());
        }
        return (String[]) list.toArray(new String[list.size()]);
    }

    public static String replace(final String text, final String fromText,
            final String toText) {
        if (text == null || fromText == null || toText == null) {
            return null;
        }
        final StringBuffer buf = new StringBuffer(100);
        int pos = 0;
        int pos2 = 0;
        while (true) {
            pos = text.indexOf(fromText, pos2);
            if (pos == 0) {
                buf.append(toText);
                pos2 = fromText.length();
            } else if (pos > 0) {
                buf.append(text.substring(pos2, pos));
                buf.append(toText);
                pos2 = pos + fromText.length();
            } else {
                buf.append(text.substring(pos2));
                break;
            }
        }
        return buf.toString();
    }

    public static String decapitalize(final String name) {
        if (isEmpty(name)) {
            return name;
        }
        final char[] chars = name.toCharArray();
        if (chars.length >= 2 && Character.isUpperCase(chars[0])
                && Character.isUpperCase(chars[1])) {
            return name;
        }
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    public static boolean equalsIgnoreCase(final String target,
            final String text) {
        return (target == null) ? (text == null) : target
                .equalsIgnoreCase(text);
    }
}
