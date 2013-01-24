/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.mongodb.impl;

import org.bson.types.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mongodb.MongoClient;
import org.mongodb.MongoClients;
import org.mongodb.MongoCollection;
import org.mongodb.MongoReadPreferenceException;
import org.mongodb.ReadPreference;
import org.mongodb.ServerAddress;

import java.net.UnknownHostException;
import java.util.Arrays;

@Ignore
public class ReplicaSetMongoClientTest {
    private MongoClient client;
    private MongoCollection<Document> collection;

    @Before
    public void before() throws UnknownHostException {
        client = MongoClients.create(Arrays.asList(new ServerAddress()));
        collection = client.getDatabase("ReplicaSetMongoClientTest")
                           .getCollection("Collection" + System.currentTimeMillis());
        collection.admin().drop();
    }

    @After
    public void after() {
        client.close();
    }

    @Test
    public void shouldFindPrimary() throws UnknownHostException {
        collection.insert(new Document("a", 1));

        collection.count();
    }

    @Test(expected = MongoReadPreferenceException.class)
    public void shouldThrowReadPreferenceException() throws UnknownHostException {
        collection.readPreference(ReadPreference.nearest(new Document("fakeTag", "fakeValue"))).count();
    }
}
