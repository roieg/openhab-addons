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

package org.openhab.binding.touchwand.internal.discovery;

import static org.openhab.binding.touchwand.internal.TouchWandBindingConstants.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.touchwand.internal.TouchWandBridgeHandler;
import org.openhab.binding.touchwand.internal.data.TouchWandUnitData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link TouchWandUnitDiscoveryService} Discovery service for units.
 *
 * @author Roie Geron - Initial contribution
 */
// @Component(service = DiscoveryService.class, immediate = false, configurationPid = "discovery.touchwand")
public class TouchWandUnitDiscoveryService extends AbstractDiscoveryService {

    private static final int SEARCH_TIME = 10;
    private static final int SCAN_INTERVAL = 120;
    private static final int LINK_DISCOVERY_SERVICE_INITIAL_DELAY = 5;

    private ScheduledFuture<?> scanningJob;

    private final TouchWandUnitScan scanningRunnable;

    private final Logger logger = LoggerFactory.getLogger(TouchWandUnitDiscoveryService.class);

    private final TouchWandBridgeHandler touchWandBridgeHandler;

    public TouchWandUnitDiscoveryService(TouchWandBridgeHandler touchWandBridgeHandler) {
        super(SUPPORTED_THING_TYPES_UIDS, SEARCH_TIME, true);
        this.touchWandBridgeHandler = touchWandBridgeHandler;
        removeOlderResults(getTimestampOfLastScan(), touchWandBridgeHandler.getThing().getUID());
        this.scanningRunnable = new TouchWandUnitScan();
        this.activate();
    }

    @Override
    protected void startScan() {

        if (touchWandBridgeHandler.touchWandClient == null) {
            logger.warn("Could not scan units without bridge handler {}");
            return;
        }

        logger.debug("Starting TouchWand discovery on bridge {}", touchWandBridgeHandler.getThing().getUID());
        String response = touchWandBridgeHandler.touchWandClient.cmdListUnits();
        if (response == null) {
            return;
        }

        logger.debug("Recieved list units respose {}", response);

        JsonParser jsonParser = new JsonParser();
        try {
            JsonArray jsonArray = jsonParser.parse(response).getAsJsonArray();
            if (jsonArray.isJsonArray()) {
                for (JsonElement unit : jsonArray) {
                    JsonObject unitObj = unit.getAsJsonObject();
                    String unitId = unitObj.get("id").getAsString();
                    String name = unitObj.get("name").getAsString();
                    String type = unitObj.get("type").getAsString();
                    String connectivity = unitObj.get("connectivity").getAsString();
                    JsonElement idDataElement = unitObj.get("idData");
                    if (!touchWandBridgeHandler.isAddSecondaryControllerUnits()) {
                        if (!idDataElement.isJsonNull()) {
                            if (!idDataElement.toString().equals("{}")) {
                                logger.debug("Skipped secondary controller unit : {} idData {}", name,
                                        idDataElement.toString());
                                continue;
                            }
                        }
                    }
                    TouchWandUnitData touchWandUnit = new TouchWandUnitData(unitId, name);
                    if (type.equals("Switch")) {
                        addDeviceDiscoveryResult(touchWandUnit, THING_TYPE_SWITCH);
                    } else if (type.equals("shutter")) {
                        addDeviceDiscoveryResult(touchWandUnit, THING_TYPE_SHUTTER);
                    }
                    logger.debug("id is {} name {} type {} connectivity {}", unitId, name, type, connectivity);
                }
            }
        } catch (

        JsonSyntaxException msg) {
            logger.warn("Could not parse list units response {}", msg);
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    public void activate() {
        super.activate(null);
        removeOlderResults(new Date().getTime(), touchWandBridgeHandler.getThing().getUID());
        logger.debug("activate discovery service");
    }

    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime(), touchWandBridgeHandler.getThing().getUID());
        super.deactivate();
        logger.debug("deactivate discovery services");
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Start TouchWand units background discovery");
        if (scanningJob == null || scanningJob.isCancelled()) {
            scanningJob = scheduler.scheduleWithFixedDelay(scanningRunnable, LINK_DISCOVERY_SERVICE_INITIAL_DELAY,
                    SCAN_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stop TouchWand device units discovery");
        if (scanningJob != null && !scanningJob.isCancelled()) {
            scanningJob.cancel(true);
            scanningJob = null;
        }

    }

    public class TouchWandUnitScan implements Runnable {
        @Override
        public void run() {
            startScan();
        }
    }

    private void addDeviceDiscoveryResult(TouchWandUnitData unit, ThingTypeUID typeUID) {
        ThingUID bridgeUID = touchWandBridgeHandler.getThing().getUID();
        ThingUID thingUID = new ThingUID(typeUID, bridgeUID, unit.getUnitId());
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", unit.getUnitId());
        properties.put("name", unit.getUnitName());
        // @formatter:off
        thingDiscovered(DiscoveryResultBuilder.create(thingUID)
                .withThingType(typeUID)
                .withLabel(unit.getUnitName())
                .withBridge(bridgeUID)
                .withProperties(properties)
                .withRepresentationProperty(unit.getUnitId())
                .build()
        );
        // @formatter:on
    }

}
