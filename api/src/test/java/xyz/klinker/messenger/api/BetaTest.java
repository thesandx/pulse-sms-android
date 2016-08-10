/*
 * Copyright (C) 2016 Jacob Klinker
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

package xyz.klinker.messenger.api;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class BetaTest extends ApiTest {

    @Test
    public void registerAndRemove() {
        String accountId = getAccountId();
        Object response = api.beta().register(accountId);
        assertNotNull(response);

        response = api.beta().remove(accountId);
        assertNotNull(response);
    }

}
