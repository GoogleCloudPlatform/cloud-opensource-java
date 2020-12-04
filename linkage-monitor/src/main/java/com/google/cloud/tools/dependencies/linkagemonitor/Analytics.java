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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Report events to
 * <a href='https://g3doc.corp.google.com/company/teams/concord/integration/quick_start.md?cl=head'>Clearcut<a/>.
 */
final class Analytics {

  // TODO is this a good value?
  private static final int DELAY = 773; // a prime number

  private static final Logger logger = Logger.getLogger(Analytics.class.getName());

  private static final String FIRELOG_COLLECTION_URL =
      "https://firebaselogging-pa.googleapis.com/v1/firelog/legacy/log";

  // Don't change the value; this name is used as an originating "application" of usage metrics.
  private static final String METRICS_NAME = "cloud-opensource-java-linkage-monitor";
  
  // for Concord Clearcut
  private static final String CONSOLE_TYPE = "LINKAGE_MONITOR";
  
  private static Analytics instance;

  private final String collectionUrl;

  private final ConcurrentLinkedQueue<PingEvent> pingEventQueue;
  
  private int sequencePosition = 0;

  private final boolean sendAnalytics;
  
  @VisibleForTesting
  Analytics(String collectionUrl, 
      ConcurrentLinkedQueue<PingEvent> concurrentLinkedQueue, boolean sendAnalytics) {
    this.collectionUrl = collectionUrl;
    this.pingEventQueue = concurrentLinkedQueue;
    this.sendAnalytics = sendAnalytics;
  }

  // TODO any synchronization issues here? triple check that this makes sense.
  private void start() {
    Runnable emptyQueue = new Runnable() {
      @Override
      public void run() {
        // drain the queue; GA can handle our traffic without buffering
        PingEvent event = pingEventQueue.poll();
        while (event != null) {
          if (sendAnalytics && event != null) {
            ping(event);
          }
          event = pingEventQueue.poll();
        }
      }
    };
    
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    
    // getExitingScheduledExecutorService doesn't block app from exiting
    ScheduledExecutorService scheduler = MoreExecutors.getExitingScheduledExecutorService(
        executor, 10, TimeUnit.SECONDS);

    scheduler.scheduleAtFixedRate(emptyQueue, DELAY, DELAY, TimeUnit.MILLISECONDS);
  }

  static synchronized Analytics getInstance(boolean sendAnalytics) {
    if (instance == null) {
      String collectionUrl = null;
      if (!PlaceholderConstants.FIRELOG_API_KEY.startsWith("@")) {
        collectionUrl = FIRELOG_COLLECTION_URL + "?key=" + PlaceholderConstants.FIRELOG_API_KEY;
      }
      instance = new Analytics(collectionUrl, new ConcurrentLinkedQueue<>(), sendAnalytics);
      instance.start();
    }
    return instance;
  }

  /**
   * Sends a usage metric to Google Analytics.
   */
  void queuePing(String eventName, String metadataKey, String metadataValue) {
    Preconditions.checkNotNull(metadataKey, "metadataKey null");
    Preconditions.checkArgument(!metadataKey.isEmpty(), "metadataKey empty");
    Preconditions.checkNotNull(metadataValue, "metadataValue null");
    Preconditions.checkArgument(!metadataValue.isEmpty(), "metadataValue empty");

    queuePing(eventName, ImmutableMap.of(metadataKey, metadataValue));
  }

  /**
   * Sends a usage metric to Google Analytics.
   */
  void queuePing(String eventName, Map<String, String> metadata) {
    Preconditions.checkNotNull(eventName, "eventName null");
    Preconditions.checkArgument(!eventName.isEmpty(), "eventName empty");
    Preconditions.checkNotNull(metadata);

    if (collectionUrl != null) {
      ImmutableMap<String, String> metadataCopy = ImmutableMap.copyOf(metadata);
      pingEventQueue.add(new PingEvent(eventName, metadataCopy));
    }
  }
  
  void queuePing(String eventName) {
    queuePing(eventName, ImmutableMap.<String, String>of());
  }

  /**
   * This is the only method that makes an HTTP connection. Everything else
   * ultimately funnels through here. 
   */
  private void ping(PingEvent pingEvent) {
    if (sendAnalytics && pingEvent != null) {
      try {
        String json = jsonEncode(pingEvent);
        int resultCode = HttpUtil.sendPost(collectionUrl, json, "application/json");
        if (resultCode >= 300) {
          logger.log(Level.FINE, "Failed to POST to Concord with HTTP return code " + resultCode);
        }
      } catch (IOException ex) {
        // Don't recover or retry.
        logger.log(Level.FINE, "Failed to POST to Concord", ex);
      } 
    }
  }

  @VisibleForTesting
  static class PingEvent {
    @VisibleForTesting final String eventName;
    @VisibleForTesting final ImmutableMap<String, String> metadata;

    PingEvent(String eventName, ImmutableMap<String, String> metadata) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(eventName), "eventName null or empty");
      Preconditions.checkNotNull(metadata, "metadata is null");
      this.eventName = eventName;
      this.metadata = metadata;
    }
  }

  @VisibleForTesting
  String jsonEncode(PingEvent event) {
    Gson gson = new Gson();

    Map<String, String> desktopClientInfo = new HashMap<>();
    desktopClientInfo.put("os", System.getProperty("os.name"));
    
    Map<String, Object> clientInfo = new HashMap<>();
    clientInfo.put("client_type", "DESKTOP");
    clientInfo.put("desktop_client_info", desktopClientInfo);
        
    // logs/proto/cloud/concord/concord_event.proto
    Map<String, Object> sourceExtension = new HashMap<>();
    sourceExtension.put("client_install_id", getAnonymizedClientId());
    sourceExtension.put("console_type", CONSOLE_TYPE);
    sourceExtension.put("event_name", event.eventName);
    
    Map<String, String> metadataMap = new LinkedHashMap<>(event.metadata);

    List<Map<String, String>> metadataList = new ArrayList<>();
    for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
      Map<String, String> keyValue = new HashMap<>();
      keyValue.put("key", entry.getKey());
      keyValue.put("value", entry.getValue());
      metadataList.add(keyValue);
    }
    sourceExtension.put("event_metadata", metadataList);
    
    String sourceExtensionJsonString = gson.toJson(sourceExtension);
    
    Map<String, Object> logEvent = new HashMap<>();
    logEvent.put("event_time_ms", System.currentTimeMillis());
    logEvent.put("sequence_position", sequencePosition++);  
    logEvent.put("source_extension_json", sourceExtensionJsonString);
    
    Map<String, Object> root = new HashMap<>();
    root.put("log_source", "CONCORD");
    root.put("request_time_ms", System.currentTimeMillis());
    root.put("client_info", clientInfo);
    root.put("log_event", Collections.singletonList(logEvent));

    return gson.toJson(root);
  }

  @VisibleForTesting
  static String getAnonymizedClientId() {
    return "clientId"; // TODO fill in with what????";
  }
}
