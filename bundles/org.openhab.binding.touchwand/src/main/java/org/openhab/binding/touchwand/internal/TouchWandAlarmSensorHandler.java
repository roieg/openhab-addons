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

import static org.openhab.binding.touchwand.internal.TouchWandBindingConstants.*;

import java.util.List;

import javax.measure.quantity.Illuminance;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.touchwand.internal.dto.TouchWandAlarmSensorCurrentStatus.BinarySensorEvent;
import org.openhab.binding.touchwand.internal.dto.TouchWandAlarmSensorCurrentStatus.Sensor;
import org.openhab.binding.touchwand.internal.dto.TouchWandUnitData;
import org.openhab.binding.touchwand.internal.dto.TouchWandUnitDataAlarmSensor;

/**
 * The {@link TouchWandAlarmSensorHandler} is responsible for handling command for Alarm Sensor unit
 *
 *
 * @author Roie Geron - Initial contribution
 *
 */
@NonNullByDefault
public class TouchWandAlarmSensorHandler extends TouchWandBaseUnitHandler {

    private static final int BATT_LEVEL_LOW = 20;
    private static final int BATT_LEVEL_LOW_HYS = 5;

    private boolean isBatteryLow = false;

    public TouchWandAlarmSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        super.initialize();

        List<Channel> channels = thing.getChannels();
        ThingBuilder thingBuilder = editThing();
        thingBuilder.withoutChannel(thing.getChannel(CHANNEL_ILLUMINATION));

    }

    @Override
    void updateTouchWandUnitState(TouchWandUnitData unitData) {
        if (unitData instanceof TouchWandUnitDataAlarmSensor) {
            TouchWandUnitDataAlarmSensor sensor = (TouchWandUnitDataAlarmSensor) unitData;
            updateBatteryLevel(sensor);
            updateIllumination(sensor);
            updateChannelLeak(sensor);
            updateChannelDoorWindow(sensor);
            updateChannelMotion(sensor);
            updateChannelTemperature(sensor);
        } else {
            logger.warn("updateTouchWandUnitState incompatible TouchWandUnitData instance");
        }
    }

    @Override
    void touchWandUnitHandleCommand(Command command) {
    }

    void updateBatteryLevel(TouchWandUnitDataAlarmSensor unitData) {
        Integer battLevel = unitData.getCurrStatus().getBatt();
        updateState(CHANNEL_BATTERY_LEVEL, new DecimalType(battLevel));
        int lowThreshold = isBatteryLow ? BATT_LEVEL_LOW + BATT_LEVEL_LOW_HYS : BATT_LEVEL_LOW;
        boolean lowBattery = (battLevel <= lowThreshold);
        updateState(CHANNEL_BATTERY_LOW, OnOffType.from(lowBattery));
        isBatteryLow = lowBattery;
    }

    void updateIllumination(TouchWandUnitDataAlarmSensor unitData) {
        for (Sensor sensor : unitData.getCurrStatus().getSensorsStatus()) {
            if (sensor.type == SENSOR_TYPE_LUMINANCE) {
                updateState(CHANNEL_ILLUMINATION, new QuantityType<Illuminance>(sensor.value, SmartHomeUnits.LUX));
            }
        }
    }

    void updateChannelLeak(TouchWandUnitDataAlarmSensor unitData) {
        for (BinarySensorEvent bSensor : unitData.getCurrStatus().getbSensorsStatus()) {
            if (bSensor.sensorType == SENSOR_TYPE_LEAK) {
                boolean isLeak = bSensor.sensor.state;
                updateState(CHANNEL_LEAK, OnOffType.from(isLeak));
            }
        }
    }

    void updateChannelDoorWindow(TouchWandUnitDataAlarmSensor unitData) {
        for (BinarySensorEvent bSensor : unitData.getCurrStatus().getbSensorsStatus()) {
            if (bSensor.sensorType == SENSOR_TYPE_DOOR_WINDOW) {
                boolean isOpen = bSensor.sensor.state;
                OpenClosedType myOpenClose;
                myOpenClose = isOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
                updateState(CHANNEL_DOORWINDOW, myOpenClose);
            }
        }
    }

    void updateChannelMotion(TouchWandUnitDataAlarmSensor unitData) {
        for (BinarySensorEvent bSensor : unitData.getCurrStatus().getbSensorsStatus()) {
            if (bSensor.sensorType == SENSOR_TYPE_MOTION) {
                boolean hasMotion = bSensor.sensor.state;
                updateState(CHANNEL_MOTION, OnOffType.from(hasMotion));
            }
        }
    }

    void updateChannelTemperature(TouchWandUnitDataAlarmSensor unitData) {
        for (Sensor sensor : unitData.getCurrStatus().getSensorsStatus()) {
            if (sensor.type == SENSOR_TYPE_TEMPERATURE) {
                updateState(CHANNEL_TEMPERATURE, new QuantityType<Temperature>(sensor.value, SIUnits.CELSIUS));
            }
        }
    }
}
