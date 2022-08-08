/*	Samsung Soundbar using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Description
This driver is for SmartThings-installed Samsung Soundbars for import of control
and status of defined functions into Hubitat Environment.
=====	Library Use
This driver uses libraries for the functions common to SmartThings devices. 
Library code is at the bottom of the distributed single-file driver.
===== Installation Instructions Link =====
https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Install_Samsung_Appliance.pdf
=====	Version B0.2
Second Beta Release of the driver set.  All functions tested and validated on 
a Q900 as well as a MS-650.
a.	Removed preferences simulate and infoLog.  added function simulate()
b.	Changed validateResp to distResp and added processing for init (as req.)
c.	Removed try statements from data parsing.
d.	Automatically send refresh with any command (reducing timeline and number of comms).
===== B0.3
Updated to support newer soundbars with more commands.
==============================================================================*/
def driverVer() { return "B0.3" }

metadata {
	definition (name: "Samsung Soundbar",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_Soundbar.groovy"
			   ){
		capability "Switch"
		capability "MediaInputSource"
		command "toggleInputSource"
		capability "MediaTransport"
		capability "AudioVolume"
		command "toggleMute"
		capability "Refresh"
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("volIncrement", "number", title: "Volume Up/Down Increment", defaultValue: 1)
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "5")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
		}
	}
}

//	========================================================
//	===== Installation, setup and update ===================
//	========================================================
def installed() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "volume", value: 0)
	sendEvent(name: "mute", value: "unmuted")
	sendEvent(name: "transportStatus", value: "stopped")
	sendEvent(name: "mediaInputSource", value: "wifi")
	runIn(1, updated)
}

def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "OK") {
		logInfo("updated: ${commonStatus}")
	} else {
		logWarn("updated: ${commonStatus}")
	}
	deviceSetup()
}

//	===== Switch =====
def on() { setSwitch("on") }
def off() { setSwitch("off") }
def setSwitch(onOff) {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: onOff,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setSwitch: [cmd: ${onOff}, ${cmdStatus}]")
}

//	===== Media Input Source =====
def toggleInputSource() {
	def inputSources = state.supportedInputs
	def totalSources = inputSources.size()
	def currentSource = device.currentValue("mediaInputSource")
	def sourceNo = inputSources.indexOf(currentSource)
	def newSourceNo = sourceNo + 1
	if (newSourceNo == totalSources) { newSourceNo = 0 }
	def inputSource = inputSources[newSourceNo]
	setInputSource(inputSource)
}
def setInputSource(inputSource) {
	def inputSources = state.supportedInputs
	if (inputSources.contains(inputSource)) {
	def cmdData = [
		component: "main",
		capability: "mediaInputSource",
		command: "setInputSource",
		arguments: [inputSource]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setInputSource: [cmd: ${inputSource}, ${cmdStatus}]")
	} else {
		logWarn("setInputSource: Invalid input source")
	}
}

//	===== Media Transport =====
def play() { setMediaPlayback("play") }
def pause() { setMediaPlayback("pause") }
def stop() { setMediaPlayback("stop") }
def setMediaPlayback(pbMode) {
	def cmdData = [
		component: "main",
		capability: "mediaPlayback",
		command: pbMode,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMediaPlayback: [cmd: ${pbMode}, ${cmdStatus}]")
}

//	===== Audio Volume =====
def volumeUp() { 
	def curVol = device.currentValue("volume")
	def newVol = curVol + volIncrement.toInteger()
	setVolume(newVol)
}
def volumeDown() {
	def curVol = device.currentValue("volume")
	def newVol = curVol - volIncrement.toInteger()
	setVolume(newVol)
}
def setVolume(volume) {
	if (volume == null) { volume = device.currentValue("volume") }
	else if (volume < 0) { volume = 0 }
	else if (volume > 100) { volume = 100 }
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: "setVolume",
		arguments: [volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setVolume: [cmd: ${volume}, ${cmdStatus}]")
}

def mute() { setMute("mute") }
def unmute() { setMute("unmute") }
def toggleMute() {
	def muteValue = "mute"
	if(device.currentValue("mute") == "muted") {
		muteValue = "unmute"
	}
	setMute(muteValue)
}
def setMute(muteValue) {
	def cmdData = [
		component: "main",
		capability: "audioMute",
		command: muteValue,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMute: [cmd: ${muteValue}, ${cmdStatus}]")
}

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			}
			statusParse(respData.components.main)
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
		logWarn("distResp: ${respLog}")
	}
}

def deviceSetupParse(mainData) {
	def setupData = [:]
	def supportedInputs =  mainData.mediaInputSource.supportedInputSources.value
	sendEvent(name: "supportedInputs", value: supportedInputs)	
	state.supportedInputs = supportedInputs
	setupData << [supportedInputs: supportedInputs]
	if (setupData != [:]) {
		logInfo("deviceSetupParse: ${setupData}")
	}
}

def statusParse(mainData) {
	def stData = [:]
	def onOff = mainData.switch.switch.value
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
		stData << [switch: onOff]
	}

	def volume = mainData.audioVolume.volume.value.toInteger()
	if (device.currentValue("volume").toInteger() != volume) {
		sendEvent(name: "volume", value: volume)
		stData << [volume: volume]
	}

	def mute = mainData.audioMute.mute.value
	if (device.currentValue("mute") != mute) {
		sendEvent(name: "mute", value: mute)
		stData << [mute: mute]
	}

	def transportStatus = mainData.mediaPlayback.playbackStatus.value
	if (device.currentValue("transportStatus") != transportStatus) {
		sendEvent(name: "transportStatus", value: transportStatus)
		stData << [transportStatus: transportStatus]
	}
	
	def mediaInputSource = mainData.mediaInputSource.inputSource.value
	if (device.currentValue("mediaInputSource") != mediaInputSource) {
		sendEvent(name: "mediaInputSource", value: mediaInputSource)
		stData << [mediaInputSource: mediaInputSource]
	}

	if (stData != [:] && stData != null) {
		logInfo("statusParse: ${stData}")
	}	
	listAttributes(true)
}

//	===== Library Integration =====



def simulate() { return false }


// ~~~~~ start include (993) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def listAttributes(trace = false) { // library marker davegut.Logging, line 10
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 11
	def attrList = [:] // library marker davegut.Logging, line 12
	attrs.each { // library marker davegut.Logging, line 13
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 14
		attrList << ["${it}": val] // library marker davegut.Logging, line 15
	} // library marker davegut.Logging, line 16
	if (trace == true) { // library marker davegut.Logging, line 17
		logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 18
	} else { // library marker davegut.Logging, line 19
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 20
	} // library marker davegut.Logging, line 21
} // library marker davegut.Logging, line 22

def logTrace(msg){ // library marker davegut.Logging, line 24
	log.trace "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 25
} // library marker davegut.Logging, line 26

def logInfo(msg) {  // library marker davegut.Logging, line 28
	log.info "${device.displayName} ${getDataValue("driverVersion")}: ${msg}"  // library marker davegut.Logging, line 29
} // library marker davegut.Logging, line 30

def debugLogOff() { // library marker davegut.Logging, line 32
	if (debug == true) { // library marker davegut.Logging, line 33
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 34
	} else if (debugLog == true) { // library marker davegut.Logging, line 35
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 36
	} // library marker davegut.Logging, line 37
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 38
} // library marker davegut.Logging, line 39

def logDebug(msg) { // library marker davegut.Logging, line 41
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 42
		log.debug "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 43
	} // library marker davegut.Logging, line 44
} // library marker davegut.Logging, line 45

def logWarn(msg) { log.warn "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" } // library marker davegut.Logging, line 47

// ~~~~~ end include (993) davegut.Logging ~~~~~

// ~~~~~ start include (1001) davegut.ST-Communications ~~~~~
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
log.trace sendCmdParams // library marker davegut.ST-Communications, line 74
		try { // library marker davegut.ST-Communications, line 75
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 76
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 77
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 78
				} else { // library marker davegut.ST-Communications, line 79
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 80
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 81
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 82
				} // library marker davegut.ST-Communications, line 83
			} // library marker davegut.ST-Communications, line 84
		} catch (error) { // library marker davegut.ST-Communications, line 85
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 86
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 87
						 errorMsg: error] // library marker davegut.ST-Communications, line 88
		} // library marker davegut.ST-Communications, line 89
	} // library marker davegut.ST-Communications, line 90
	return respData // library marker davegut.ST-Communications, line 91
} // library marker davegut.ST-Communications, line 92

// ~~~~~ end include (1001) davegut.ST-Communications ~~~~~

// ~~~~~ start include (1000) davegut.ST-Common ~~~~~
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
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 41
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 42
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 43
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 44
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 45
	} // library marker davegut.ST-Common, line 46
} // library marker davegut.ST-Common, line 47

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 49
	def respData = [:] // library marker davegut.ST-Common, line 50
	if (simulate() == true) { // library marker davegut.ST-Common, line 51
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 52
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 53
		respData << "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 54
	} else { // library marker davegut.ST-Common, line 55
		def sendData = [ // library marker davegut.ST-Common, line 56
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 57
			cmdData: cmdData // library marker davegut.ST-Common, line 58
		] // library marker davegut.ST-Common, line 59
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 60
	} // library marker davegut.ST-Common, line 61
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 62
		refresh() // library marker davegut.ST-Common, line 63
	} else { // library marker davegut.ST-Common, line 64
		poll() // library marker davegut.ST-Common, line 65
	} // library marker davegut.ST-Common, line 66
	return respData // library marker davegut.ST-Common, line 67
} // library marker davegut.ST-Common, line 68

def refresh() {  // library marker davegut.ST-Common, line 70
	def cmdData = [ // library marker davegut.ST-Common, line 71
		component: "main", // library marker davegut.ST-Common, line 72
		capability: "refresh", // library marker davegut.ST-Common, line 73
		command: "refresh", // library marker davegut.ST-Common, line 74
		arguments: []] // library marker davegut.ST-Common, line 75
	deviceCommand(cmdData) // library marker davegut.ST-Common, line 76
} // library marker davegut.ST-Common, line 77

def poll() { // library marker davegut.ST-Common, line 79
	if (simulate() == true) { // library marker davegut.ST-Common, line 80
		def children = getChildDevices() // library marker davegut.ST-Common, line 81
		if (children) { // library marker davegut.ST-Common, line 82
			children.each { // library marker davegut.ST-Common, line 83
				it.statusParse(testData()) // library marker davegut.ST-Common, line 84
			} // library marker davegut.ST-Common, line 85
		} // library marker davegut.ST-Common, line 86
		statusParse(testData()) // library marker davegut.ST-Common, line 87
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 88
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 89
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 90
	} else { // library marker davegut.ST-Common, line 91
		def sendData = [ // library marker davegut.ST-Common, line 92
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 93
			parse: "distResp" // library marker davegut.ST-Common, line 94
			] // library marker davegut.ST-Common, line 95
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 96
	} // library marker davegut.ST-Common, line 97
} // library marker davegut.ST-Common, line 98

def deviceSetup() { // library marker davegut.ST-Common, line 100
	if (simulate() == true) { // library marker davegut.ST-Common, line 101
		def children = getChildDevices() // library marker davegut.ST-Common, line 102
		if (children) { // library marker davegut.ST-Common, line 103
			children.each { // library marker davegut.ST-Common, line 104
				it.deviceSetupParse(testData()) // library marker davegut.ST-Common, line 105
			} // library marker davegut.ST-Common, line 106
		} // library marker davegut.ST-Common, line 107
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 108
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 109
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 110
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 111
	} else { // library marker davegut.ST-Common, line 112
		def sendData = [ // library marker davegut.ST-Common, line 113
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 114
			parse: "distResp" // library marker davegut.ST-Common, line 115
			] // library marker davegut.ST-Common, line 116
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 117
	} // library marker davegut.ST-Common, line 118
} // library marker davegut.ST-Common, line 119

def getDeviceList() { // library marker davegut.ST-Common, line 121
	def sendData = [ // library marker davegut.ST-Common, line 122
		path: "/devices", // library marker davegut.ST-Common, line 123
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 124
		] // library marker davegut.ST-Common, line 125
	asyncGet(sendData) // library marker davegut.ST-Common, line 126
} // library marker davegut.ST-Common, line 127

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 129
	def respData // library marker davegut.ST-Common, line 130
	if (resp.status != 200) { // library marker davegut.ST-Common, line 131
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 132
					httpCode: resp.status, // library marker davegut.ST-Common, line 133
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 134
	} else { // library marker davegut.ST-Common, line 135
		try { // library marker davegut.ST-Common, line 136
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 137
		} catch (err) { // library marker davegut.ST-Common, line 138
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 139
						errorMsg: err, // library marker davegut.ST-Common, line 140
						respData: resp.data] // library marker davegut.ST-Common, line 141
		} // library marker davegut.ST-Common, line 142
	} // library marker davegut.ST-Common, line 143
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 144
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 145
	} else { // library marker davegut.ST-Common, line 146
		log.info "" // library marker davegut.ST-Common, line 147
		respData.items.each { // library marker davegut.ST-Common, line 148
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 149
		} // library marker davegut.ST-Common, line 150
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 151
	} // library marker davegut.ST-Common, line 152
} // library marker davegut.ST-Common, line 153

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 155
	Integer currTime = now() // library marker davegut.ST-Common, line 156
	Integer compTime // library marker davegut.ST-Common, line 157
	try { // library marker davegut.ST-Common, line 158
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 159
	} catch (e) { // library marker davegut.ST-Common, line 160
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 161
	} // library marker davegut.ST-Common, line 162
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 163
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 164
	return timeRemaining // library marker davegut.ST-Common, line 165
} // library marker davegut.ST-Common, line 166

// ~~~~~ end include (1000) davegut.ST-Common ~~~~~

// ~~~~~ start include (998) davegut.Samsung-Soundbar-Sim ~~~~~
library ( // library marker davegut.Samsung-Soundbar-Sim, line 1
	name: "Samsung-Soundbar-Sim", // library marker davegut.Samsung-Soundbar-Sim, line 2
	namespace: "davegut", // library marker davegut.Samsung-Soundbar-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.Samsung-Soundbar-Sim, line 4
	description: "Simulator - Samsung Soundbar", // library marker davegut.Samsung-Soundbar-Sim, line 5
	category: "utilities", // library marker davegut.Samsung-Soundbar-Sim, line 6
	documentationLink: "" // library marker davegut.Samsung-Soundbar-Sim, line 7
) // library marker davegut.Samsung-Soundbar-Sim, line 8

def testData() { // library marker davegut.Samsung-Soundbar-Sim, line 10
	def supportedSources = ["digital", "HDMI1", "bluetooth", "HDMI2", "wifi"] // library marker davegut.Samsung-Soundbar-Sim, line 11
	def trackTime = 280 // library marker davegut.Samsung-Soundbar-Sim, line 12
	def trackRemain = 122 // library marker davegut.Samsung-Soundbar-Sim, line 13
	def trackData = [title: "a", artist: "b"] // library marker davegut.Samsung-Soundbar-Sim, line 14

	if (!state.onOff) { // library marker davegut.Samsung-Soundbar-Sim, line 16
		state.onOff = "on" // library marker davegut.Samsung-Soundbar-Sim, line 17
		state.pbStatus = "playing" // library marker davegut.Samsung-Soundbar-Sim, line 18
		state.volume = 11 // library marker davegut.Samsung-Soundbar-Sim, line 19
		state.inputSource = "HDMI2" // library marker davegut.Samsung-Soundbar-Sim, line 20
		state.mute = "unmuted" // library marker davegut.Samsung-Soundbar-Sim, line 21
	} // library marker davegut.Samsung-Soundbar-Sim, line 22

	return [ // library marker davegut.Samsung-Soundbar-Sim, line 24
		mediaPlayback:[ // library marker davegut.Samsung-Soundbar-Sim, line 25
			playbackStatus: [value: state.pbStatus],	// // library marker davegut.Samsung-Soundbar-Sim, line 26
			supportedPlaybackCommands:[value:[play, pause, stop]]],	//	not used // library marker davegut.Samsung-Soundbar-Sim, line 27
		audioVolume:[volume:[value: state.volume]],	// // library marker davegut.Samsung-Soundbar-Sim, line 28
		mediaInputSource:[ // library marker davegut.Samsung-Soundbar-Sim, line 29
			supportedInputSources:[value: supportedSources], 	//	devicesetup // library marker davegut.Samsung-Soundbar-Sim, line 30
			inputSource:[value: state.inputSource]],	// // library marker davegut.Samsung-Soundbar-Sim, line 31
		audioMute:[mute:[value: state.mute]],	// // library marker davegut.Samsung-Soundbar-Sim, line 32
		switch:[switch:[value: state.onOff]], // library marker davegut.Samsung-Soundbar-Sim, line 33
		audioTrackData:[ // library marker davegut.Samsung-Soundbar-Sim, line 34
			totalTime:[value: trackTime], // library marker davegut.Samsung-Soundbar-Sim, line 35
			audioTrackData:[value: trackData], // library marker davegut.Samsung-Soundbar-Sim, line 36
			elapsedTime:[value: trackRemain]] // library marker davegut.Samsung-Soundbar-Sim, line 37
	] // library marker davegut.Samsung-Soundbar-Sim, line 38
} // library marker davegut.Samsung-Soundbar-Sim, line 39
def testResp(cmdData) { // library marker davegut.Samsung-Soundbar-Sim, line 40
	def cmd = cmdData.command // library marker davegut.Samsung-Soundbar-Sim, line 41
	def args = cmdData.arguments // library marker davegut.Samsung-Soundbar-Sim, line 42
	switch(cmd) { // library marker davegut.Samsung-Soundbar-Sim, line 43
		case "off": // library marker davegut.Samsung-Soundbar-Sim, line 44
			state.onOff = "off" // library marker davegut.Samsung-Soundbar-Sim, line 45
			break // library marker davegut.Samsung-Soundbar-Sim, line 46
		case "on": // library marker davegut.Samsung-Soundbar-Sim, line 47
			state.onOff = "on" // library marker davegut.Samsung-Soundbar-Sim, line 48
			break // library marker davegut.Samsung-Soundbar-Sim, line 49
		case "play": // library marker davegut.Samsung-Soundbar-Sim, line 50
			state.pbStatus = "playing" // library marker davegut.Samsung-Soundbar-Sim, line 51
			break // library marker davegut.Samsung-Soundbar-Sim, line 52
		case "pause": // library marker davegut.Samsung-Soundbar-Sim, line 53
			state.pbStatus = "paused" // library marker davegut.Samsung-Soundbar-Sim, line 54
			break // library marker davegut.Samsung-Soundbar-Sim, line 55
		case "stop": // library marker davegut.Samsung-Soundbar-Sim, line 56
			state.pbStatus = "stopped" // library marker davegut.Samsung-Soundbar-Sim, line 57
			break // library marker davegut.Samsung-Soundbar-Sim, line 58
		case "setVolume": // library marker davegut.Samsung-Soundbar-Sim, line 59
			state.volume = args[0] // library marker davegut.Samsung-Soundbar-Sim, line 60
			break // library marker davegut.Samsung-Soundbar-Sim, line 61
		case "setInputSource": // library marker davegut.Samsung-Soundbar-Sim, line 62
			state.inputSource = args[0] // library marker davegut.Samsung-Soundbar-Sim, line 63
			break // library marker davegut.Samsung-Soundbar-Sim, line 64
		case "mute": // library marker davegut.Samsung-Soundbar-Sim, line 65
			state.mute = "muted" // library marker davegut.Samsung-Soundbar-Sim, line 66
			break // library marker davegut.Samsung-Soundbar-Sim, line 67
		case "unmute": // library marker davegut.Samsung-Soundbar-Sim, line 68
			state.mute = "unmuted"		 // library marker davegut.Samsung-Soundbar-Sim, line 69
			break // library marker davegut.Samsung-Soundbar-Sim, line 70
		case "playTrack": // library marker davegut.Samsung-Soundbar-Sim, line 71
			break // library marker davegut.Samsung-Soundbar-Sim, line 72
		case "refresh": // library marker davegut.Samsung-Soundbar-Sim, line 73
			break // library marker davegut.Samsung-Soundbar-Sim, line 74
		default: // library marker davegut.Samsung-Soundbar-Sim, line 75
			logWarn("testResp: [unhandled: ${cmdData}]") // library marker davegut.Samsung-Soundbar-Sim, line 76
	} // library marker davegut.Samsung-Soundbar-Sim, line 77

	return [ // library marker davegut.Samsung-Soundbar-Sim, line 79
		cmdData: cmdData, // library marker davegut.Samsung-Soundbar-Sim, line 80
		status: [status: "OK", // library marker davegut.Samsung-Soundbar-Sim, line 81
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-Soundbar-Sim, line 82
} // library marker davegut.Samsung-Soundbar-Sim, line 83
def xxtestData() { // library marker davegut.Samsung-Soundbar-Sim, line 84
	def pbStatus = "playing" // library marker davegut.Samsung-Soundbar-Sim, line 85
	def sfMode = 3 // library marker davegut.Samsung-Soundbar-Sim, line 86
	def sfDetail = "External Device" // library marker davegut.Samsung-Soundbar-Sim, line 87
	def volume = 15 // library marker davegut.Samsung-Soundbar-Sim, line 88
	def inputSource = "HDMI2" // library marker davegut.Samsung-Soundbar-Sim, line 89
	def supportedSources = ["digital", "HDMI1", "bluetooth", "HDMI2", "wifi"] // library marker davegut.Samsung-Soundbar-Sim, line 90
	def mute = "unmuted" // library marker davegut.Samsung-Soundbar-Sim, line 91
	def onOff = "on" // library marker davegut.Samsung-Soundbar-Sim, line 92
	def trackTime = 280 // library marker davegut.Samsung-Soundbar-Sim, line 93
	def trackRemain = 122 // library marker davegut.Samsung-Soundbar-Sim, line 94
	def trackData = [title: "a", artist: "b", album: "c"] // library marker davegut.Samsung-Soundbar-Sim, line 95


	return [ // library marker davegut.Samsung-Soundbar-Sim, line 98
		mediaPlayback:[ // library marker davegut.Samsung-Soundbar-Sim, line 99
			playbackStatus: [value: pbStatus], // library marker davegut.Samsung-Soundbar-Sim, line 100
			supportedPlaybackCommands:[value:[play, pause, stop]]], // library marker davegut.Samsung-Soundbar-Sim, line 101
		"samsungvd.soundFrom":[ // library marker davegut.Samsung-Soundbar-Sim, line 102
			mode:[value: sfMode], // library marker davegut.Samsung-Soundbar-Sim, line 103
			detailName:[value: sfDetail]], // library marker davegut.Samsung-Soundbar-Sim, line 104
		audioVolume:[volume:[value: volume]], // library marker davegut.Samsung-Soundbar-Sim, line 105
		mediaInputSource:[ // library marker davegut.Samsung-Soundbar-Sim, line 106
			supportedInputSources:[value: supportedSources],  // library marker davegut.Samsung-Soundbar-Sim, line 107
			inputSource:[value: inputSource]], // library marker davegut.Samsung-Soundbar-Sim, line 108
		audioMute:[mute:[value: mute]], // library marker davegut.Samsung-Soundbar-Sim, line 109
		switch:[switch:[value: onOff]], // library marker davegut.Samsung-Soundbar-Sim, line 110
		audioTrackData:[ // library marker davegut.Samsung-Soundbar-Sim, line 111
			totalTime:[value: trackTime], // library marker davegut.Samsung-Soundbar-Sim, line 112
			audioTrackData:[value: trackData], // library marker davegut.Samsung-Soundbar-Sim, line 113
			elapsedTime:[value: trackRemain]]] // library marker davegut.Samsung-Soundbar-Sim, line 114
} // library marker davegut.Samsung-Soundbar-Sim, line 115
def xxtestResp(cmdData) { // library marker davegut.Samsung-Soundbar-Sim, line 116
	return [ // library marker davegut.Samsung-Soundbar-Sim, line 117
		cmdData: cmdData, // library marker davegut.Samsung-Soundbar-Sim, line 118
		status: [status: "OK", // library marker davegut.Samsung-Soundbar-Sim, line 119
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-Soundbar-Sim, line 120
} // library marker davegut.Samsung-Soundbar-Sim, line 121

// ~~~~~ end include (998) davegut.Samsung-Soundbar-Sim ~~~~~
