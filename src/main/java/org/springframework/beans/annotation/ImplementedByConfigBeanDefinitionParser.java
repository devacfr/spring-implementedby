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

import javax.annotation.Nonnull;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.annotation.AnnotationConfigBeanDefinitionParser;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.w3c.dom.Element;

/**
 * This class allows adding {@link ImplementedBy} annotation in annotation postprocessing.
 * @author devacfr<christophefriederich@mac.com>
 * @since 1.0
 */
public class ImplementedByConfigBeanDefinitionParser extends AnnotationConfigBeanDefinitionParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public BeanDefinition parse(@Nonnull final Element element, @Nonnull final ParserContext parserContext) {
        Object source = parserContext.extractSource(element);

        BeanDefinitionHolder holder = null;
        BeanDefinitionRegistry registry = parserContext.getRegistry();
        if (!registry.containsBeanDefinition(AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
            String name = AnnotationConfigUtils.AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME;
            RootBeanDefinition def = new RootBeanDefinition(ExtendAutowiredAnnotationBeanPostProcessor.class);
            def.setSource(source);
            holder = registerPostProcessor(registry, def, name);

            // Registers component for the surrounding <implementedby:annotation-config> element.
            CompositeComponentDefinition compDefinition =
                    new CompositeComponentDefinition(element.getTagName(), source);
            parserContext.pushContainingComponent(compDefinition);
        }

        // Nest the concrete beans in the surrounding component.
        if (holder != null) {
            parserContext.registerComponent(new BeanComponentDefinition(holder));
        }

        super.parse(element, parserContext);

        return null;
    }

    /**
     * 
     * @param registry Spring bean definition registry.
     * @param definition the definition of bean to register.
     * @param beanName the name of bean.
     * @return Returns a holder of bean definition.
     */
    @Nonnull
    private static BeanDefinitionHolder
            registerPostProcessor(final BeanDefinitionRegistry registry, final RootBeanDefinition definition,
                                  final String beanName) {

        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(beanName, definition);
        return new BeanDefinitionHolder(definition, beanName);
    }

}