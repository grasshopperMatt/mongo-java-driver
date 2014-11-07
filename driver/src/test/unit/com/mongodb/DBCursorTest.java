/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
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
 */

package com.mongodb;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static com.mongodb.ClusterFixture.disableMaxTimeFailPoint;
import static com.mongodb.ClusterFixture.enableMaxTimeFailPoint;
import static com.mongodb.ClusterFixture.isSharded;
import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class DBCursorTest extends DatabaseTestCase {
    private static final int NUMBER_OF_DOCUMENTS = 10;
    private DBCursor cursor;

    @Before
    public void setUp() {
        super.setUp();
        for (int i = 0; i < NUMBER_OF_DOCUMENTS; i++) {
            collection.insert(new BasicDBObject("_id", i).append("x", i));
        }
        collection.createIndex(new BasicDBObject("x", 1));

        cursor = collection.find();
    }

    @After
    public void after() {
        if (cursor != null) {
            cursor.close();
        }
    }

    @Test
    public void testNextHasNext() {
        cursor.sort(new BasicDBObject("_id", 1));
        int i = 0;
        while (cursor.hasNext()) {
            DBObject cur = cursor.next();
            assertEquals(i, cur.get("_id"));
            i++;
        }

        try {
            cursor.next();
            fail();
        } catch (NoSuchElementException e) {
            // all good
        }
    }

    @Test
    public void testCurr() {
        assertNull(cursor.curr());
        DBObject next = cursor.next();
        assertEquals(next, cursor.curr());
        next = cursor.next();
        assertEquals(next, cursor.curr());
    }

    @Test
    public void testMarkPartial() {
        DBCursor markPartialCursor = collection.find(new BasicDBObject(), new BasicDBObject("_id", 1));
        assertTrue(markPartialCursor.next().isPartialObject());
    }

    @Test
    public void testMarkPartialForEmptyObjects() {
        DBCursor cursor = collection.find(new BasicDBObject(), new BasicDBObject("_id", 0));
        for (final DBObject document : cursor) {
            assertTrue(document.isPartialObject());
        }
    }

    @Test
    public void testIterator() {
        cursor.sort(new BasicDBObject("_id", 1));
        Iterator<DBObject> iter = cursor.iterator();
        int i = 0;
        while (iter.hasNext()) {
            DBObject cur = iter.next();
            assertEquals(i, cur.get("_id"));
            i++;
        }
    }

    @Test
    public void testCopy() {
        DBCursor cursorCopy = cursor.copy();
        assertEquals(cursor.getCollection(), cursorCopy.getCollection());
        assertEquals(cursor.getQuery(), cursorCopy.getQuery());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        cursor.remove();
    }

    @Test
    public void testLimit() {
        DBCollection c = collection;
        collection.drop();
        for (int i = 0; i < 100; i++) {
            c.save(new BasicDBObject("x", i));
        }

        DBCursor dbCursor = c.find();
        try {
            assertEquals(0, dbCursor.getLimit());
            assertEquals(0, dbCursor.getBatchSize());
            assertEquals(100, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }

        dbCursor = c.find().limit(50);
        try {
            assertEquals(50, dbCursor.getLimit());
            assertEquals(50, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }

        dbCursor = c.find().limit(-50);
        try {
            assertEquals(-50, dbCursor.getLimit());
            assertEquals(50, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testBatchSize() {
        DBCollection c = collection;
        collection.drop();
        for (int i = 0; i < 100; i++) {
            c.save(new BasicDBObject("x", i));
        }

        DBCursor dbCursor = c.find().batchSize(0);
        try {
            assertEquals(0, dbCursor.getBatchSize());
            assertEquals(100, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }

        dbCursor = c.find().batchSize(50);
        try {
            assertEquals(50, dbCursor.getBatchSize());
            assertEquals(100, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }

        dbCursor = c.find().batchSize(-50);
        try {
            assertEquals(-50, dbCursor.getBatchSize());
            assertEquals(50, dbCursor.toArray().size());
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testSkip() {
        DBCursor cursor = collection.find().skip(2);
        try {
            assertEquals(8, cursor.toArray().size());
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testGetCursorId() {
        DBCursor cursor = collection.find().limit(2);
        assertEquals(0, cursor.getCursorId());
        cursor.hasNext();
        assertNotEquals(0, cursor.getCursorId());
    }

    @Test
    public void testGetServerAddress() {
        DBCursor cursor = collection.find().limit(2);
        assertEquals(null, cursor.getServerAddress());
        cursor.hasNext();
        assertTrue(getClient().getServerAddressList().contains(cursor.getServerAddress()));
    }

    @Test
    public void getNumSeen() {
        DBCursor cursor = collection.find();
        assertEquals(0, cursor.numSeen());
        cursor.hasNext();
        assertEquals(0, cursor.numSeen());
        cursor.next();
        assertEquals(1, cursor.numSeen());
        cursor.next();
        assertEquals(2, cursor.numSeen());
    }

    @Test
    public void testLength() {
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.length());
    }

    @Test
    public void testToArray() {
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.toArray().size());
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.toArray().size());
    }

    @Test
    public void testToArrayWithMax() {
        assertEquals(9, cursor.toArray(9).size());
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.toArray().size());
    }

    @Test
    public void testIterationCount() {
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.itcount());
    }

    @Test
    public void testCount() {
        assertEquals(NUMBER_OF_DOCUMENTS, cursor.count());
        assertEquals(1, collection.find(new BasicDBObject("_id", 1)).count());
    }

    @Test
    public void testGetKeysWanted() {
        assertNull(cursor.getKeysWanted());
        DBObject keys = new BasicDBObject("x", 1);
        DBCursor cursorWithKeys = collection.find(new BasicDBObject(), keys);
        assertEquals(keys, cursorWithKeys.getKeysWanted());
    }

    @Test
    public void testGetQuery() {
        assertEquals(new BasicDBObject(), cursor.getQuery());
        DBObject query = new BasicDBObject("x", 1);
        DBCursor cursorWithQuery = collection.find(query);
        assertEquals(query, cursorWithQuery.getQuery());
    }

    @Test
    public void testReadPreference() {
        assertEquals(ReadPreference.primary(), cursor.getReadPreference());
        cursor.setReadPreference(ReadPreference.secondary());
        assertEquals(ReadPreference.secondary(), cursor.getReadPreference());
    }

    @Test
    public void testConstructor() {
        DBObject query = new BasicDBObject("x", 1);
        DBObject keys = new BasicDBObject("x", 1).append("y", 1);
        DBCursor local = new DBCursor(collection, query, keys, ReadPreference.secondary());
        assertEquals(ReadPreference.secondary(), local.getReadPreference());
        assertEquals(query, local.getQuery());
        assertEquals(keys, local.getKeysWanted());
    }

    @Test
    public void testMaxScan() {
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                     .addSpecial("$maxScan", 4), 4);
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary()).maxScan(4), 4);
    }

    @Test
    public void testMax() {
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                     .addSpecial("$max", new BasicDBObject("x", 4)), 4);
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                     .max(new BasicDBObject("x", 4)), 4);
    }

    @Test
    public void testMin() {
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                     .addSpecial("$min", new BasicDBObject("x", 4)), 6);
        countResults(new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                     .min(new BasicDBObject("x", 4)), 6);
    }

    @Test
    public void testReturnKey() {
        DBCursor cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                          .addSpecial("$returnKey", true);
        try {
            while (cursor.hasNext()) {
                Assert.assertNull(cursor.next()
                                        .get("_id"));
            }
        } finally {
            cursor.close();
        }
        cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                 .returnKey();
        try {
            while (cursor.hasNext()) {
                Assert.assertNull(cursor.next()
                                        .get("_id"));
            }
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testShowDiskLoc() {
        DBCursor cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                          .addSpecial("$showDiskLoc", true);
        try {
            while (cursor.hasNext()) {
                DBObject next = cursor.next();
                Assert.assertNotNull(next.toString(), next.get("$diskLoc"));
            }
        } finally {
            cursor.close();
        }
        cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                 .showDiskLoc();
        try {
            while (cursor.hasNext()) {
                DBObject next = cursor.next();
                Assert.assertNotNull(next.toString(), next.get("$diskLoc"));
            }
        } finally {
            cursor.close();
        }
    }

    @Test
    public void testSnapshot() {
        DBCursor cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                          .addSpecial("$snapshot", true);
        try {
            while (cursor.hasNext()) {
                cursor.next();
            }
        } finally {
            cursor.close();
        }
    }

    private void countResults(final DBCursor cursor, final int expected) {
        int count = 0;
        while (cursor.hasNext()) {
            cursor.next();
            count++;
        }
        cursor.close();
        assertEquals(expected, count);
    }

    @Test
    public void testSettingACommentInsertsCommentIntoProfileCollectionWhenProfilingIsTurnedOn() {
        assumeFalse(isSharded());

        // given
        database.command(new BasicDBObject("profile", 2));
        String expectedComment = "test comment";

        // when
        DBCursor cursor = new DBCursor(collection, new BasicDBObject(), new BasicDBObject(), ReadPreference.primary())
                          .comment(expectedComment);
        while (cursor.hasNext()) {
            cursor.next();
        }

        // then
        DBCollection profileCollection = database.getCollection("system.profile");
        assertEquals(1, profileCollection.count());
        assertEquals(expectedComment, ((DBObject) profileCollection.findOne().get("query")).get("$comment"));

        // finally
        database.command(new BasicDBObject("profile", 0));
        profileCollection.drop();
    }

    @Test
    public void testShouldReturnOnlyTheFieldThatWasInTheIndexUsedForTheFindWhenReturnKeyIsUsed() {
        // Given
        // put some documents into the collection
        for (int i = 0; i < NUMBER_OF_DOCUMENTS; i++) {
            collection.insert(new BasicDBObject("y", i).append("someOtherKey", "someOtherValue"));
        }
        //set an index on the field "y"
        collection.createIndex(new BasicDBObject("y", 1));

        // When
        // find a document by using a search on the field in the index
        DBCursor cursor = collection.find(new BasicDBObject("y", 7))
                                    .returnKey();

        // Then
        DBObject foundItem = cursor.next();
        assertThat("There should only be one field in the resulting document", foundItem.keySet().size(), is(1));
        assertThat("This should be the 'y' field with its value", (Integer) foundItem.get("y"), is(7));
    }

    @Test
    public void testMaxTimeForIterator() {
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.maxTime(1, TimeUnit.SECONDS);
        try {
            cursor.hasNext();
            fail("Show have thrown");
        } catch (MongoExecutionTimeoutException e) {
            assertEquals(50, e.getCode());
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    public void testMaxTimeForIterable() {
        assumeFalse(isSharded());
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.maxTime(1, TimeUnit.SECONDS);
        try {
            cursor.iterator().hasNext();
            fail("Show have thrown");
        } catch (MongoExecutionTimeoutException e) {
            assertEquals(50, e.getCode());
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    public void testMaxTimeForOne() {
        assumeFalse(isSharded());
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.maxTime(1, TimeUnit.SECONDS);
        try {
            cursor.one();
            fail("Show have thrown");
        } catch (MongoExecutionTimeoutException e) {
            assertEquals(50, e.getCode());
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    public void testMaxTimeForCount() {
        assumeFalse(isSharded());
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.maxTime(1, TimeUnit.SECONDS);
        try {
            cursor.count();
            fail("Show have thrown");
        } catch (MongoExecutionTimeoutException e) {
            assertEquals(50, e.getCode());
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    public void testMaxTimeForSize() {
        assumeFalse(isSharded());
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.maxTime(1, TimeUnit.SECONDS);
        try {
            cursor.size();
            fail("Show have thrown");
        } catch (MongoExecutionTimeoutException e) {
            assertEquals(50, e.getCode());
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void exhaustOptionShouldThrowUnsupportedOperationException() {
        DBCursor cursor = new DBCursor(collection, new BasicDBObject("x", 1), new BasicDBObject(), ReadPreference.primary());
        cursor.addOption(Bytes.QUERYOPTION_EXHAUST);
    }

    @Test
    public void testClose() {
        cursor.next();
        cursor.close();
        try {
            cursor.next();
            fail();
        } catch (IllegalStateException e) {
            // all good
        }
    }
}
