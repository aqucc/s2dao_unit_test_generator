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
package org.seasar.framework.log;

/**
 * S2Framework {@code org.seasar.framework.log.Logger} の互換シムです。
 * ベンダリングした {@code SqlContextImpl} が参照します。実解析には影響しないため
 * ログ出力は行いません(no-op)。
 */
public final class Logger {

    private Logger() {
    }

    public static Logger getLogger(final Class clazz) {
        return new Logger();
    }

    public void log(final String messageCode, final Object[] args) {
        // no-op: メタ抽出処理ではロギングは不要
    }

    public void debug(final Object message) {
        // no-op
    }

    public boolean isDebugEnabled() {
        return false;
    }
}
