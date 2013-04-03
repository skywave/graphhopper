/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.search;

import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class NameIndexTest {

    @Test
    public void testPut() {
        NameIndex instance = new NameIndex(new RAMDirectory()).createNew(1000);
        int result = instance.put("Something Streetä");
        assertEquals("Something Streetä", instance.get(result));

        int existing = instance.put("Something Streetä");
        assertEquals(result, existing);

        result = instance.put("testing");
        assertEquals("testing", instance.get(result));
    }
}