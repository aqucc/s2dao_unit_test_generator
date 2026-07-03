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

import ognl.Ognl;
import ognl.OgnlException;

import org.seasar.framework.exception.SRuntimeException;

/**
 * S2Framework {@code org.seasar.framework.util.OgnlUtil} の互換シムです。
 * OGNL 式のパースおよび評価をベンダリングした IfNode/ParenBindVariableNode から利用します。
 */
public final class OgnlUtil {

    private OgnlUtil() {
    }

    public static Object parseExpression(final String expression) {
        try {
            return Ognl.parseExpression(expression);
        } catch (final OgnlException e) {
            throw new SRuntimeException("EOGNL0000",
                    new Object[] { expression, e });
        }
    }

    public static Object getValue(final Object exp, final Object root) {
        return getValue(exp, null, root);
    }

    public static Object getValue(final Object exp, final Object context,
            final Object root) {
        try {
            if (context == null) {
                return Ognl.getValue(exp, root);
            }
            return Ognl.getValue(exp, (java.util.Map) context, root);
        } catch (final OgnlException e) {
            throw new SRuntimeException("EOGNL0001",
                    new Object[] { exp, e });
        }
    }
}
