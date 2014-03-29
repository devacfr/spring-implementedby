spring-implementedby
====================

If you want that a class needs a certain injected type but isn't explicit declared in context and no in scope of package scanning.

**@ImplementedBy** annotation is your solution for SpringFramework 3.x. the injector will attempt to a **Just-In-Time binding** (JIT bindings or implicit bindings). This is based on same Guice feature. 

## Using

In general, **@ImplementedBy** allows declaring a default implementation for specific interface or class. Here is a example how using implicit binding. First, declare the default class on an interface :

	@ImplementedBy(MemoryCache.class)
	public interface Cache {
	
	    /**
	     * Returns the value to which this cache maps the specified key.
	     */
	    Object get(@Nonnull Object key);
	
	    /**
	     * Associate the specified value with the specified key in this cache.
	     */
	    void put(@Nonnull Object key, @Nonnull Object value);
	}

After, let's Spring working:

	public class DefaultUserService implements UserService {
	
	    private final Cache cache;
	    private final UserStore userStore;

	    @Autowired(required=true)
	    public UserService(@Nonnull final Cache cache, @Nonnull final UserStore userStore) {
	        this.cache = notNull(cache, "cache is required");
	        this.userStore = noNull(userStore, "userStore is required");	    }	}

Above example, **@ImplementedBy** annotation informs spring using `MemoryCache` class as default implementation unless exists declared bean implementing the `Cache` interface. This simplifies a certain implementation like:

	public class DefaultUserService implements UserService {
	
	    private Cache cache;
	    private final UserStore userStore;

	    @Autowired()
	    public UserService(@Nullable Cache cache, @Nonnull final UserStore userStore) {
	        this.cache = cache;
	        this.userStore = noNull(userStore, "userStore is required");	    }
	    
	   @PostConstruct()
	   public void afterInitialize() {
	   	if (this.cache == null) {
	   		this.cache = new MemoryCache();	   	}	   }	}


**@ImplementedBy** annotation enables implicit bindings in Springframework. For this, this library overrides the default spring autowire annotation post-process. You have to change the autowiring in your spring context file or add manually `ExtendAutowiredAnnotationBeanPostProcessor` using method `addBeanPostProcessor(beanPostProcessor)` in bean factory.

### Configuration Spring Context File

You are need declare the xml namespace `xmlns:implementedby="http://www.springframework.org/schema/implementedby"` and the schema location `http://www.springframework.org/schema/implementedby/spring-implementedby-1.0.xsd`. You can also prefer to reference the default schema `http://www.springframework.org/schema/implementedby/spring-implementedby.xsd`.

	<?xml version="1.0" encoding="UTF-8"?>
	<beans xmlns="http://www.springframework.org/schema/beans"
	    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
	    xmlns:util="http://www.springframework.org/schema/util"
	    xmlns:implementedby="http://www.springframework.org/schema/implementedby"
	    xsi:schemaLocation="
	                http://www.springframework.org/schema/implementedby http://www.springframework.org/schema/implementedby/spring-implementedby.xsd
	                http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
	                http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd
	                http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util.xsd">
	    <!-- Remove the default implementation -->
	    <!-- <context:annotation-config/> -->
		<!-- Replace by -->
	    <implementedby:annotation-config />
	</beans>****

### Maven Repository

This library is in the bintray repository. Add in your *pom.xml* or *setting.xml*

	<repositories>
	    <repository>
	        <id>bintray</id>
	        <url>http://dl.bintray.com/devacfr/maven</url>
	        <releases>
	            <enabled>true</enabled>
	        </releases>
	        <snapshots>
	            <enabled>false</enabled>
	        </snapshots>
	    </repository>
	</repositories>


## Why ?

**@ImplementedBy** annotation is useful feature, but great power involves great responsibility. Begining of development, I said me "why limits only this annotation to target type?", so I implemented all target (field, method, constructor, parameter...) and after done all tests, I did some soul-searching:

* What's append if  the code references same default implementation in several places and how take avantage of qualifier (naming) ?
* @Autowired (@Inject) became obsolete. Why not ? (ridiculous)
*  But if I implement like Guice and I reduce scope to type, I can't annotate interface of other libraries. Is it useful ? Can I do some other way ?


## Contribution Policy

Contributions via GitHub pull requests are gladly accepted from their original author.
Along with any pull requests, please state that the contribution is your original work and 
that you license the work to the project under the project's open source license.
Whether or not you state this explicitly, by submitting any copyrighted material via pull request, 
email, or other means you agree to license the material under the project's open source license and 
warrant that you have the legal authority to do so.

## Licence

	This software is licensed under the Apache 2 license, quoted below.
	
	Copyright 2014 Christophe Friederich
	
	Licensed under the Apache License, Version 2.0 (the "License"); you may not
	use this file except in compliance with the License. You may obtain a copy of
	the License at http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
	License for the specific language governing permissions and limitations under
	the License.

[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/devacfr/spring-implementedby/trend.png)](https://bitdeli.com/free "Bitdeli Badge")