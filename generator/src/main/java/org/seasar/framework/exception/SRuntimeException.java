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
package org.seasar.framework.exception;

/**
 * S2Framework {@code org.seasar.framework.exception.SRuntimeException} の互換シムです。
 * ベンダリングした 2-way SQL パーサの例外基底クラスとして使用します。
 */
public class SRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String messageCode;

    private final Object[] args;

    public SRuntimeException(final String messageCode) {
        this(messageCode, new Object[0]);
    }

    public SRuntimeException(final String messageCode, final Object[] args) {
        super("[" + messageCode + "]" + format(args));
        this.messageCode = messageCode;
        this.args = args;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getArgs() {
        return args;
    }

    private static String format(final Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        final StringBuffer buf = new StringBuffer();
        for (int i = 0; i < args.length; i++) {
            buf.append(i == 0 ? " " : ", ").append(args[i]);
        }
        return buf.toString();
    }
}
