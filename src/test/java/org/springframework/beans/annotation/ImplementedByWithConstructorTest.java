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

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author devacfr<christophefriederich@mac.com>
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration()
public class ImplementedByWithConstructorTest {

    @Inject
    private ClassA clazz;

    @Test
    public void injectTest() {
        Assert.assertNotNull(clazz.getInstance());
    }

    @ImplementedBy(DefaultImplementation.class)
    public interface Interface {

    }

    public static class DefaultImplementation implements Interface {

    }

    public static class ClassA {

        private final Interface instance;

        @Autowired(required = true)
        public ClassA(final Interface instance) {
            this.instance = instance;
        }

        /**
         * @return the instance
         */
        public Interface getInstance() {
            return instance;
        }
    }
}
