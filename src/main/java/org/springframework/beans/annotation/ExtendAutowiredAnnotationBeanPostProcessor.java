/**
 * Copyright 2002-2011 the original author or authors.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that autowires annotated fields, setter methods and arbitrary config methods.
 * Such members to be injected are detected through a Java 5 annotation: by default,
 * Spring's {@link Autowired @Autowired} and {@link Value @Value} annotations.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
 * if available, as a direct alternative to Spring's own <code>@Autowired</code>.
 *
 * <p>Only one constructor (at max) of any given bean class may carry this
 * annotation with the 'required' parameter set to <code>true</code>, 
 * indicating <i>the</i> constructor to autowire when used as a Spring bean. 
 * If multiple <i>non-required</i> constructors carry the annotation, they 
 * will be considered as candidates for autowiring. The constructor with 
 * the greatest number of dependencies that can be satisfied by matching
 * beans in the Spring container will be chosen. If none of the candidates
 * can be satisfied, then a default constructor (if present) will be used.
 * An annotated constructor does not have to be public.
 *
 * <p>Fields are injected right after construction of a bean, before any
 * config methods are invoked. Such a config field does not have to be public.
 *
 * <p>Config methods may have an arbitrary name and any number of arguments; each of
 * those arguments will be autowired with a matching bean in the Spring container.
 * Bean property setter methods are effectively just a special case of such a
 * general config method. Config methods do not have to be public.
 *
 * <p>Note: A default AutowiredAnnotationBeanPostProcessor will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom AutowiredAnnotationBeanPostProcessor bean definition.
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection;
 * thus the latter configuration will override the former for properties wired through
 * both approaches.
 * <p><b>NOTE:</b> This is add {@link ImplementedBy} annotation enabling the default implementation
 * for no final class or interface.</p>
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author devacfr<christophefriederich@mac.com>
 * @since 2.5
 * @see #setAutowiredAnnotationType
 * @see Autowired
 * @see Value
 */
public class ExtendAutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter implements
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
     * list of Autowired annotation.
     */
    private final Set<Class<? extends Annotation>> autowiredAnnotationTypes =
            new LinkedHashSet<Class<? extends Annotation>>();

    /**
     * name of required parameter of annotation.
     */
    private String requiredParameterName = "required";

    /**
     * value of required paramater of annotation.
     */
    private boolean requiredParameterValue = true;

    /**
     * order in the postprocessor execution.
     */
    private int order = Ordered.LOWEST_PRECEDENCE - 2;

    /**
     * bean factory.
     */
    private DefaultListableBeanFactory beanFactory;

    /**
     * cache of prefered constructor for classes.
     */
    private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache =
            new ConcurrentHashMap<Class<?>, Constructor<?>[]>();

    /**
     * cache of inject metadata for classes.
     */
    private final Map<Class<?>, InjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<Class<?>, InjectionMetadata>();

    /**
     * Create a new AutowiredAnnotationBeanPostProcessor
     * for Spring's standard {@link Autowired} annotation.
     * <p>Also supports JSR-330's {@link javax.inject.Inject} annotation, if available.
     */
    @SuppressWarnings("unchecked")
    public ExtendAutowiredAnnotationBeanPostProcessor() {
        this.autowiredAnnotationTypes.add(Autowired.class);
        this.autowiredAnnotationTypes.add(Value.class);
        ClassLoader cl = ExtendAutowiredAnnotationBeanPostProcessor.class.getClassLoader();
        try {
            this.autowiredAnnotationTypes.add((Class<? extends Annotation>) cl.loadClass("javax.inject.Inject"));
            logger.info("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
        } catch (ClassNotFoundException ex) {
            // JSR-330 API not available - simply skip.
        }
    }

    /**
     * Set the 'autowired' annotation type, to be used on constructors, fields,
     * setter methods and arbitrary config methods.
     * <p>The default autowired annotation type is the Spring-provided
     * {@link Autowired} annotation, as well as {@link Value}.
     * <p>This setter property exists so that developers can provide their own
     * (non-Spring-specific) annotation type to indicate that a member is
     * supposed to be autowired.
     * @param autowiredAnnotationType Autowired annotation.
     */
    public void setAutowiredAnnotationType(@Nonnull final Class<? extends Annotation> autowiredAnnotationType) {
        Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.add(autowiredAnnotationType);
    }

    /**
     * Set the 'autowired' annotation types, to be used on constructors, fields,
     * setter methods and arbitrary config methods.
     * <p>The default autowired annotation type is the Spring-provided
     * {@link Autowired} annotation, as well as {@link Value}.
     * <p>This setter property exists so that developers can provide their own
     * (non-Spring-specific) annotation types to indicate that a member is
     * supposed to be autowired.
     * @param autowiredAnnotationTypes list of Autowired annotation.
     */
    public void setAutowiredAnnotationTypes(@Nonnull final Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
        Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
    }

    /**
     * Set the name of a parameter of the annotation that specifies
     * whether it is required.
     * @see #setRequiredParameterValue(boolean)
     * @param requiredParameterName name of a requireed parameter of the annotation 
     */
    public void setRequiredParameterName(@Nonnull final String requiredParameterName) {
        this.requiredParameterName = requiredParameterName;
    }

    /**
     * Set the boolean value that marks a dependency as required 
     * <p>For example if using 'required=true' (the default), 
     * this value should be <code>true</code>; but if using 
     * 'optional=false', this value should be <code>false</code>.
     * @see #setRequiredParameterName(String)
     * @param requiredParameterValue value of a requireed parameter of the annotation 
     */
    public void setRequiredParameterValue(@Nonnull final boolean requiredParameterValue) {
        this.requiredParameterValue = requiredParameterValue;
    }

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
        if (beanFactory == null || !(beanFactory instanceof DefaultListableBeanFactory)) {
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
                                                @Nullable final Class<?> beanType, @Nonnull final String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = findAutowiringMetadata(beanType);
            metadata.checkConfigMembers(beanDefinition);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public Constructor<?>[] determineCandidateConstructors(@Nonnull final Class<?> beanClass,
                                                           @Nonnull final String beanName) throws BeansException {
        // Quick check on the concurrent map first, with minimal locking.
        Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
        if (candidateConstructors == null) {
            synchronized (this.candidateConstructorsCache) {
                candidateConstructors = this.candidateConstructorsCache.get(beanClass);
                if (candidateConstructors == null) {
                    Constructor<?>[] rawCandidates = beanClass.getDeclaredConstructors();
                    List<Constructor<?>> candidates = new ArrayList<Constructor<?>>(rawCandidates.length);
                    Constructor<?> requiredConstructor = null;
                    Constructor<?> defaultConstructor = null;
                    for (Constructor<?> candidate : rawCandidates) {
                        Annotation annotation = findAutowiredAnnotation(candidate);
                        if (annotation != null) {
                            if (requiredConstructor != null) {
                                throw new BeanCreationException("Invalid autowire-marked constructor: " + candidate
                                        + ". Found another constructor with 'required' Autowired annotation: "
                                        + requiredConstructor);
                            }
                            if (candidate.getParameterTypes().length == 0) {
                                throw new IllegalStateException("Autowired annotation requires at least one argument: "
                                        + candidate);
                            }
                            boolean required = determineRequiredStatus(annotation);
                            if (required) {
                                if (!candidates.isEmpty()) {
                                    throw new BeanCreationException("Invalid autowire-marked constructors: "
                                            + candidates
                                            + ". Found another constructor with 'required' Autowired annotation: "
                                            + requiredConstructor);
                                }
                                requiredConstructor = candidate;
                            }
                            candidates.add(candidate);
                        } else if (candidate.getParameterTypes().length == 0) {
                            defaultConstructor = candidate;
                        }
                    }
                    if (!candidates.isEmpty()) {
                        // Add default constructor to list of optional constructors, as fallback.
                        if (requiredConstructor == null && defaultConstructor != null) {
                            candidates.add(defaultConstructor);
                        }
                        candidateConstructors = candidates.toArray(new Constructor[candidates.size()]);
                    } else {
                        candidateConstructors = new Constructor[0];
                    }
                    this.candidateConstructorsCache.put(beanClass, candidateConstructors);
                }
            }
        }
        Constructor<?>[] cotrs = (candidateConstructors.length > 0 ? candidateConstructors : null);
        if (cotrs != null) {
            // find default implementation on all constructor canditates
            Set<String> autowiredBeanNames = new LinkedHashSet<String>(1);
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            for (Constructor<?> constructor : cotrs) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    Annotation annot = findImplementedByAnnotation(paramTypes[i]);
                    if (annot != null) {
                        MethodParameter param = MethodParameter.forMethodOrConstructor(constructor, i);
                        DependencyDescriptor descriptor = new DependencyDescriptor(param, false);
                        autowiredBeanNames.clear();
                        resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
                    }
                }
            }
        }
        return cotrs;
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

        InjectionMetadata metadata = findAutowiringMetadata(bean.getClass());
        try {
            metadata.inject(bean, beanName, pvs);
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
        }
        return pvs;
    }

    /**
     * 'Native' processing method for direct calls with an arbitrary target instance,
     * resolving all of its fields and methods which are annotated with <code>@Autowired</code>.
     * @param bean the target instance to process
     * @throws BeansException if autowiring failed
     */
    public void processInjection(@Nonnull final Object bean) throws BeansException {
        Class<?> clazz = bean.getClass();
        InjectionMetadata metadata = findAutowiringMetadata(clazz);
        try {
            metadata.inject(bean, null, null);
        } catch (Throwable ex) {
            throw new BeanCreationException("Injection of autowired dependencies failed for class [" + clazz + "]", ex);
        }
    }

    /**
     * Finds autowiring injection metedata.
     * @param clazz the class 
     * @return Reeturn autowiring injection metedata.
     */
    @Nonnull
    private InjectionMetadata findAutowiringMetadata(@Nonnull final Class<?> clazz) {
        // Quick check on the concurrent map first, with minimal locking.
        InjectionMetadata metadata = this.injectionMetadataCache.get(clazz);
        if (metadata == null) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(clazz);
                if (metadata == null) {
                    metadata = buildAutowiringMetadata(clazz);
                    this.injectionMetadataCache.put(clazz, metadata);
                }
            }
        }
        return metadata;
    }

    /**
     * Finds the annotation {@link ImplementedBy} on class.
     * @param type a class
     * @return Returns the annotatio if exists
     */
    @Nullable
    private Annotation findImplementedByAnnotation(@Nonnull final Class<?> type) {
        return type.getAnnotation(implementedByAnnotationType);
    }

    /**
     * Build injection metadata.
     * @param clazz a class
     * @return Returns the injection metadata.
     */
    private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
        LinkedList<InjectionMetadata.InjectedElement> elements = new LinkedList<InjectionMetadata.InjectedElement>();
        Class<?> targetClass = clazz;

        do {
            LinkedList<InjectionMetadata.InjectedElement> currElements =
                    new LinkedList<InjectionMetadata.InjectedElement>();
            for (Field field : targetClass.getDeclaredFields()) {
                Annotation annotation = findAutowiredAnnotation(field);
                if (annotation != null) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Autowired annotation is not supported on static fields: " + field);
                        }
                        continue;
                    }
                    boolean required = determineRequiredStatus(annotation);
                    currElements.add(new AutowiredFieldElement(field, required));
                }
            }
            for (Method method : targetClass.getDeclaredMethods()) {
                Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
                Annotation annotation = null;
                if (BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                    annotation = findAutowiredAnnotation(bridgedMethod);
                } else {
                    annotation = findAutowiredAnnotation(method);
                }
                if (annotation != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Autowired annotation is not supported on static methods: " + method);
                        }
                        continue;
                    }
                    if (method.getParameterTypes().length == 0) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Autowired annotation should be used on methods with actual parameters: "
                                    + method);
                        }
                    }
                    boolean required = determineRequiredStatus(annotation);
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method);
                    currElements.add(new AutowiredMethodElement(method, required, pd));
                }
            }
            elements.addAll(0, currElements);
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null && targetClass != Object.class);

        return new InjectionMetadata(clazz, elements);
    }

    /**
     * Finds autowired annotation on accessible object {@link AccessibleObject}.
     * @param ao a accessible object.
     * @return Returns finded autowired annotation
     */
    @Nullable
    private Annotation findAutowiredAnnotation(@Nonnull final AccessibleObject ao) {
        for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
            Annotation annotation = ao.getAnnotation(type);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * Obtains all beans of the given type as autowire candidates.
     * @param type the type of the bean
     * @return the target beans, or an empty Collection if no bean of this type is found
     * @throws BeansException if bean retrieval failed
     * @param <T> type of candidate class.
     */
    @Nonnull
    protected <T> Map<String, T> findAutowireCandidates(final Class<T> type) throws BeansException {
        if (this.beanFactory == null) {
            throw new IllegalStateException("No BeanFactory configured - "
                    + "override the getBeanOfType method or specify the 'beanFactory' property");
        }
        return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
    }

    /**
     * Determines if the annotated field or method requires its dependency.
     * <p>A 'required' dependency means that autowiring should fail when no beans
     * are found. Otherwise, the autowiring process will simply bypass the field
     * or method when no beans are found.
     * @param annotation the Autowired annotation
     * @return whether the annotation indicates that a dependency is required
     */
    protected boolean determineRequiredStatus(final Annotation annotation) {
        try {
            Method method = ReflectionUtils.findMethod(annotation.annotationType(), this.requiredParameterName);
            return (this.requiredParameterValue == (Boolean) ReflectionUtils.invokeMethod(method, annotation));
        } catch (Exception ex) {
            // required by default
            return true;
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
     * Registers bean.
     * @param clazz a class to register
     * @param name the name of bean
     * @return Returns the bean definition registered.
     */
    @Nonnull
    protected BeanDefinition registerBean(@Nonnull final Class<?> clazz, @Nonnull final String name) {
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
     * Register the specified bean as dependent on the autowired beans.
     * @param beanName bean name.
     * @param beanNames list of bean's name depending to the <code>beanname</code>.
     */
    private void registerDependentBeans(final String beanName, final Set<String> beanNames) {
        if (beanName != null) {
            for (String autowiredBeanName : beanNames) {
                beanFactory.registerDependentBean(autowiredBeanName, beanName);
                if (logger.isDebugEnabled()) {
                    logger.debug("Autowiring by type from bean name '" + beanName + "' to bean named '"
                            + autowiredBeanName + "'");
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
     * Resolve the specified dependency against the beans defined in this factory.
     * @param descriptor Resolve the specified dependency against the beans defined in this factory.
     * @param beanName the name of the bean which declares the present dependency
     * @param autowiredBeanNames a Set that all names of autowired beans (used for resolving the present dependency) 
     * are supposed to be added to
     * @param typeConverter the TypeConverter to use for populating arrays and collections
     * @return Returns the resolved object, or null if none found
     */
    protected Object resolveDependency(@Nonnull final DependencyDescriptor descriptor, @Nonnull final String beanName,
                                       @Nonnull final Set<String> autowiredBeanNames,
                                       @Nonnull final TypeConverter typeConverter) {
        Object value = null;
        try {
            value = beanFactory.resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
        } catch (BeansException ex) {
        }
        if (value == null && registerDefaultDependency(descriptor.getDependencyType())) {
            value = beanFactory.resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
        }
        return value;
    }

    /**
     * Register the default implementation.
     * @param declaredClass the class
     * @return Returns <code>true</code> if register the default implementation, otherwise <code>false</code>.
     */
    protected boolean registerDefaultDependency(@Nonnull final Class<?> declaredClass) {
        Annotation annot = findImplementedByAnnotation(declaredClass);
        if (annot != null) {
            Class<?> implementedClass = determineImplementedClass(annot);
            registerBean(implementedClass, implementedClass.getCanonicalName());
            return true;
        }
        return false;
    }

    /**
     * Class representing injection information about an annotated field.
     */
    private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {

        /**
         * indicating whether autowired of field is required.
         */
        private final boolean required;

        /**
         * indicating wether the value is cached.
         */
        private volatile boolean cached = false;

        /**
         * the cached value.
         */
        private volatile Object cachedFieldValue;

        /**
         * Default constructor.
         * @param field the field to inject
         * @param required indicating whether autowired of field is required.
         */
        public AutowiredFieldElement(@Nonnull final Field field, @Nonnull final boolean required) {
            super(field, null);
            this.required = required;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void inject(@Nonnull final Object bean, @Nonnull final String beanName,
                              @Nonnull final PropertyValues pvs) throws Throwable {
            Field field = (Field) this.member;
            try {
                Object value = null;
                if (this.cached) {
                    value = resolvedCachedArgument(beanName, this.cachedFieldValue);
                } else {
                    DependencyDescriptor descriptor = new DependencyDescriptor(field, this.required);
                    Set<String> autowiredBeanNames = new LinkedHashSet<String>(1);
                    TypeConverter typeConverter = beanFactory.getTypeConverter();
                    value = resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
                    synchronized (this) {
                        if (!this.cached) {
                            if (value != null || this.required) {
                                this.cachedFieldValue = descriptor;
                                registerDependentBeans(beanName, autowiredBeanNames);
                                if (autowiredBeanNames.size() == 1) {
                                    String autowiredBeanName = autowiredBeanNames.iterator().next();
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
                throw new BeanCreationException("Could not autowire field: " + field, ex);
            }
        }
    }

    /**
     * Class representing injection information about an annotated method.
     */
    private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {

        /**
         * indicating whether autowired of method is required.
         */
        private final boolean required;

        /**
         * indicating wether the value is cached.
         */
        private volatile boolean cached = false;

        /**
         * the cached values.
         */
        private volatile Object[] cachedMethodArguments;

        /**
         * Default constructor.
         * @param method method to inject
         * @param required indicating whether autowired of method is required
         * @param pd property descriptor of method
         */
        public AutowiredMethodElement(final Method method, final boolean required, final PropertyDescriptor pd) {
            super(method, pd);
            this.required = required;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void inject(final Object bean, final String beanName, final PropertyValues pvs) throws Throwable {
            if (checkPropertySkipping(pvs)) {
                return;
            }
            Method method = (Method) this.member;
            try {
                Object[] arguments;
                if (this.cached) {
                    // Shortcut for avoiding synchronization...
                    arguments = resolveCachedArguments(beanName);
                } else {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    arguments = new Object[paramTypes.length];
                    DependencyDescriptor[] descriptors = new DependencyDescriptor[paramTypes.length];
                    Set<String> autowiredBeanNames = new LinkedHashSet<String>(paramTypes.length);
                    TypeConverter typeConverter = beanFactory.getTypeConverter();
                    for (int i = 0; i < arguments.length; i++) {
                        MethodParameter methodParam = new MethodParameter(method, i);
                        GenericTypeResolver.resolveParameterType(methodParam, bean.getClass());
                        descriptors[i] = new DependencyDescriptor(methodParam, this.required);
                        arguments[i] = resolveDependency(descriptors[i], beanName, autowiredBeanNames, typeConverter);
                        if (arguments[i] == null) {

                            Class<?> declaredClass = descriptors[i].getDependencyType();
                            Annotation annot = findImplementedByAnnotation(declaredClass);
                            if (annot != null) {
                                Class<?> implementedClass = determineImplementedClass(annot);
                                registerBean(implementedClass, implementedClass.getCanonicalName());
                                arguments[i] =
                                        beanFactory.resolveDependency(descriptors[i],
                                            beanName,
                                            autowiredBeanNames,
                                            typeConverter);
                            }
                        }
                        if (arguments[i] == null && !this.required) {
                            arguments = null;
                            break;
                        }
                    }
                    synchronized (this) {
                        if (!this.cached) {
                            if (arguments != null) {
                                this.cachedMethodArguments = new Object[arguments.length];
                                for (int i = 0; i < arguments.length; i++) {
                                    this.cachedMethodArguments[i] = descriptors[i];
                                }
                                registerDependentBeans(beanName, autowiredBeanNames);
                                if (autowiredBeanNames.size() == paramTypes.length) {
                                    Iterator<String> it = autowiredBeanNames.iterator();
                                    for (int i = 0; i < paramTypes.length; i++) {
                                        String autowiredBeanName = it.next();
                                        if (beanFactory.containsBean(autowiredBeanName)) {
                                            if (beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
                                                this.cachedMethodArguments[i] =
                                                        new RuntimeBeanReference(autowiredBeanName);
                                            }
                                        }
                                    }
                                }
                            } else {
                                this.cachedMethodArguments = null;
                            }
                            this.cached = true;
                        }
                    }
                }
                if (arguments != null) {
                    ReflectionUtils.makeAccessible(method);
                    method.invoke(bean, arguments);
                }
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            } catch (Throwable ex) {
                throw new BeanCreationException("Could not autowire method: " + method, ex);
            }
        }

        /**
         * Resolve the specified cached method arguments.
         * @param beanName bean name
         * @return Returns the specified cached method arguments.
         */
        @Nullable
        private Object[] resolveCachedArguments(@Nonnull final String beanName) {
            if (this.cachedMethodArguments == null) {
                return null;
            }
            Object[] arguments = new Object[this.cachedMethodArguments.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = resolvedCachedArgument(beanName, this.cachedMethodArguments[i]);
            }
            return arguments;
        }
    }

}
