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

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

/**
 * 
 * @author devacfr<christophefriederich@mac.com>
 *
 */
public class ImplementedByAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter implements
        MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {

    /**
     * log instance.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * the class of {@link ImplementedBy} annotation.
     */
    private final Class<? extends Annotation> implementedByAnnotationType = ImplementedBy.class;

    /**
     * ordering.
     */
    private int order = Ordered.LOWEST_PRECEDENCE - 2;

    /**
     * Spring bean factory.
     */
    private DefaultListableBeanFactory beanFactory;

    /**
     * {@link InjectionMetadata} cached.
     */
    private final Map<Class<?>, InjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<Class<?>, InjectionMetadata>();

    /**
     * Sets ordering in Postprocessor execution.
     * @param order the order.
     */
    public void setOrder(final int order) {
        this.order = order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanFactory(@Nonnull final BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ImplementedByAnnotationBeanPostProcessor requires a DefaultListableBeanFactory");
        }
        this.beanFactory = (DefaultListableBeanFactory) beanFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessMergedBeanDefinition(@Nonnull final RootBeanDefinition beanDefinition,
                                                @Nullable final Class<?> beanType, @Nullable final String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = findImplementedMetadata(beanType);
            metadata.checkConfigMembers(beanDefinition);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public PropertyValues postProcessPropertyValues(@Nonnull final PropertyValues pvs,
                                                    @Nonnull final PropertyDescriptor[] pds,
                                                    @Nonnull final Object bean, @Nonnull final String beanName)
            throws BeansException {

        InjectionMetadata metadata = findImplementedMetadata(bean.getClass());
        try {
            metadata.inject(bean, beanName, pvs);
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of implemended by dependencies failed", ex);
        }
        return pvs;
    }

    /**
     * 'Native' processing method for direct calls with an arbitrary target instance,
     * resolving all of its fields and methods which are annotated with <code>@ImplemendedBy</code>.
     * @param bean the target instance to process
     * @throws BeansException if autowiring failed
     */
    public void processInjection(@Nonnull final Object bean) throws BeansException {
        Class<?> clazz = bean.getClass();
        InjectionMetadata metadata = findImplementedMetadata(clazz);
        try {
            metadata.inject(bean, null, null);
        } catch (Throwable ex) {
            throw new BeanCreationException(
                    "Injection of implemended by dependencies failed for class [" + clazz + "]", ex);
        }
    }

    /**
     * Finds all metadata for one class.
     * @param clazz a class
     * @return Returns the injection metadata.
     */
    @Nullable
    private InjectionMetadata findImplementedMetadata(@Nonnull final Class<?> clazz) {
        // Quick check on the concurrent map first, with minimal locking.
        InjectionMetadata metadata = this.injectionMetadataCache.get(clazz);
        if (metadata == null) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(clazz);
                if (metadata == null) {
                    metadata = buildImplementingMetadata(clazz);
                    this.injectionMetadataCache.put(clazz, metadata);
                }
            }
        }
        return metadata;
    }

    /**
     * Build injection metadata.
     * @param clazz a class
     * @return Returns the injection metadata.
     */
    @Nonnull
    private InjectionMetadata buildImplementingMetadata(@Nonnull final Class<?> clazz) {
        LinkedList<InjectionMetadata.InjectedElement> elements = new LinkedList<InjectionMetadata.InjectedElement>();
        Class<?> targetClass = clazz;

        do {
            LinkedList<InjectionMetadata.InjectedElement> currElements =
                    new LinkedList<InjectionMetadata.InjectedElement>();
            for (Field field : targetClass.getDeclaredFields()) {
                Annotation annotation = findImplementedByAnnotation(field);
                if (annotation != null) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("ImplementedBy annotation is not supported on static fields: " + field);
                        }
                        continue;
                    }
                    currElements.add(new ImplementedByFieldElement(field, determineImplementedClass(annotation)));
                }
            }
            elements.addAll(0, currElements);
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null && targetClass != Object.class);

        return new InjectionMetadata(clazz, elements);
    }

    /**
     * Finds the annotation {@link ImplementedBy} on accessible object.
     * @param ao a accessible object
     * @return Returns the annotatio if exists
     */
    @Nullable
    private Annotation findImplementedByAnnotation(@Nonnull final AccessibleObject ao) {
        return ao.getAnnotation(implementedByAnnotationType);
    }

    /**
     * Register the specified bean as dependent on the autowired beans.
     * @param beanName bean name.
     * @param beanNames list of bean's name depending to the <code>beanname</code>.
     */
    private void registerDependentBeans(@Nonnull final String beanName, @Nonnull final Set<String> beanNames) {
        if (beanName != null) {
            for (String name : beanNames) {
                beanFactory.registerDependentBean(name, beanName);
                if (logger.isDebugEnabled()) {
                    logger.debug("Default Implementing by type from bean name '" + beanName + "' to bean named '"
                            + name + "'");
                }
            }
        }
    }

    /**
     * Resolve the specified cached method argument or field value.
     * @param beanName bean name
     * @param cachedArgument the cached argument
     * @return Returns the specified cached method argument or field value.
     */
    @Nullable
    private Object resolvedCachedArgument(@Nonnull final String beanName, @Nullable final Object cachedArgument) {
        if (cachedArgument instanceof DependencyDescriptor) {
            DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            return beanFactory.resolveDependency(descriptor, beanName, null, typeConverter);
        } else if (cachedArgument instanceof RuntimeBeanReference) {
            return beanFactory.getBean(((RuntimeBeanReference) cachedArgument).getBeanName());
        } else {
            return cachedArgument;
        }
    }

    /**
     * Gets the default implementation class of annotation.
     * @param annotation the annotation
     * @return Returns the default implementation class of annotation.
     */
    protected Class<?> determineImplementedClass(@Nonnull final Annotation annotation) {
        return ((ImplementedBy) annotation).value();
    }

    /**
     * Register bean.
     * @param clazz a class to register
     * @param name the name of bean
     * @return Returns the bean definition registered.
     */
    @Nonnull
    public BeanDefinition registerBean(@Nonnull final Class<?> clazz, @Nonnull final String name) {
        String className = clazz.getCanonicalName();
        // create the bean definition
        BeanDefinition beanDefinition = null;
        try {
            beanDefinition = BeanDefinitionReaderUtils.createBeanDefinition(null, className, clazz.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

        // Create the bean - I'm using the class name as the bean name
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return beanDefinition;
    }

    /**
     * Class representing injection information about an annotated field.
     */
    private class ImplementedByFieldElement extends InjectionMetadata.InjectedElement {

        /**
         * 
         */
        private final Class<?> implementedClass;

        /**
         * 
         */
        private volatile boolean cached = false;

        /**
         * 
         */
        private volatile Object cachedFieldValue;

        /**
         * Default contructor.
         * @param field the field to inject
         * @param implementedClass the default implemented class according to field
         */
        public ImplementedByFieldElement(@Nonnull final Field field, @Nonnull final Class<?> implementedClass) {
            super(field, null);
            this.implementedClass = implementedClass;
            checkResourceType(implementedClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void inject(final Object bean, final String beanName, final PropertyValues pvs) throws Throwable {
            Field field = (Field) this.member;
            try {
                Object value = null;
                if (this.cached) {
                    value = resolvedCachedArgument(beanName, this.cachedFieldValue);
                } else {
                    DependencyDescriptor descriptor = new DependencyDescriptor(field, false);
                    Set<String> beanNames = new LinkedHashSet<String>(1);
                    TypeConverter typeConverter = beanFactory.getTypeConverter();
                    try {
                        value = beanFactory.resolveDependency(descriptor, beanName, beanNames, typeConverter);
                    } catch (BeansException ex) {
                    }
                    if (value == null) {
                        registerBean(implementedClass, implementedClass.getCanonicalName());
                        value = beanFactory.resolveDependency(descriptor, beanName, beanNames, typeConverter);
                    }
                    synchronized (this) {
                        if (!this.cached) {
                            if (value != null) {
                                this.cachedFieldValue = descriptor;
                                registerDependentBeans(beanName, beanNames);
                                if (beanNames.size() == 1) {
                                    String autowiredBeanName = beanNames.iterator().next();
                                    if (beanFactory.containsBean(autowiredBeanName)) {
                                        if (beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
                                            this.cachedFieldValue = new RuntimeBeanReference(autowiredBeanName);
                                        }
                                    }
                                }
                            } else {
                                this.cachedFieldValue = null;
                            }
                            this.cached = true;
                        }
                    }
                }
                if (value != null) {
                    ReflectionUtils.makeAccessible(field);
                    field.set(bean, value);
                }
            } catch (Throwable ex) {
                throw new BeanCreationException("Could not inject default implementation field: " + field, ex);
            }
        }
    }

}
