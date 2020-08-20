/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.touchwand.internal;

import static org.openhab.binding.touchwand.internal.TouchWandBindingConstants.SUPPORTED_TOUCHWAND_TYPES;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.touchwand.internal.dto.TouchWandUnitData;
import org.openhab.binding.touchwand.internal.dto.TouchWandUnitFromJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link TouchWandWebSockets} class implements WebSockets API to TouchWand controller
 *
 * @author Roie Geron - Initial contribution
 */
@NonNullByDefault
public class TouchWandWebSockets {

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int WEBSOCKET_RECONNECT_INTERVAL_SEC = CONNECT_TIMEOUT_SEC * 2;
    private static final int WEBSOCKET_IDLE_TIMEOUT_MS = CONNECT_TIMEOUT_SEC * 10 * 1000;
    private final Logger logger = LoggerFactory.getLogger(TouchWandWebSockets.class);
    private static final String WS_ENDPOINT_TOUCHWAND = "/async";

    private WebSocketClient client;
    private String controllerAddress;
    private TouchWandSocket touchWandSocket;
    private boolean isShutDown = false;
    private List<TouchWandUnitStatusUpdateListener> listeners = new ArrayList<>();
    private @Nullable ScheduledFuture<?> socketReconnect;
    private @Nullable URI uri;

    ScheduledExecutorService scheduler;

    public TouchWandWebSockets(String ipAddress, ScheduledExecutorService scheduler) {
        client = new WebSocketClient();
        touchWandSocket = new TouchWandSocket();
        this.controllerAddress = ipAddress;
        this.scheduler = scheduler;
        socketReconnect = null;
    }

    public void connect() {
        try {
            uri = new URI("ws://" + controllerAddress + WS_ENDPOINT_TOUCHWAND);
        } catch (URISyntaxException e) {
            logger.warn("URI not valid {} message {}", uri, e.getMessage());
            return;
        }

        client.setConnectTimeout(CONNECT_TIMEOUT_SEC);
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setSubProtocols("relay_protocol");

        try {
            client.start();
            client.connect(touchWandSocket, uri, request);
        } catch (Exception e) {
            logger.warn("Could not connect webSocket URI {} message {}", uri, e.getMessage());
            return;
        }
    }

    public void dispose() {
        isShutDown = true;
        try {
            client.stop();
        } catch (Exception e) {
            logger.warn("Could not stop webSocketClient,  message {}", e.getMessage());
        }
    }

    public synchronized void registerListener(TouchWandUnitStatusUpdateListener listener) {
        if (!listeners.contains(listener)) {
            logger.debug("Adding TouchWandWebSocket listener {}", listener);
            listeners.add(listener);
        }
    }

    public synchronized void unregisterListener(TouchWandUnitStatusUpdateListener listener) {
        logger.debug("Removing TouchWandWebSocket listener {}", listener);
        listeners.remove(listener);
    }

    @WebSocket(maxIdleTime = WEBSOCKET_IDLE_TIMEOUT_MS)
    public class TouchWandSocket {

        public TouchWandSocket() {
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            logger.debug("Connection closed: {} - {}", statusCode, reason);
            if (!isShutDown) {
                logger.debug("weSocket Closed - reconnecting");
                asyncWeb();
            }
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            logger.debug("TouchWandWebSockets connected to {}", session.getRemoteAddress().toString());
            try {
                session.getRemote().sendString("{\"myopenhab\": \"myopenhab\"}");
            } catch (IOException e) {
                logger.warn("sendString : {}", e.getMessage());
            }
        }

        @OnWebSocketMessage
        public void onMessage(String msg) {
            TouchWandUnitData touchWandUnit;
            JsonParser jsonParser = new JsonParser();
            try {
                JsonObject unitObj = jsonParser.parse(msg).getAsJsonObject();
                boolean eventUnitChanged = unitObj.get("type").getAsString().equals("UNIT_CHANGED");
                if (!eventUnitChanged) {
                    return;
                }
                touchWandUnit = TouchWandUnitFromJson.parseResponse(unitObj.get("unit").getAsJsonObject());
                if (!touchWandUnit.getStatus().equals("ALIVE")) {
                    return;
                }
                boolean supportedUnitType = Arrays.asList(SUPPORTED_TOUCHWAND_TYPES).contains(touchWandUnit.getType());
                if (!supportedUnitType) {
                    logger.debug("UNIT_CHANGED for unsupported unit type {}", touchWandUnit.getType());
                    return;
                }
                logger.debug("UNIT_CHANGED: name {} id {} status {}", touchWandUnit.getName(), touchWandUnit.getId(),
                        touchWandUnit.getCurrStatus());
                if (touchWandUnit.getCurrStatus() != null) {
                    for (TouchWandUnitStatusUpdateListener listener : listeners) {
                        listener.onDataReceived(touchWandUnit);
                    }
                }
            } catch (JsonSyntaxException e) {
                logger.warn("jsonParser.parse {} ", e.getMessage());
            }
        }

        @OnWebSocketError
        public void onError(Throwable cause) {
            logger.warn("WebSocket Error: {}", cause.getMessage());
            if (!isShutDown) {
                logger.debug("WebSocket onError - reconnecting");
                asyncWeb();
            }
        }

        private void asyncWeb() {
            ScheduledFuture<?> mySocketReconnect = socketReconnect;
            if (mySocketReconnect == null || mySocketReconnect.isDone()) {
                socketReconnect = scheduler.schedule(TouchWandWebSockets.this::connect,
                        WEBSOCKET_RECONNECT_INTERVAL_SEC, TimeUnit.SECONDS);
            }
        }
    }
}
