/*	Samsung HVAC using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Description
This driver is for SmartThings-installed Samsung HVAC for import of control
and status of defined functions into Hubitat Environment.
=====	Library Use
This driver uses libraries for the functions common to SmartThings devices. 
Library code is at the bottom of the distributed single-file driver.
===== Installation Instructions Link =====
https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Install_Samsung_Appliance.pdf

1.2.2Test Changes
==============================================================================*/
def driverVer() { return "1.2.2Test" }

metadata {
	definition (name: "Test Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_HVAC.groovy"
			   ){
		capability "Refresh"
		capability "ThermostatSetpoint"
		command "setThermostatSetpoint", ["NUMBER"]
//		command "setThermostatMode", [[
//			name: "Thermostat Mode",
//			constraints: ["off", "auto", "cool", "heat", "dry", "wind", "samsungAuto"],
//			type: "ENUM"]]
//	TEST Command
		capability "RelativeHumidityMeasurement"
//		command "poll"
		command "setOptionalMode", [[
			name: "Thermostat Optional Mode",
			constraints: ["off", "sleep", "quiet", "smart", "speed", "windFree", "windFreeSleep"],
			type: "ENUM"]]
		attribute "acOptionalMode", "string"
		attribute "dustFilterStatus", "string"
		command "togglePanelLight"
		attribute "lightStatus", "string"
		command "setOscillationMode", [[
			name: "Fan Oscillation Mode",
			constraints: ["fixed", "all", "vertical", "horizontal"],
			type: "ENUM"]]
		attribute "fanOscillationMode", "string"
		
//		TEST ONLY
//		command "setHVACScale", [
//			[name: "Test Scale", constraints: ["C", "F"],
//			 type: "ENUM"]]
//		command "deviceSetup"
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("tempScale", "enum", options: ["°C", "°F"],
				   title: "Hub Temperature Scale", defaultValue: "°C")
		}
	}
}

def setOptionalMode(opMode) {
	def cmdData = [
		component: "main",
		capability: "custom.airConditionerOptionalMode",
		command: "setAcOptionalMode",
		arguments: [opMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setOptionalMode: [cmd: ${opMode}, ${cmdStatus}]")
}

def setOscillationMode(oscMode) {
	def cmdData = [
		component: "main",
		capability: "fanOscillationMode",
		command: "setFanOscillationMode",
		arguments: [oscMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setOscillationMode: [cmd: ${oscMode}, ${cmdStatus}]")
}

def setThermostatSetpoint(setpoint) {
	def hvacSetpoint = hubToHvacConvert(setpoint, getDataValue("tempUnit"))
	def rangeCheck = checkSetpointRange(hvacSetpoint)
	if ( rangeCheck == "OK") {
		def cmdData = [
			component: "main",
			capability: "thermostatCoolingSetpoint",
			command: "setCoolingSetpoint",
			arguments: [setpoint]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("setThermostatSetpoint: [setpoint: ${setpoint}, havcSetpoint: ${hvacSetpoint}, ${cmdStatus}]")
	} else {
		logWarn("setThermostateSetpoint: ${rangeCheck}}")
	}
}
def hubToHvacConvert(setpoint, tempUnit) {
	if (tempScale == "°C" && tempUnit == "°F") {
		setpoint = (setpoint * (9.0 / 5.0) + 32.0 + 0.5).toInteger()
	} else if (tempScale == "°F" && tempUnit == "°C"){
		setpoint = (0.9 + 2 * (setpoint - 32.0) * (5.0 / 9.0)).toInteger() / 2
	}
	return setpoint
}
def checkSetpointRange(setpoint) {
	def minSetpoint = getDataValue("minSetpoint").toFloat()
	def maxSetpoint = getDataValue("maxSetpoint").toFloat()
	if (setpoint < minSetpoint || setpoint > maxSetpoint) {
		return [error: "setpoint is outside device min and max setpoints"]
	} else {
		return "OK"
	}
}

def togglePanelLight() {
	def newOnOff = "on"
	if (device.currentValue("lightStatus") == "on") {
		newOnOff = "off"
	}
	def lightCmd = "Light_Off"
	if (newOnOff == "off") {
		lightCmd = "Light_On"
	}
	if (simulate == true) {
		state.light = ["Sleep_0", lightCmd, "Volume_Mute"]
	}
	def cmdString = """{"commands":[{"component": "main","capability": "execute",""" +
		   """"command": "execute","arguments": ["mode/vs/0",""" +
		   """{"x.com.samsung.da.options":["${lightCmd}"]}]}]}"""
	def respData = stringPostHttp(cmdString)
	logInfo("togglePanelLight [newOnOff: ${newOnOff}, cmd: ${lightCmd}, ${respData}]")
}
def stringPostHttp(cmdString) {
	def respData = [:]
	def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: "/devices/${stDeviceId.trim()}/commands",
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()],
			body : cmdString]
	try {
		httpPost(sendCmdParams) {resp ->
			if (resp.status == 200 && resp.data != null) {
				respData << [status: "OK", results: resp.data.results]
				refresh()
			} else {
				respData << [status: "FAILED",
							 httpCode: resp.status,
							 errorMsg: resp.errorMessage]
			}
		}
	} catch (error) {
		respData << [status: "FAILED",
					 httpCode: "Timeout",
					 errorMsg: error]
	}
	return respData
}

def installed() { 
	device.updateSetting("tempScale", [type:"enum", value: "°C"])
}
def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "FAILED") {
		logWarn("updated: ${commonStatus}")
	} else {
		logInfo("updated: ${commonStatus}")
	}
	deviceSetup()
	pauseExecution(5000)
}

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			} else {
				statusParse(respData.components.main)
			}
		} catch (err) {
			respLog << [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	} else {
		respLog << [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	}
	if (respLog != [:]) {
//		logWarn("distResp: ${respLog}")
	}
}
def deviceSetupParse(parseData) {
	def logData = [:]
	tempUnit = parseData.temperatureMeasurement.temperature.unit
	tempUnit = "°${tempUnit}"
	updateDataValue("tempUnit", tempUnit)
	logData << [tempUnit: tempUnit]
	
	def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
	updateDataValue("minSetpoint", minSetpoint.toString())
	logData << [minSetpoint: minSetpoint]
	def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
	updateDataValue("maxSetpoint", maxSetpoint.toString())
	logData << [maxSetpoint: maxSetpoint]

//	Modified to raw values.  Change for final
	def supportedThermostatModes = parseData.airConditionerMode.supportedAcModes.value
	sendEvent(name: "supportedThermostatModes", value: supportedThermostatModes)
	logData << [supportedThermostatModes: supportedThermostatModes]
//	New optional modes.
	def supportedOpModes = parseData["custom.airConditionerOptionalMode"].supportedAcOptionalMode.value
	sendEvent(name: "supportedOpModes", value: supportedOpModes)
	logData << [supportedOpModes: supportedOpModes]

	def supportedOscillationModes = parseData.fanOscillationMode.supportedFanOscillationModes.value
	sendEvent(name: "supportedOscillationModes", value: supportedOscillationModes)
	logData << [supportedOscillationModes: supportedOscillationModes]

	logInfo("deviceSetupParse: ${logData}")
}
def statusParse(parseData) {
	def logData = [:]
	def tempUnit = parseData.temperatureMeasurement.temperature.unit
	tempUnit = "°${tempUnit}"
	if (tempUnit != getDataValue("tempUnit")) {
		updateDataValue("tempUnit", tempUnit)
		logData << [tempUnit: tempUnit]	
		def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
		updateDataValue("minSetpoint", minSetpoint.toString())
		logData << [minSetpoint: minSetpoint]
		def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
		updateDataValue("maxSetpoint", maxSetpoint.toString())
		logData << [maxSetpoint: maxSetpoint]
	}

	def thermostatSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	thermostateSetpoint = hvacToHubConvert(thermostatSetpoint, tempUnit)
	sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint)
	logData << [thermostatSetpoint: thermostatSetpoint]

//	TEST CODE	
	def acOptionalMode = parseData["custom.airConditionerOptionalMode"].acOptionalMode.value
	sendEvent(name: "acOptionalMode", value: acOptionalMode)
	logData << [acOptionalMode: acOptionalMode]
	
	def dustFilterStatus = parseData["custom.dustFilter"].dustFilterStatus.value
	sendEvent(name: "dustFilterStatus", value: dustFilterStatus)
	logData << [dustFilterStatus: dustFilterStatus]
	
	def execStatus = parseData.execute.data.value.payload["x.com.samsung.da.options"]
	def lightStatus = "on"
	if (execStatus.contains("Light_On")) { lightStatus = "off" }
	if (device.currentValue("lightStatus") != lightStatus) {
		sendEvent(name: "lightStatus", value: lightStatus)
		logData << [lightStatus: lightStatus]
	}
	
	def humidity = parseData.relativeHumidityMeasurement.humidity.value
	sendEvent(name: "humidity", value: humidity, unit: "%rh")
	logData << [humidity: humidity]
	
	def fanOscillationMode = parseData.fanOscillationMode.fanOscillationMode.value
	sendEvent(name: "fanOscillationMode", value: fanOscillationMode)
	logData << [fanOscillationMode: fanOscillationMode]
	
	logTrace("parseData: ${logData}")
}
def hvacToHubConvert(setpoint, tempUnit) {
	//	uses preference tempScale and data value tempUnit from device.
	if (tempScale != tempUnit) {
		if (tempScale == "°C") {
			//	convert f to c
			setpoint = (0.9 + 2 * (setpoint - 32.0) * (5.0 / 9.0)).toInteger() / 2
		} else {
			//	convert c to f
			setpoint = (setpoint * (9.0 / 5.0) + 32.0 + 0.5).toInteger() 
		}
	}
	return setpoint
}

//	===== Library Integration =====



def simulate() { return false}
//def simulate() { return true}
//#include davegut.Samsung-HVAC-Sim

// ~~~~~ start include (1072) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

//	Logging during development // library marker davegut.Logging, line 10
def listAttributes(trace = false) { // library marker davegut.Logging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 12
	def attrList = [:] // library marker davegut.Logging, line 13
	attrs.each { // library marker davegut.Logging, line 14
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 15
		attrList << ["${it}": val] // library marker davegut.Logging, line 16
	} // library marker davegut.Logging, line 17
	if (trace == true) { // library marker davegut.Logging, line 18
		logInfo("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.Logging, line 25
def logTrace(msg){ // library marker davegut.Logging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.Logging, line 27
} // library marker davegut.Logging, line 28

def logInfo(msg) {  // library marker davegut.Logging, line 30
	if (!infoLog || infoLog == true) { // library marker davegut.Logging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def debugLogOff() { // library marker davegut.Logging, line 36
	if (debug == true) { // library marker davegut.Logging, line 37
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} else if (debugLog == true) { // library marker davegut.Logging, line 39
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 40
	} // library marker davegut.Logging, line 41
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 42
} // library marker davegut.Logging, line 43

def logDebug(msg) { // library marker davegut.Logging, line 45
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 46
		log.debug "${device.displayName}: ${msg}" // library marker davegut.Logging, line 47
	} // library marker davegut.Logging, line 48
} // library marker davegut.Logging, line 49

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.Logging, line 51

// ~~~~~ end include (1072) davegut.Logging ~~~~~

// ~~~~~ start include (1091) davegut.ST-Communications ~~~~~
library ( // library marker davegut.ST-Communications, line 1
	name: "ST-Communications", // library marker davegut.ST-Communications, line 2
	namespace: "davegut", // library marker davegut.ST-Communications, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Communications, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Communications, line 5
	category: "utilities", // library marker davegut.ST-Communications, line 6
	documentationLink: "" // library marker davegut.ST-Communications, line 7
) // library marker davegut.ST-Communications, line 8
import groovy.json.JsonSlurper // library marker davegut.ST-Communications, line 9

private asyncGet(sendData, passData = "none") { // library marker davegut.ST-Communications, line 11
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 12
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 13
	} else { // library marker davegut.ST-Communications, line 14
		logDebug("asyncGet: ${sendData}, ${passData}") // library marker davegut.ST-Communications, line 15
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 16
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 17
			path: sendData.path, // library marker davegut.ST-Communications, line 18
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) // library marker davegut.ST-Communications, line 21
		} catch (error) { // library marker davegut.ST-Communications, line 22
			logWarn("asyncGet: [status: FAILED, errorMsg: ${error}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

private syncGet(path){ // library marker davegut.ST-Communications, line 28
	def respData = [:] // library marker davegut.ST-Communications, line 29
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 30
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 31
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 32
	} else { // library marker davegut.ST-Communications, line 33
		logDebug("syncGet: ${sendData}") // library marker davegut.ST-Communications, line 34
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 35
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 36
			path: path, // library marker davegut.ST-Communications, line 37
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 38
		] // library marker davegut.ST-Communications, line 39
		try { // library marker davegut.ST-Communications, line 40
			httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 41
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 42
					respData << [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 43
				} else { // library marker davegut.ST-Communications, line 44
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 45
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 46
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 47
				} // library marker davegut.ST-Communications, line 48
			} // library marker davegut.ST-Communications, line 49
		} catch (error) { // library marker davegut.ST-Communications, line 50
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 51
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 52
						 errorMsg: error] // library marker davegut.ST-Communications, line 53
		} // library marker davegut.ST-Communications, line 54
	} // library marker davegut.ST-Communications, line 55
	return respData // library marker davegut.ST-Communications, line 56
} // library marker davegut.ST-Communications, line 57

private syncPost(sendData){ // library marker davegut.ST-Communications, line 59
	def respData = [:] // library marker davegut.ST-Communications, line 60
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 61
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 62
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 63
	} else { // library marker davegut.ST-Communications, line 64
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 65

		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 67
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 68
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 69
			path: sendData.path, // library marker davegut.ST-Communications, line 70
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 71
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 72
		] // library marker davegut.ST-Communications, line 73
		try { // library marker davegut.ST-Communications, line 74
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 75
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 76
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 77
				} else { // library marker davegut.ST-Communications, line 78
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 79
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 80
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 81
				} // library marker davegut.ST-Communications, line 82
			} // library marker davegut.ST-Communications, line 83
		} catch (error) { // library marker davegut.ST-Communications, line 84
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 85
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 86
						 errorMsg: error] // library marker davegut.ST-Communications, line 87
		} // library marker davegut.ST-Communications, line 88
	} // library marker davegut.ST-Communications, line 89
	return respData // library marker davegut.ST-Communications, line 90
} // library marker davegut.ST-Communications, line 91

// ~~~~~ end include (1091) davegut.ST-Communications ~~~~~

// ~~~~~ start include (1090) davegut.ST-Common ~~~~~
library ( // library marker davegut.ST-Common, line 1
	name: "ST-Common", // library marker davegut.ST-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Common, line 4
	description: "ST Wash/Dryer Common Methods", // library marker davegut.ST-Common, line 5
	category: "utilities", // library marker davegut.ST-Common, line 6
	documentationLink: "" // library marker davegut.ST-Common, line 7
) // library marker davegut.ST-Common, line 8

def commonUpdate() { // library marker davegut.ST-Common, line 10
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Common, line 11
		return [status: "FAILED", reason: "No stApiKey"] // library marker davegut.ST-Common, line 12
	} // library marker davegut.ST-Common, line 13
	if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Common, line 14
		getDeviceList() // library marker davegut.ST-Common, line 15
		return [status: "FAILED", reason: "No stDeviceId"] // library marker davegut.ST-Common, line 16
	} // library marker davegut.ST-Common, line 17

	unschedule() // library marker davegut.ST-Common, line 19
	def updateData = [:] // library marker davegut.ST-Common, line 20
	updateData << [status: "OK"] // library marker davegut.ST-Common, line 21
	if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 22
	updateData << [stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 23
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 24
	if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 25
		getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 26
		updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 27
		updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 28
	} // library marker davegut.ST-Common, line 29
	setPollInterval(pollInterval) // library marker davegut.ST-Common, line 30
	updateData << [pollInterval: pollInterval] // library marker davegut.ST-Common, line 31

	runIn(5, refresh) // library marker davegut.ST-Common, line 33
	return updateData // library marker davegut.ST-Common, line 34
} // library marker davegut.ST-Common, line 35

def setPollInterval(pollInterval) { // library marker davegut.ST-Common, line 37
	logDebug("setPollInterval: ${pollInterval}") // library marker davegut.ST-Common, line 38
	state.pollInterval = pollInterval // library marker davegut.ST-Common, line 39
	switch(pollInterval) { // library marker davegut.ST-Common, line 40
		case "10sec":  // library marker davegut.ST-Common, line 41
			schedule("*/10 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 42
			break // library marker davegut.ST-Common, line 43
		case "20sec": // library marker davegut.ST-Common, line 44
			schedule("*/20 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 45
			break // library marker davegut.ST-Common, line 46
		case "30sec": // library marker davegut.ST-Common, line 47
			schedule("*/30 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 48
			break // library marker davegut.ST-Common, line 49
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 50
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 51
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 52
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 53
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 54
	} // library marker davegut.ST-Common, line 55
} // library marker davegut.ST-Common, line 56

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 58
	def respData = [:] // library marker davegut.ST-Common, line 59
	if (simulate() == true) { // library marker davegut.ST-Common, line 60
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 61
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 62
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 63
	} else { // library marker davegut.ST-Common, line 64
		def sendData = [ // library marker davegut.ST-Common, line 65
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 66
			cmdData: cmdData // library marker davegut.ST-Common, line 67
		] // library marker davegut.ST-Common, line 68
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 69
	} // library marker davegut.ST-Common, line 70
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 71
		refresh() // library marker davegut.ST-Common, line 72
	} else { // library marker davegut.ST-Common, line 73
		poll() // library marker davegut.ST-Common, line 74
	} // library marker davegut.ST-Common, line 75
	return respData // library marker davegut.ST-Common, line 76
} // library marker davegut.ST-Common, line 77

def refresh() { // library marker davegut.ST-Common, line 79
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 80
		def cmdData = [ // library marker davegut.ST-Common, line 81
			component: "main", // library marker davegut.ST-Common, line 82
			capability: "refresh", // library marker davegut.ST-Common, line 83
			command: "refresh", // library marker davegut.ST-Common, line 84
			arguments: []] // library marker davegut.ST-Common, line 85
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 86
	} // library marker davegut.ST-Common, line 87
} // library marker davegut.ST-Common, line 88

def poll() { // library marker davegut.ST-Common, line 90
	if (simulate() == true) { // library marker davegut.ST-Common, line 91
		def children = getChildDevices() // library marker davegut.ST-Common, line 92
		if (children) { // library marker davegut.ST-Common, line 93
			children.each { // library marker davegut.ST-Common, line 94
				it.statusParse(testData()) // library marker davegut.ST-Common, line 95
			} // library marker davegut.ST-Common, line 96
		} // library marker davegut.ST-Common, line 97
		statusParse(testData()) // library marker davegut.ST-Common, line 98
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 99
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 100
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 101
	} else { // library marker davegut.ST-Common, line 102
		def sendData = [ // library marker davegut.ST-Common, line 103
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 104
			parse: "distResp" // library marker davegut.ST-Common, line 105
			] // library marker davegut.ST-Common, line 106
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 107
	} // library marker davegut.ST-Common, line 108
} // library marker davegut.ST-Common, line 109

def deviceSetup() { // library marker davegut.ST-Common, line 111
	if (simulate() == true) { // library marker davegut.ST-Common, line 112
		def children = getChildDevices() // library marker davegut.ST-Common, line 113
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 114
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 115
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 116
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 117
	} else { // library marker davegut.ST-Common, line 118
		def sendData = [ // library marker davegut.ST-Common, line 119
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 120
			parse: "distResp" // library marker davegut.ST-Common, line 121
			] // library marker davegut.ST-Common, line 122
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 123
	} // library marker davegut.ST-Common, line 124
} // library marker davegut.ST-Common, line 125

def getDeviceList() { // library marker davegut.ST-Common, line 127
	def sendData = [ // library marker davegut.ST-Common, line 128
		path: "/devices", // library marker davegut.ST-Common, line 129
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 130
		] // library marker davegut.ST-Common, line 131
	asyncGet(sendData) // library marker davegut.ST-Common, line 132
} // library marker davegut.ST-Common, line 133

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 135
	def respData // library marker davegut.ST-Common, line 136
	if (resp.status != 200) { // library marker davegut.ST-Common, line 137
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 138
					httpCode: resp.status, // library marker davegut.ST-Common, line 139
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 140
	} else { // library marker davegut.ST-Common, line 141
		try { // library marker davegut.ST-Common, line 142
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 143
		} catch (err) { // library marker davegut.ST-Common, line 144
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 145
						errorMsg: err, // library marker davegut.ST-Common, line 146
						respData: resp.data] // library marker davegut.ST-Common, line 147
		} // library marker davegut.ST-Common, line 148
	} // library marker davegut.ST-Common, line 149
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 150
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 151
	} else { // library marker davegut.ST-Common, line 152
		log.info "" // library marker davegut.ST-Common, line 153
		respData.items.each { // library marker davegut.ST-Common, line 154
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 155
		} // library marker davegut.ST-Common, line 156
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 157
	} // library marker davegut.ST-Common, line 158
} // library marker davegut.ST-Common, line 159

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 161
	Integer currTime = now() // library marker davegut.ST-Common, line 162
	Integer compTime // library marker davegut.ST-Common, line 163
	try { // library marker davegut.ST-Common, line 164
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 165
	} catch (e) { // library marker davegut.ST-Common, line 166
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 167
	} // library marker davegut.ST-Common, line 168
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 169
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 170
	return timeRemaining // library marker davegut.ST-Common, line 171
} // library marker davegut.ST-Common, line 172

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~
