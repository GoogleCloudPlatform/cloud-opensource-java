/*
 * Copyright 2020 Google LLC
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

package com.google.cloud.tools.dependencies.linkagemonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.cloud.tools.dependencies.linkagemonitor.Analytics.PingEvent;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Test;

public class AnalyticsTest {

  private static final ImmutableMap<String, String> EMPTY_MAP = ImmutableMap.of();

  private ConcurrentLinkedQueue<PingEvent> pingEventQueue = new ConcurrentLinkedQueue<>();

  private Analytics analytics = new Analytics("https://example.com", pingEventQueue, false);

  @Test
  public void testPingEventConstructor_nullEventName() {
    try {
      new PingEvent(null, EMPTY_MAP);
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals("eventName null or empty", ex.getMessage());
    }
  }

  @Test
  public void testPingEventConstructor_emptyEventName() {
    try {
      new PingEvent("", EMPTY_MAP);
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals("eventName null or empty", ex.getMessage());
    }
  }

  @Test
  public void testPingEventConstructor_nullMetadata() {
    try {
      new PingEvent("some.event-name", null);
      fail();
    } catch (NullPointerException ex) {
      assertEquals("metadata is null", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingScheduled() {
    analytics.queuePing("eventName", "metadataKey", "metadataValue");
    PingEvent event = pingEventQueue.peek();
    assertEquals("eventName", event.eventName);
    assertEquals(1, event.metadata.size());
    assertTrue(event.metadata.containsKey("metadataKey"));
    assertTrue(event.metadata.containsValue("metadataValue"));
  }

  @Test
  public void testGetAnonymizedClientId_generateNewId() {
    String clientId = Analytics.getAnonymizedClientId();
    assertFalse(clientId.isEmpty());
  }

  @Test
  public void testQueuePingArguments_validEventName() {
    analytics.queuePing("eventName");
  }

  @Test
  public void testQueuePingArguments_nullEventName() {
    try {
      analytics.queuePing(null);
      fail();
    } catch (NullPointerException ex) {
      assertEquals("eventName null", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_emptyEventName() {
    try {
      analytics.queuePing("");
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals("eventName empty", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_validMetadataKeyValue() {
    analytics.queuePing("eventName", "metadataKey", "metadataValue");
  }

  @Test
  public void testQueuePingArguments_nullMetadataKey() {
    try {
      analytics.queuePing("eventName", null, "metadataValue");
      fail();
    } catch (NullPointerException ex) {
      assertEquals("metadataKey null", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_emptyMetadataKey() {
    try {
      analytics.queuePing("eventName", "", "metadataValue");
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals("metadataKey empty", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_nullMetadataValue() {
    try {
      analytics.queuePing("eventName", "metadataKey", null);
      fail();
    } catch (NullPointerException ex) {
      assertEquals("metadataValue null", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_emptyMetadataValue() {
    try {
      analytics.queuePing("eventName", "metadataKey", "");
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals("metadataValue empty", ex.getMessage());
    }
  }

  @Test
  public void testQueuePingArguments_emptyMetadataMap() {
    analytics.queuePing("eventName", EMPTY_MAP);
  } 
  
  @Test
  public void testBuildJson() {
    Gson gson = new Gson();
    ImmutableMap<String, String> metadata = ImmutableMap.of(
        "foo", "bar",
        "bax", "bat");
    PingEvent event = new PingEvent("SomeEvent", metadata);
    String json = analytics.jsonEncode(event);

    Type mapType = new TypeToken<Map<String, ?>>(){}.getType();
    Map<String, ?> root = gson.fromJson(json, mapType);

    Map<String, ?> clientInfo = (Map<String, ?>) root.get("client_info");
    assertEquals("DESKTOP", clientInfo.get("client_type"));  
    assertEquals("CONCORD", root.get("log_source"));

    long requestTimeMs = ((Double) root.get("request_time_ms")).longValue();
    assertTrue(requestTimeMs >= 1000000);
    
    Map<String, String> desktopClientInfo =
        (Map<String, String>) clientInfo.get("desktop_client_info");
    assertTrue(desktopClientInfo.get("os").length() > 1);

    List<Map<String, Object>> logEvents = (List<Map<String, Object>>) root.get("log_event");
    assertEquals(1, logEvents.size());

    Map<String, Object> logEvent = logEvents.get(0);
    long eventTimeMs = ((Double) logEvent.get("event_time_ms")).longValue();
    assertTrue(eventTimeMs >= 1000000);

    String sourceExtensionJson = (String) logEvent.get("source_extension_json");

    // double encoded
    Type sourceExtensionJsonType = new TypeToken<Map<String, ?>>(){}.getType();
    Map<String, ?> source = gson.fromJson(sourceExtensionJson, sourceExtensionJsonType);
    assertEquals("LINKAGE_MONITOR", source.get("console_type"));
    assertEquals("clientId", source.get("client_install_id"));
    assertEquals("SomeEvent", source.get("event_name"));

    List<Map<String, String>> eventMetadata =
        (List<Map<String, String>>) source.get("event_metadata");
    assertEquals(2, eventMetadata.size());

    assertEquals("foo", eventMetadata.get(0).get("key"));
    assertEquals("bar", eventMetadata.get(0).get("value"));

    assertEquals("bax", eventMetadata.get(1).get("key"));
    assertEquals("bat", eventMetadata.get(1).get("value"));
  }
  
}
