/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import static org.openhab.binding.touchwand.internal.TouchWandBindingConstants.*;

import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TouchWandRestClient} is responsible for handling low level commands units TouchWand WonderFull hub
 * REST API interface
 *
 * @author Roie Geron - Initial contribution
 */

public class TouchWandRestClient {

    private final Logger logger = LoggerFactory.getLogger(TouchWandRestClient.class);

    static CookieManager cookieManager = new CookieManager();

    private String touchWandIpAddr;
    private String touchWandPort;
    private boolean isConnected = false;

    private static final HttpMethod METHOD_GET = HttpMethod.GET;
    private static final HttpMethod METHOD_POST = HttpMethod.POST;

    private static final String CMD_LOGIN = "login";
    private static final String CMD_LIST_UNITS = "listunits";
    private static final String CMD_LIST_SCENARIOS = "listsencarios";
    private static final String CMD_UNIT_ACTION = "action";
    private static final String CMD_GET_UNIT_BY_ID = "getunitbyid";

    private static final String ACTION_SWITCH_OFF = "{\"id\":%s,\"value\":" + SWITCH_STATUS_OFF + "}";
    private static final String ACTION_SWITCH_ON = "{\"id\":%s,\"value\":" + SWITCH_STATUS_ON + "}";
    private static final String ACTION_SHUTTER_DOWN = "{\"id\":%s,\"value\":0,\"type\":\"height\"}";
    private static final String ACTION_SHUTTER_UP = "{\"id\":%s,\"value\":255,\"type\":\"height\"}";
    private static final String ACTION_SHUTTER_STOP = "{\"id\":%s,\"value\":0,\"type\":\"stop\"}";
    private static final String ACTION_SHUTTER_POSITION = "{\"id\":%s,\"value\":%s}";
    private static final String ACTION_DIMMER_POSITION = "{\"id\":%s,\"value\":%s}";

    private static final String CONTENT_TYPE_APPLICATION_JSON = MimeTypes.Type.APPLICATION_JSON.asString();

    private static final int REQUEST_TIMEOUT = 10000; // 10 seconds
    private Map<String, String> commandmap = new HashMap<String, String>();

    private HttpClient httpClient = null;

    public TouchWandRestClient(HttpClient httpClient) {
        commandmap.put(CMD_LOGIN, "/auth/login?");
        commandmap.put(CMD_LIST_UNITS, "/units/listUnits");
        commandmap.put(CMD_LIST_SCENARIOS, "/scenarios/listScenarios");
        commandmap.put(CMD_UNIT_ACTION, "/units/action");
        commandmap.put(CMD_GET_UNIT_BY_ID, "/units/getUnitByID?");

        this.httpClient = httpClient;
    }

    public final boolean connect(String user, String pass, String ipAddr, String port) {
        touchWandIpAddr = ipAddr;
        touchWandPort = port;
        isConnected = cmdLogin(user, pass, ipAddr);

        return isConnected;
    }

    private final boolean cmdLogin(String user, String pass, String ipAddr) {
        String command = buildUrl(CMD_LOGIN) + "user=" + user + "&" + "psw=" + pass;
        String response = sendCommand(command, METHOD_GET, null);
        if (response != null && !response.equals("Unauthorized")) {
            return true;
        }
        return false;
    }

    public String cmdListUnits() {
        String command = buildUrl(CMD_LIST_UNITS);
        String response = sendCommand(command, METHOD_GET, null);
        return response;
    }

    public String cmdGetUnitById(String id) {
        String command = buildUrl(CMD_GET_UNIT_BY_ID) + "id=" + id;
        String response = sendCommand(command, METHOD_GET, null);

        return response;
    }

    public void cmdSwitchOnOff(String id, OnOffType onoff) {
        String action;

        if (OnOffType.OFF.equals(onoff)) {
            action = String.format(ACTION_SWITCH_OFF, id);
        } else {
            action = String.format(ACTION_SWITCH_ON, id);
        }
        cmdUnitAction(action);
    }

    public void cmdShutterUp(String id) {
        String action = String.format(ACTION_SHUTTER_UP, id);
        cmdUnitAction(action);
    }

    public void cmdShutterDown(String id) {
        String action = String.format(ACTION_SHUTTER_DOWN, id);
        cmdUnitAction(action);
    }

    public void cmdShutterPosition(String id, String position) {
        String action = String.format(ACTION_SHUTTER_POSITION, id, position);
        cmdUnitAction(action);
    }

    public void cmdShutterStop(String id) {
        String action = String.format(ACTION_SHUTTER_STOP, id);
        cmdUnitAction(action);
    }

    public void cmdDimmerPosition(String id, String position) {
        String action = String.format(ACTION_DIMMER_POSITION, id, position);
        cmdUnitAction(action);
    }

    private String cmdUnitAction(String action) {
        String command = buildUrl(CMD_UNIT_ACTION);
        String response = sendCommand(command, METHOD_POST, action);

        return response;
    }

    private String buildUrl(String command) {
        String url = "http://" + touchWandIpAddr + ":" + touchWandPort + commandmap.get(command);
        return url;
    }

    private synchronized String sendCommand(String command, HttpMethod method, String content) {
        ContentResponse response;
        Request request;

        URL url = null;
        try {
            url = new URL(command);
        } catch (MalformedURLException e) {
            logger.warn("Error building URL {} : {}", command, e.getMessage());
            return null;
        }

        request = httpClient.newRequest(url.toString()).timeout(REQUEST_TIMEOUT, TimeUnit.SECONDS).method(method);
        if (method.equals(METHOD_POST) && (content != null)) {
            ContentProvider contentProvider = new StringContentProvider(CONTENT_TYPE_APPLICATION_JSON, content,
                    StandardCharsets.UTF_8);
            request = request.content(contentProvider);
        }

        try {
            response = request.send();
            return response.getContentAsString();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Error open connecton to {} : {} ", touchWandIpAddr, e.getMessage());
        }
        return null;
    }
}
