/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.athenz.common.server.rest;

import com.yahoo.athenz.auth.Authority;
import com.yahoo.athenz.auth.Authorizer;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import org.mockito.Mockito;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class HttpContainerTest {

    @Test
    public void testAuthority() {
        Authority authority = Mockito.mock(Authority.class);
        HttpContainer extend = new HttpContainer();
        assertNotNull(extend.authority(authority));
    }

    @Test
    public void testAuthorizer() {
        Authorizer authorizer = Mockito.mock(Authorizer.class);
        HttpContainer extend = new HttpContainer();
        assertNotNull(extend.authorizer(authorizer));
    }

    @Test
    public void testAbstractBinder() {
        AbstractBinder abstractBinder = Mockito.mock(AbstractBinder.class);
        HttpContainer extend = new HttpContainer();
        assertNotNull(extend.delegate(abstractBinder));
    }

    @Test
    public void testSetBanner() {
        HttpContainer extend = new HttpContainer();
        extend.setBanner("hoge");
        assertEquals(extend.banner, "hoge");
    }

    @Test
    public void testGetServer() {
        HttpContainer extend = new HttpContainer();
        assertNull(extend.getServer());
    }

    @Test
    public void testGetHandlers() {
        HttpContainer extend = new HttpContainer();
        assertNull(extend.getHandlers());
    }

    @Test
    public void testStop() {
        HttpContainer extend = new HttpContainer();
        extend.createServer(20);
        assertNotNull(extend.getHandlers());
        extend.stop();
    }
}
