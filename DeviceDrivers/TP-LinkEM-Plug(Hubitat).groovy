/*
TP-Link Device Driver, Version 4.3

	Copyright 2018, 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Final code for Hubitat without reference to deviceType and enhancement of logging functions.
3.28.19	4.2.01	a.	Added capability Change Level implementation.
				c.	Added user command to synchronize the Kasa App name with the Hubitat device label.
				d.	Added method updateInstallData called from app on initial update only.
7.01.19	4.3.01	a.	Updated communications architecture, reducing required logic (and error potentials).
				b.	Added import ability for driver from the HE editor.
				c.	Added preference for synching name between hub and device.  Deleted command syncKasaName.
				d.	Updated to match the TP-Link Engr Mon Multi-Plug reporting.
				e.	Added Fast Polling as a driver-defined option. RECOMMENDATION:  DO NOT USE FAST POLLING.
					For Energy Monitor devices, fast polling updates the energy usage, not the on/off status.
7.16/19	4.3.02	a.	Corrected minor issues.
				b.	Removed "Refresh Rate" Preference.  Added state.refreshInterval (default 15) to Install.
				c.	Added command "Set Refresh Interval".  Used by user to set the refresh interval.  
=======================================================================================================*/
def driverVer() { return "4.3.02" }
metadata {
	definition (name: "TP-Link Engr Mon Plug",
    			namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: "https://github.com/DaveGut/Hubitat-TP-Link-Integration/blob/master/DeviceDrivers/TP-LinkEM-Plug(Hubitat).groovy"
			   ) {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		command "setRefreshInterval", ["1, 5, 10, 15, 30"]
	}
	preferences {
		def nameMaster  = [:]
		nameMaster << ["none": "Don't synchronize"]
		nameMaster << ["device" : "Kasa (device) alias master"]
		nameMaster << ["hub" : "Hubitat label master"]
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})", defaultValue: "")
		}
		input ("emEnabled", "bool", title: "Enable energy monitoring features", defaultValue: false)
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
		input ("nameSync", "enum", title: "Synchronize Names", options: nameMaster, defaultValue: "none")
	}
}

def installed() {
	log.info "Installing .."
	if (!state.refreshInterval) { state.refreshInterval = "15" }
	runIn(2, updated)
}
def updated() {
	log.info "Updating .."
	unschedule()
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	if (!getDataValue("applicationVersion")) {
		logInfo("Setting deviceIP for program.")
		updateDataValue("deviceIP", device_IP)
	}
	setRefreshInterval(state.refreshInterval)
	if (getDataValue("driverVersion") != driverVer()) { updateInstallData() }
	if (getDataValue("deviceIP") && getDataValue("plugNo")) {
		if (emEnabled == true) {
			logInfo("Scheduling nightly energy statistics update.")
			schedule("0 01 0 * * ?", updateStats)
			updateStats()
		}
		if (nameSync != "none") { syncName() }
		runIn(5, refresh)
	}
}
//	Update methods called in updated
def updateInstallData() {
	logInfo("updateInstallData: Updating installation to driverVersion ${driverVer()}")
	updateDataValue("driverVersion", driverVer())
	if (getDataValue("plugId")) { updateDataValue("plugId", null) }
	if (getDataValue("plugNo")) { updateDataValue("plugNo", null) }
	state.remove("multiPlugInstalled")
	pauseExecution(1000)
	state.remove("currentError")
	pauseExecution(1000)
	state.remove("commsErrorCount")
	pauseExecution(1000)
	state.remove("updated")
}
def setRefreshInterval(interval) {
	logDebug("setRefreshInterval: interval = ${interval}")
	unschedule(refresh)
	interval = interval.toString()
	state.refreshInterval = interval
	switch(interval) {
		case "1" :
			runEvery1Minute(refresh)
			break
		case "5" :
			runEvery5Minutes(refresh)
			break
		case "15" :
			runEvery15Minutes(refresh)
			break
		case "30" :
			runEvery30Minutes(refresh)
			break
		default:
		logWarn("setRefreshInterval: Invalid Interval sent.")
		return
	}
	logInfo("Refresh set for every ${interval} minute(s).")
}
def syncName() {
	logDebug("syncName. Synchronizing device name and label with master = ${nameSync}")
	if (nameSync == "hub") {
		def plugId = getDataValue("plugId")
		sendCmd("""{"context":{"child_ids":["${plugId}"]},"system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "nameSyncHub")
	} else if (nameSync == "device") {
		sendCmd("""{"system":{"get_sysinfo":{}}}""", "nameSyncDevice")
	}
}
def nameSyncHub(response) {
	def cmdResponse = parseInput(response)
	logInfo("Setting deviceIP for program.")
}
def nameSyncDevice(response) {
	def cmdResponse = parseInput(response)
	def children = cmdResponse.system.get_sysinfo.children
	def status = children.find { it.id == getDataValue("plugNo") }
	device.setLabel(status.alias)
	logInfo("Hubit name for device changed to ${status.alias}.")
}


//	Device Commands
def on() {
	logDebug("on")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}}}""", "commandResponse")
}
def off() {
	logDebug("off")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}}}""", "commandResponse")
}
def refresh() {
	logDebug("refresh")
	sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "refreshResponse")
}
//	Device command parsing methods
def commandResponse(response) {
	logDebug("commandResponse")
	sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "refreshResponse")
}
def refreshResponse(response) {
	def cmdResponse = parseInput(response)
	def status = cmdResponse.system.get_sysinfo
	logDebug("refreshResponse: status = ${status}")
	def pwrState = "off"
	if (status.relay_state == 1) { pwrState = "on" }
	if (device.currentValue("switch") != pwrState) {
		sendEvent(name: "switch", value: "${pwrState}")
		logInfo("Power: ${pwrState}")
	}
	if (emEnabled == true) { getPower() }
}


//	Update Today's power data.  Called from refreshResponse.
def getPower(){
	logDebug("getPower")
	sendCmd("""{"emeter":{"get_realtime":{}}}""", 
			"powerResponse")
}
def powerResponse(response) {
	def cmdResponse = parseInput(response)
	logDebug("powerResponse: cmdResponse = ${cmdResponse}")
	def realtime = cmdResponse.emeter.get_realtime
	def scale = "energy"
	if (realtime.power == null) { scale = "power_mw" }
	def power = realtime."${scale}"
	if(power == null) { power = 0 }
	else if (scale == "power_mw") { power = power / 1000 }
	power = Math.round(100*power)/100
	sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
	logInfo("Power is ${power} Watts.")
	def year = new Date().format("YYYY").toInteger()
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""",
			"setEngrToday")
}
def setEngrToday(response) {
	def cmdResponse = parseInput(response)
	logDebug("setEngrToday: energyScale = ${state.energyScale}, cmdResponse = ${cmdResponse}")
	def month = new Date().format("M").toInteger()
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == month }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	if (scale == "energy_wh") { energyData = energyData/1000 }
	energyData -= device.currentValue("currMonthTotal")
	energyData = Math.round(100*energyData)/100
	if (energyData != device.currentValue("energy")) {
		sendEvent(name: "energy", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
		logInfo("Energy is ${energyData} kilowatt hours.")
	}
}


//	Update this and last month's stats (at 00:01 AM).  Called from updated.
def updateStats() {
	logDebug("updateStats")
	def year = new Date().format("YYYY").toInteger()
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""",
			"setThisMonth")
}
def setThisMonth(response) {
	def cmdResponse = parseInput(response)
	logDebug("setThisMonth: energyScale = ${state.energyScale}, cmdResponse = ${cmdResponse}")
	def month = new Date().format("M").toInteger()
	def day = new Date().format("d").toInteger()
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == month }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	def avgEnergy = energyData/(day - 1)
	if (scale == "energy_wh") {
		energyData = energyData/1000
		avgEnergy = avgEnergy/1000
	}
	energyData = Math.round(100*energyData)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "currMonthTotal", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
	sendEvent(name: "currMonthAvg", value: avgEnergy, descriptionText: "KiloWatt Hours per Day", unit: "KWH/D")
	logInfo("This month's energy stats set to ${energyData} // ${avgEnergy}")
	def year = new Date().format("YYYY").toInteger()
	if (month == 1) { year = year -1 }
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""",
			"setLastMonth")
}
def setLastMonth(response) {
	def cmdResponse = parseInput(response)
	logDebug("setThisMonth: energyScale = ${state.energyScale}, cmdResponse = ${cmdResponse}")
	def lastMonth = new Date().format("M").toInteger() - 1
	def monthLength
	switch(lastMonth) {
		case 4:
		case 6:
		case 9:
		case 11:
			monthLength = 30
			break
		case 2:
			monthLength = 28
			if (year == 2020 || year == 2024 || year == 2028) { monthLength = 29 }
			break
		default:
			monthLength = 31
	}
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == lastMonth }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	def avgEnergy = energyData/monthLength
	if (scale == "energy_wh") {
		energyData = energyData/1000
		avgEnergy = avgEnergy/1000
	}
	energyData = Math.round(100*energyData)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "lastMonthTotal", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
	sendEvent(name: "lastMonthAvg", value: avgEnergy, descriptionText: "KiloWatt Hoursper Day", unit: "KWH/D")
	logInfo("Last month's energy stats set to ${energyData} // ${avgEnergy}")
}


//	Communications and initial common parsing
private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}, action = ${action}")
	runIn(5, setCommsError)
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 4,
		 callback: action])
	sendHubCommand(myHubAction)
}
def parseInput(response) {
	unschedule(setCommsError)
	try {
		def encrResponse = parseLanMessage(response).payload
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logDebug("parseInput: cmdResponse = ${cmdResponse}")
		return cmdResponse
	} catch (error) {
		logWarn "CommsError: Fragmented message returned from device."
	}
}
def setCommsError() {
	sendEvent(name: "switch", value: "OFFLINE",descriptionText: "No response from device.")
	logWarn "CommsError: No response from device.  Device set to offline.  Refresh.  If off line " +
			"persists, check IP address of device."
}


//	Utility Methods
private outputXOR(command) {
	def str = ""
	def encrCmd = ""
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}
private inputXOR(encrResponse) {
	String[] strBytes = encrResponse.split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}
def logInfo(msg) {
	if (descriptionText == true) { log.info "${device.label} ${driverVer()} ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }

//	end-of-file