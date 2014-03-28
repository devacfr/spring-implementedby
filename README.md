spring-implementedby
====================

This is implementation of **@ImplementedBy** annotation for SpringFramework 3.x (like Guice).

## Using

If a class need injected a certain type but isn't explicit declared in spring context and no in scope of package scanning, the injector will attempt to a **Just-In-Time binding** (or  JIT bindings and implicit bindings).

**@ImplementedBy** annotation enables implicit bindings in Springframework. for this, this library overrides the default spring autowire annotation post-process. You have to change the autowiring in your spring context file or add manually `ExtendAutowiredAnnotationBeanPostProcessor` using method `addBeanPostProcessor(beanPostProcessor)` in bean factory.

This allows declaring a default implementation for specific interface, abstract class ; more generally a no final class.

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

Above example, **@ImplementedBy** annotation inform spring using `MemoryCache` class as default implementation unless exists declared bean implementing the `Cache` interface.

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

## Why ?

This annotation can be useful to inject default spi implementation in api or declare a default test implementation...

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
