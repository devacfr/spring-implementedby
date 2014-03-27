/**
 * Copyright 2014 devacfr<christophefriederich@mac.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.beans.annotation;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * 
 * @author devacfr<christophefriederich@mac.com>
 *
 */
public class ImplementedByNamespaceHandler extends NamespaceHandlerSupport {

    /**
     * {@inheritDoc}
     */
    public void init() {

        registerBeanDefinitionParser("annotation-config", new ImplementedByConfigBeanDefinitionParser());

    }

}