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

package org.openhab.binding.ipcamera.internal;

import static org.openhab.binding.ipcamera.internal.IpCameraBindingConstants.*;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ipcamera.internal.IpCameraBindingConstants.FFmpegFormat;
import org.openhab.binding.ipcamera.internal.handler.IpCameraHandler;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

/**
 * The {@link DahuaHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Matthew Skinner - Initial contribution
 */

@NonNullByDefault
public class DahuaHandler extends ChannelDuplexHandler {
    private IpCameraHandler ipCameraHandler;
    private int nvrChannel;

    public DahuaHandler(IpCameraHandler handler, int nvrChannel) {
        ipCameraHandler = handler;
        this.nvrChannel = nvrChannel;
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(@Nullable ChannelHandlerContext ctx, @Nullable Object msg) throws Exception {
        if (msg == null || ctx == null) {
            return;
        }
        String content = msg.toString();
        try {
            if (!content.isEmpty()) {
                ipCameraHandler.logger.trace("HTTP Result back from camera is \t:{}:", content);
            }
            // determine if the motion detection is turned on or off.
            if (content.contains("table.MotionDetect[0].Enable=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            } else if (content.contains("table.MotionDetect[" + nvrChannel + "].Enable=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            }
            // Handle motion alarm
            if (content.contains("Code=VideoMotion;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
            } else if (content.contains("Code=VideoMotion;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
            }
            // Handle item taken alarm
            if (content.contains("Code=TakenAwayDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_ITEM_TAKEN);
            } else if (content.contains("Code=TakenAwayDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_ITEM_TAKEN);
            }
            // Handle item left alarm
            if (content.contains("Code=LeftDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_ITEM_LEFT);
            } else if (content.contains("Code=LeftDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_ITEM_LEFT);
            }
            // Handle CrossLineDetection alarm
            if (content.contains("Code=CrossLineDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
            } else if (content.contains("Code=CrossLineDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_LINE_CROSSING_ALARM);
            }
            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            // Handle AudioMutation alarm
            if (content.contains("Code=AudioMutation;action=Start;index=0")) {
                ipCameraHandler.audioDetected();
            } else if (content.contains("Code=AudioMutation;action=Stop;index=0")) {
                ipCameraHandler.noAudioDetected();
            }
            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = ipCameraHandler.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, PercentType.valueOf(value));
            }
            // Handle FaceDetection alarm
            if (content.contains("Code=FaceDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_FACE_DETECTED);
            } else if (content.contains("Code=FaceDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_FACE_DETECTED);
            }
            // Handle ParkingDetection alarm
            if (content.contains("Code=ParkingDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_PARKING_ALARM);
            } else if (content.contains("Code=ParkingDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_PARKING_ALARM);
            }
            // Handle CrossRegionDetection alarm
            if (content.contains("Code=CrossRegionDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            } else if (content.contains("Code=CrossRegionDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            }
            // Handle External Input alarm
            if (content.contains("Code=AlarmLocal;action=Start;index=0")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=0")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            }
            // Handle External Input alarm2
            if (content.contains("Code=AlarmLocal;action=Start;index=1")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=1")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.OFF);
            }
            // CrossLineDetection alarm on/off
            if (content.contains("table.VideoAnalyseRule[0][1].Enable=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
            } else if (content.contains("table.VideoAnalyseRule[0][1].Enable=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    // This handles the commands that come from the Openhab event bus.
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            switch (channelUID.getId()) {
                case CHANNEL_THRESHOLD_AUDIO_ALARM:
                    // ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
                    return;
                case CHANNEL_ENABLE_AUDIO_ALARM:
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
                    return;
                case CHANNEL_ENABLE_LINE_CROSSING_ALARM:
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=VideoAnalyseRule");
                    return;
                case CHANNEL_ENABLE_MOTION_ALARM:
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
                    return;
            }
            return; // Return as we have handled the refresh command above and don't need to
                    // continue further.
        } // end of "REFRESH"
        switch (channelUID.getId()) {
            case CHANNEL_TEXT_OVERLAY:
                String text = Helper.encodeSpecialChars(command.toString());
                if (text.isEmpty()) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                                    + text);
                }
                return;
            case CHANNEL_ENABLE_LED:
                ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
                if (DecimalType.ZERO.equals(command) || OnOffType.OFF.equals(command)) {
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&Lighting[0][0].Mode=Off");
                } else if (OnOffType.ON.equals(command)) {
                    ipCameraHandler
                            .sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&Lighting[0][0].Mode=Manual");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                    + command.toString());
                }
                return;
            case CHANNEL_AUTO_LED:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, UnDefType.UNDEF);
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&Lighting[0][0].Mode=Auto");
                }
                return;
            case CHANNEL_THRESHOLD_AUDIO_ALARM:
                int threshold = Math.round(Float.valueOf(command.toString()));

                if (threshold == 0) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&AudioDetect[0].MutationThreold=1");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&AudioDetect[0].MutationThreold=" + threshold);
                }
                return;
            case CHANNEL_ENABLE_AUDIO_ALARM:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&AudioDetect[0].MutationDetect=false");
                }
                return;
            case CHANNEL_ENABLE_LINE_CROSSING_ALARM:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&VideoAnalyseRule[0][1].Enable=true");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&VideoAnalyseRule[0][1].Enable=false");
                }
                return;
            case CHANNEL_ENABLE_MOTION_ALARM:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
                } else {
                    ipCameraHandler
                            .sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[0].Enable=false");
                }
                return;
            case CHANNEL_ACTIVATE_ALARM_OUTPUT:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&AlarmOut[0].Mode=1");
                } else {
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&AlarmOut[0].Mode=0");
                }
                return;
            case CHANNEL_ACTIVATE_ALARM_OUTPUT2:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&AlarmOut[1].Mode=1");
                } else {
                    ipCameraHandler.sendHttpGET("/cgi-bin/configManager.cgi?action=setConfig&AlarmOut[1].Mode=0");
                }
                return;
            case CHANNEL_FFMPEG_MOTION_CONTROL:
                if (OnOffType.ON.equals(command)) {
                    ipCameraHandler.motionAlarmEnabled = true;
                } else if (OnOffType.OFF.equals(command) || DecimalType.ZERO.equals(command)) {
                    ipCameraHandler.motionAlarmEnabled = false;
                    ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                } else {
                    ipCameraHandler.motionAlarmEnabled = true;
                    ipCameraHandler.motionThreshold = Double.valueOf(command.toString()) / 10000;
                }
                ipCameraHandler.setupFfmpegFormat(FFmpegFormat.RTSP_ALARMS);
                return;
        }
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<String>(1);
        return lowPriorityRequests;
    }
}