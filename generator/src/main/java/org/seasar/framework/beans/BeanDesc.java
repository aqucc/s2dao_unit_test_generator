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
package org.seasar.framework.beans;

/**
 * S2Framework {@code org.seasar.framework.beans.BeanDesc} の互換シム(抜粋)です。
 * ベンダリングした 2-way SQL パーサが利用する範囲のみを定義しています。
 */
public interface BeanDesc {

    boolean hasPropertyDesc(String propertyName);

    PropertyDesc getPropertyDesc(String propertyName);
}
