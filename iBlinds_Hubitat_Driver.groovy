/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  last update 9/9/18 - Eric B
 *  inital device driver for Hubitat needs work
 */
metadata {
    definition (name: "iblinds", namespace: "iblinds", author: "HAB") {
        capability "Switch Level"
        capability "Actuator"
        capability "Switch"
        capability "Window Shade"
        capability "Refresh"
        capability "Battery"
        capability "Configuration"


        // fingerprint inClusters: "0x26"
        fingerprint type: "1106", cc: "5E,85,59,86,72,5A,73,26,25,80"

    }

    simulator {
        status "on":  "command: 2003, payload: FF"
        status "off": "command: 2003, payload: 00"
        status "09%": "command: 2003, payload: 09"
        status "10%": "command: 2003, payload: 0A"
        status "33%": "command: 2003, payload: 21"
        status "66%": "command: 2003, payload: 42"
        status "99%": "command: 2003, payload: 63"

        // reply messages
        reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
        reply "200100,delay 5000,2602": "command: 2603, payload: 00"
        reply "200119,delay 5000,2602": "command: 2603, payload: 19"
        reply "200132,delay 5000,2602": "command: 2603, payload: 32"
        reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
        reply "200163,delay 5000,2602": "command: 2603, payload: 63"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"blind", type: "lighting", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true) {
            tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "set level", label:'${name}', action:"refresh.refresh", icon:"http://myiblinds.com/icons/blind2.png", backgroundColor:"#ffa81e"
                attributeState "open", label:'${name}', action:"switch.off", icon:"http://myiblinds.com/icons/blind2.png", backgroundColor:"#00B200", nextState:"closing"
                attributeState "closed", label:'${name}', action:"switch.on", icon:"http://myiblinds.com/icons/blind2.png", backgroundColor:"#ffffff", nextState:"opening"
                attributeState "opening", label:'${name}', action:"switch.off", icon:"http://myiblinds.com/icons/blind2.png", backgroundColor:"#00B200", nextState:"closing"
                attributeState "closing", label:'${name}', action:"switch.on", icon:"http://myiblinds.com/icons/blind2.png", backgroundColor:"#ffffff", nextState:"opening"

            }

            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
            tileAttribute("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'Battery Level: ${currentValue}%', unit:"%"
            }
        }

        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "battery", label:'${currentValue}% Battery Level', unit:""
        }

        valueTile("levelval", "device.level", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "Level", label:'${currentValue}% Tilt Angle', unit:""
        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("config", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'Calibrate', action:"configuration.configure" , icon:"st.custom.buttons.add-icon"
        }

        main(["blind"])
        details(["blind", "levelval", "battery", "levelSliderControl",  "refresh","config"])

        preferences {
            input name: "openPosition", type: "number", title: "Open Position", description: "Position to open to by default:", defaultValue: 50, required: true
            input name: "time", type: "time", title: "Check battery level every day at: ", description: "Enter time", defaultValue: "12:00:00.000", required: true, displayDuringSetup: true
            input name: "reverse", type: "bool", title: "Reverse", description: "Reverse Blind Direction", required: true
        }
    }
}

def parse(String description) {
    def result = null
    if (description != "updated") {
        log.debug "parse() >> zwave.parse($description)"
        def cmd = zwave.parse(description, [0x20: 1, 0x26: 1, 0x70: 1])
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
        result = [result, response(zwave.basicV1.basicGet())]
        log.debug "Was hailed: requesting state update"
    } else {
        log.debug "Parse returned ${result?.descriptionText}"
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd) {
    dimmerEvents(cmd)
}

private dimmerEvents(hubitat.zwave.Command cmd) {
    log.debug "Dimmer event: $cmd"
    def position = cmd.value
    if (reverse) {
        position = 99 - position
    }

    def switchValue = "off"
    def shadePosition = "closed"
    if (position > 0 && position < 99) {
        switchValue = "on"
        shadePosition = "open"
    }
    def result = [
        createEvent(name: "switch", value: switchValue),
        createEvent(name: "windowShade", value: shadePosition)
    ]

    if (position < 100) {
        result << createEvent(name: "level", value: position, unit: "%")
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug "ConfigurationReport $cmd"
    def value = "when off"
    if (cmd.configurationValue[0] == 1) {value = "when on"}
    if (cmd.configurationValue[0] == 2) {value = "never"}
    createEvent([name: "indicatorStatus", value: value])
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
    createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug "manufacturerId:   ${cmd.manufacturerId}"
    log.debug "manufacturerName: ${cmd.manufacturerName}"
    log.debug "productId:        ${cmd.productId}"
    log.debug "productTypeId:    ${cmd.productTypeId}"
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
    [createEvent(name:"switch", value:"on"), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    log.debug "BatteryReport $cmd"
    def map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
    } else {
        map.value = cmd.batteryLevel
    }
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    [:]
}

def on() {
    log.trace "on"
    setLevel(openPosition)
}

def off() {
    log.trace "off()"
    setLevel(0)
}

def open() {
    log.trace "open()"
    on()
}

def close() {
    log.trace "close()"
    off()
}

def setPosition(value) {
    log.trace "presetPosition()"
    setLevel(value)
}

def setLevel(value, duration=0) {
    log.debug "setLevel >> value: $value, duration: $duration"
    def level = Math.max(Math.min(value as Integer, 99), 0)

    if (level <= 0 || level >= 99) {
         sendEvent(name: "switch", value: "off")
         sendEvent(name: "windowShade", value: "closed")
    } else {
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "windowShade", value: "open")
    }

    sendEvent(name: "level", value: level, unit: "%")
    def setLevel = reverse ? 99 - level : level
    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
    zwave.switchMultilevelV2.switchMultilevelSet(value: setLevel, dimmingDuration: dimmingDuration).format()
}

/**
def poll() {
    //zwave.switchMultilevelV1.switchMultilevelGet().format()
    log.trace "Poll (started)"
        delayBetween([
        // zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format(),
        zwave.batteryV1.batteryGet().format(),
     //   zwave.basicV1.basicGet().format(),
    ], 3000)
}
**/

def installed() {
    // When device is installed get battery level and set daily schedule for battery refresh
    log.debug "Installed, Set Get Battery Schedule"
    runIn(15, "getBattery")
    schedule("$time", "getBattery")
}

def updated() {
    // When device is updated get battery level and set daily schedule for battery refresh
    log.debug "Updated , Set Get Battery Schedule"
    runIn(15, "getBattery")
    schedule("$time", "getBattery")
}

def getBattery() {
    log.debug "Get battery level"
    sendHubCommand(
        new hubitat.device.HubAction(zwave.batteryV1.batteryGet().format(),
                                     hubitat.device.Protocol.ZWAVE)
    )
}

def refresh() {
    log.trace "refresh(started)"
    log.debug "Refresh Tile Pushed"
    delayBetween([
        // zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format(),
        zwave.batteryV1.batteryGet().format(),
    ], 3000)
}
