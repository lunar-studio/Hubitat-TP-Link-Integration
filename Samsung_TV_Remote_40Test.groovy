/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Samsung TV Remote Driver
		Copyright 2022 Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2022 Version 4.0 ====================================================================

===========================================================================================*/
def driverVer() { return "DEV 4.0" }
import groovy.json.JsonOutput

metadata {
	definition (name: "Samsung TV Remote",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungTVRemote.groovy"
			   ){
		capability "SamsungTV"			//	cmds: on/off, volume, mute. attrs: switch, volume, mute
		command "showMessage", [[name: "NOT IMPLEMENTED"]]
		command "setVolume", ["SmartThings Function"]	//	SmartThings
		command "setPictureMode", ["SmartThings Function"]	//	SmartThings
		command "setSoundMode", ["SmartThings Function"]	//	SmartThings
		capability "Switch"
		//	===== UPnP Augmentation =====
		command "pause"				//	Only work on TV Players
		command "play"					//	Only work on TV Players
		command "stop"					//	Only work on TV Players
		//	===== Remote Control Interface =====
		command "sendKey", ["string"]	//	Send entered key. eg: HDMI
		command "artMode"				//	Toggles artModeStatus
		attribute "artModeStatus", "string"	//	on/off/notFrame
		command "ambientMode"			//	non-Frame TVs
		command "ambientModeExit"		//	non-Frame TVs
		//	Cursor and Entry Control
		command "arrowLeft"
		command "arrowRight"
		command "arrowUp"
		command "arrowDown"
		command "enter"
		command "numericKeyPad"
		//	Menu Access
		command "home"
		command "menu"
		command "guide"
		command "info"					//	Pops up Info banner
		//	Source Commands
		command "source"				//	Pops up source window
		command "hdmi"					//	Direct progression through available sources
		command "setInputSource", ["SmartThings Function"]	//	SmartThings
		attribute "inputSource", "string"					//	SmartThings
		attribute "inputSources", "string"					//	SmartThings
		//	TV Channel
		command "channelList"
		command "channelUp"
		command "channelDown"
		command "previousChannel"
		command "nextChannel"
		command "setTvChannel", ["SmartThings Function"]	//	SmartThings
		attribute "tvChannel", "string"						//	SmartThings
		attribute "tvChannelName", "string"					//	SmartThings
		//	Playing Navigation Commands
		command "exit"
		command "Return"
		command "fastBack"
		command "fastForward"
		
		command "toggleInputSource", [[name: "SmartThings Function"]]	//	SmartThings
		command "toggleSoundMode", [[name: "SmartThings Function"]]	//	SmartThings
		command "togglePictureMode", [[name: "SmartThings Function"]]	//	SmartThings
		
		//	Application Access/Control
		command "appOpenByName", ["string"]
		command "appCloseNamedApp"
		command "appInstallByCode", ["string"]
		command "appOpenByCode", ["string"]
		command "appRunBrowser"
		command "appRunYouTube"
		command "appRunNetflix"
		command "appRunPrimeVideo"
		command "appRunYouTubeTV"
		command "appRunHulu"
		//	===== Button Interface =====
		capability "PushableButton"
		//	for media player tile
		command "setLevel", ["SmartThings Function"]	//	SmartThings
		attribute "transportStatus", "string"
		attribute "level", "NUMBER"
		attribute "trackDescription", "string"
		command "nextTrack", [[name: "Sets Channel Up"]]
		command "previousTrack", [[name: "Sets Channel Down"]]
	}
	preferences {
		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
		if (deviceIp) {
			input ("tvPwrOnMode", "enum", title: "TV Startup Display", 
				   options: ["ART_MODE", "Ambient", "none"], defaultValue: "none")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
			input ("infoLog", "bool", 
				   title: "Enable information logging " + helpLogo(),
				   defaultValue: true)
			input ("connectST", "bool", title: "Connect to SmartThings for added functions", defaultValue: false)
		}
		if (connectST) {
			input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
			if (stApiKey) {
				input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
			}
		}
		input ("pollInterval","enum", title: "Power Polling Interval (seconds)",
			   options: ["off","10", "15", "20", "30", "60"], defaultValue: "60")
	}
}

String helpLogo() { // library marker davegut.kasaCommon, line 11
	return """<a href="https://github.com/DaveGut/HubitatActive/blob/master/SamsungTvRemote/README.md">""" +
		"""<div style="position: absolute; top: 10px; right: 10px; height: 80px; font-size: 20px;">Samsung TV Remote Help</div></a>"""
}

//	===== Installation, setup and update =====
def installed() {
	state.token = "12345678"
	def tokenSupport = "false"
	runIn(1, updated)
}

def updated() {
	unschedule()
	def updStatus = [:]
	if (!deviceIp) {
		logWarn("\n\n\t\t<b>Enter the deviceIp and Save Preferences</b>\n\n")
		updStatus << [status: "ERROR", data: "Device IP not set."]
	} else {
		updStatus << [getDeviceData: getDeviceData()]
		if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
			updateDataValue("driverVersion", driverVer())
			updStatus << [driverVer: driverVer()]
			state.remove("offCount")
			pauseExecution(200)
			state.remove("onCount")
			pauseExecution(200)
			state.remove("pollTimeOutCount")
			pauseExection(200)
			state.remove("___2022_Model_Note___")
		}
		if (debugLog) { runIn(1800, debugLogOff) }
		updStatus << [debugLog: debugLog, infoLog: infoLog]
		updStatus << [setOnPollInterval: setOnPollInterval()]
		updStatus << [stUpdate: stUpdate()]
	}

	if (updStatus.toString().contains("ERROR")) {
		logWarn("updated: ${updStatus}")
	} else {
		logInfo("updated: ${updStatus}")
	}
}
def setOnPollInterval() {
	if (pollInterval == null) {
		pollInterval = "60"
		device.updateSetting("pollInterval", [type:"enum", value: "60"])
	}
	if (pollInterval == "60") {
		runEvery1Minute(onPoll)
	} else if (interval != "off") {
		schedule("0/${interval} * * * * ?",  onPoll)
	}
	return interval
}

def getDeviceData() {
	def respData = [:]
	if (getDataValue("uuid")) {
		respData << [status: "already run"]
	} else {
		try{
			httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
				def wifiMac = resp.data.device.wifiMac
				updateDataValue("deviceMac", wifiMac)
				def alternateWolMac = wifiMac.replaceAll(":", "").toUpperCase()
				updateDataValue("alternateWolMac", alternateWolMac)
				device.setDeviceNetworkId(alternateWolMac)
				def modelYear = "20" + resp.data.device.model[0..1]
				updateDataValue("modelYear", modelYear)
				def frameTv = "false"
				if (resp.data.device.FrameTVSupport) {
					frameTv = resp.data.device.FrameTVSupport
					sendEvent(name: "artModeStatus", value: "notFrameTV")
					respData << [artModeStatus: "notFrameTV"]
				}
				updateDataValue("frameTv", frameTv)
				if (resp.data.device.TokenAuthSupport) {
					tokenSupport = resp.data.device.TokenAuthSupport
					updateDataValue("tokenSupport", tokenSupport)
				}
				def uuid = resp.data.device.duid.substring(5)
				updateDataValue("uuid", uuid)
				respData << [status: "OK", dni: alternateWolMac, modelYear: modelYear,
							 frameTv: frameTv, tokenSupport: tokenSupport]
			}
		} catch (error) {
			respData << [status: "ERROR", reason: error]
		}
	}
	return respData
}

def stUpdate() {
	def stData = [:]
	stData << [connectST: connectST]
	if (!stApiKey || stApiKey == "") {
		logWarn("\n\n\t\t<b>Enter the ST API Key and Save Preferences</b>\n\n")
		stData << [status: "ERROR", date: "no stApiKey"]
	} else if (!stDeviceId || stDeviceId == "") {
		getDeviceList()
		logWarn("\n\n\t\t<b>Enter the deviceId from the Log List and Save Preferences</b>\n\n")
		stData << [status: "ERROR", date: "no stDeviceId"]
	} else {
		runEvery5Minutes(stRefresh)
		if (device.currentValue("volume") == null) {
			sendEvent(name: "volume", value: 0)
		}
		runIn(1, deviceSetup)
		stData << [stRefreshInterval: "5 min"]
	}
	return stData
}

//	===== Polling/Refresh Capability =====
def onPoll() {
	logDebug("asyncGet: ${sendData}, ${passData}")
	def sendCmdParams = [
		uri: "http://${deviceIp}:8001/api/v2/",
		timeout: 2
	]
	asynchttpGet("onPollParse", sendCmdParams)
}
def onPollParse(resp, data) {
	def onOff = "off"
	if (resp.status == 200) {
		def powerState = new JsonSlurper().parseText(resp.data).device.PowerState
			if (powerState == "on") {
			onOff = "on"
		}
	}
	if (device.currentValue("switch") != onOff) {
		if (onOff == "on") {
			runIn(2, setPowerOnMode)
		}
		sendEvent(name: "switch", value: onOff)
		logInfo("powerPoll: TV is ${onOff}")
	}
}
def setPowerOnMode() {
	connect("remote")
	if(tvPwrOnMode == "ART_MODE" && getDataValue("frameTv") == "true") {
		artMode()
	} else if (tvPwrOnMode == "Ambient") {
		ambientMode()
	}
}

def stRefresh() {
	if (connectST && device.currentValue("switch") == "on") {
		refresh()
	}
}

//	===== Switch Commands =====
def on() {
	logInfo("on")
	def wolMac = getDataValue("alternateWolMac")
	def cmd = "FFFFFFFFFFFF$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac"
	wol = new hubitat.device.HubAction(cmd,
									   hubitat.device.Protocol.LAN,
									   [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
										destinationAddress: "255.255.255.255:7",
										encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
	sendHubCommand(wol)
	runIn(1, onPoll)
}

def off() {
	logInfo("off")
	if (getDataValue("frameTv") == "false") {
		sendKey("POWER")
	} else {
		sendKey("POWER", "Press")
		pauseExecution(3000)
		sendKey("POWER", "Release")
	}
	runIn(1, onPoll)
	runIn(2, close)
}

def showMessage() { logWarn("showMessage: not implemented") }

//	===== Web Socket Remote Commands =====
def mute() {
	sendKey("MUTE")
	runIn(5, stRefresh)
}

def unmute() {
	sendKey("MUTE")
	runIn(5, stRefresh)
}

def volumeUp() { 
	sendKey("VOLUP") 
	runIn(5, stRefresh)
}

def volumeDown() { 
	sendKey("VOLDOWN")
	runIn(5, stRefresh)
}

def play() { sendKey("PLAY") }

def pause() { sendKey("PAUSE") }

def stop() { sendKey("STOP") }

def exit() { sendKey("EXIT") }

def Return() { sendKey("RETURN") }

def fastBack() {
	sendKey("LEFT", "Press")
	pauseExecution(1000)
	sendKey("LEFT", "Release")
}

def fastForward() {
	sendKey("RIGHT", "Press")
	pauseExecution(1000)
	sendKey("RIGHT", "Release")
}

def arrowLeft() { sendKey("LEFT") }

def arrowRight() { sendKey("RIGHT") }

def arrowUp() { sendKey("UP") }

def arrowDown() { sendKey("DOWN") }

def enter() { sendKey("ENTER") }

def numericKeyPad() { sendKey("MORE") }

def home() { sendKey("HOME") }

def menu() { sendKey("MENU") }

def guide() { sendKey("GUIDE") }

def info() { sendKey("INFO") }

def source() { 
	sendKey("SOURCE")
	runIn(5, stRefresh)
}

def hdmi() {
	sendKey("HDMI")
	runIn(5, stRefresh)
}

def channelList() { sendKey("CH_LIST") }

def channelUp() { 
	sendKey("CHUP") 
	runIn(5, stRefresh)
}

def nextTrack() { channelUp() }

def channelDown() { 
	sendKey("CHDOWN") 
	runIn(5, stRefresh)
}

def previousTrack() { channelDown() }

def previousChannel() { 
	sendKey("PRECH") 
	runIn(5, stRefresh)
}

//	===== Art Mode Implementation / Ambient Mode =====
def artMode() {
	sendKey("POWER")
	runIn(5, getArtModeStatus)
}
def getArtModeStatus() {
	def data = [request:"get_artmode_status",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	def cmdData = [method:"ms.channel.emit",
				   params:[data:"${data}",
						   to:"host",
						   event:"art_app_request"]]
	cmdData = JsonOutput.toJson(cmdData)
	sendMessage("frameArt", cmdData)
}

def ambientMode() {
	sendKey("AMBIENT")
}
def ambientModeExit() {
	sendKey("HOME")
	pauseExecution(3000)
	sendKey("HOME")
}

//	===== WebSocket Implementation =====
def sendKey(key, cmd = "Click") {
	key = "KEY_${key.toUpperCase()}"
	def data = [method:"ms.remote.control",
				params:[Cmd:"${cmd}",
						DataOfCmd:"${key}",
						TypeOfRemote:"SendRemoteKey"]]
	sendMessage("remote", JsonOutput.toJson(data) )
}

def connect(funct) {
	logDebug("connect: function = ${funct}")
	def url
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ=="
	if (getDataValue("tokenSupport") == "true") {
		if (funct == "remote") {
			url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${state.token}"
		} else if (funct == "frameArt") {
			url = "wss://${deviceIp}:8002/api/v2/channels/com.samsung.art-app?name=${name}&token=${state.token}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2/applications?name=${name}"
		} else {
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = true")
		}
	} else {
		if (funct == "remote") {
			url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
		} else if (funct == "frameArt") {
			url = "ws://${deviceIp}:8001/api/v2/channels/com.samsung.art-app?name=${name}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2?name=${name}"
		} else {
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = false")
		}
	}
	state.currentFunction = funct
	interfaces.webSocket.connect(url, ignoreSSLIssues: true)
}

def sendMessage(funct, data) {
	logDebug("sendMessage: function = ${funct} | data = ${data} | connectType = ${state.currentFunction}")
	if (state.wsDeviceStatus != "open" || state.currentFunction != funct) {
		connect(funct)
		pauseExecution(300)
	}
	interfaces.webSocket.sendMessage(data)
}

def close() {
	interfaces.webSocket.close()
}

def webSocketStatus(message) {
	def status
	if (message == "status: open") {
		state.wsDeviceStatus = "open"
		status = "open"
	} else if (message == "status: closing") {
		state.wsDeviceStatus = "closed"
		state.currentFunction = "close"
		status = "closed"
	} else if (message.substring(0,7) == "failure") {
		status = "closed-failure"
		state.wsDeviceStatus = "closed"
		state.currentFunction = "close"
		close()
	}
	logDebug("webSocketStatus: [status: ${status}, message: ${message}]")
}

def parse(resp) {
//	Parse websocket interface returns
	def logData = [:]
	try {
		resp = parseJson(resp)
		def event = resp.event
		logData << [EVENT: event]
		switch(event) {
			case "ms.channel.connect":
				def newToken = resp.data.token
				if (newToken != null && newToken != state.token) {
					state.token = newToken
					logData << [TOKEN: "updated"]
				} else {
					logData << [TOKEN: "noChange"]
				}
				break
			case "d2d_service_message":
				def data = parseJson(resp.data)
				logData << [SUB_EVENT: data.event, DATA: data]
				if (data.event == "artmode_status" ||
					data.event == "art_mode_changed") {
					def status = data.value
					if (status == null) { status = data.status }
					sendEvent(name: "artModeStatus", value: status)
					logData << [ART_MODE_STATUS: data.value]
				}
				break
			case "ms.error":
				logData << [STATUS: "Error, Closing WS",DATA: resp.data]
				close()
				break
			case "ms.channel.ready":
			case "ms.channel.clientConnect":
			case "ms.channel.clientDisconnect":
				break
			default:
				logData << [STATUS: "Not Parsed", DATA: resp.data]
				break
		}
		logDebug("parse: ${logData}")
	} catch (e) {
		logWarn("parse: [STATUS: unhandled, ERROR: ${e}]")
	}
}

//	===== LAN TV Apps Implementation =====
def appRunBrowser() { appOpenByCode("org.tizen.browser") }

def appRunYouTube() { appOpenByName("YouTube") }

def appRunNetflix() { appOpenByName("Netflix") }

def appRunPrimeVideo() { appOpenByName("AmazonInstantVideo") }

def appRunYouTubeTV() { appOpenByName("YouTubeTV") }

def appRunHulu() { appOpenByCode("3201601007625") }

//	HTTP Commands
def appOpenByName(appName) {
	def url = "http://${deviceIp}:8080/ws/apps/${appName}"
	try {
		httpPost(url, "") { resp ->
			logDebug("appOpenByName: [name: ${appName}, status: ${resp.status}, data: ${resp.data}]")
		}
	} catch (e) {
		logWarn("appOpenByName: [name: ${appName}, status: FAILED, data: ${e}]")
	}
}

def appOpenByCode(appId) {
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}"
	try {
		httpPost(uri, body) { resp ->
			logDebug("appOpenByCode: [code: ${appId}, status: ${resp.status}, data: ${resp.data}]")
		}
		runIn(5, appGetData, [data: appId]) 
	} catch (e) {
		logWarn("appOpenByCode: [code: ${appId}, status: FAILED, data: ${e}]")
	}
}

//	===== SmartThings Implementation =====
def setLevel(level) { setVolume(level) }

def setVolume(volume) {
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: "setVolume",
		arguments: [volume.toInteger()]]
	deviceCommand(cmdData)
}

def togglePictureMode() {
	//	requires state.pictureModes
	def pictureModes = state.pictureModes
	def totalModes = pictureModes.size()
	def currentMode = device.currentValue("pictureMode")
	def modeNo = pictureModes.indexOf(currentMode)
	def newModeNo = modeNo + 1
	if (newModeNo == totalModes) { newModeNo = 0 }
	def newPictureMode = pictureModes[newModeNo]
	setPictureMode(newPictureMode)
}

def setPictureMode(pictureMode) {
	def cmdData = [
		component: "main",
		capability: "custom.picturemode",
		command: "setPictureMode",
		arguments: [pictureMode]]
	deviceCommand(cmdData)
}

def toggleSoundMode() {
	def soundModes = state.soundModes
	def totalModes = soundModes.size()
	def currentMode = device.currentValue("soundMode")
	def modeNo = soundModes.indexOf(currentMode)
	def newModeNo = modeNo + 1
	if (newModeNo == totalModes) { newModeNo = 0 }
	def soundMode = soundModes[newModeNo]
	setSoundMode(soundMode)
}

def setSoundMode(soundMode) { 
	def cmdData = [
		component: "main",
		capability: "custom.soundmode",
		command: "setSoundMode",
		arguments: [soundMode]]
	deviceCommand(cmdData)
}

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
	def cmdData = [
		component: "main",
		capability: "mediaInputSource",
		command: "setInputSource",
		arguments: [inputSource]]
	deviceCommand(cmdData)
}

def setTvChannel(newChannel) {
	def cmdData = [
		component: "main",
		capability: "tvChannel",
		command: "setTvChannel",
		arguments: [newChannel]]
	deviceCommand(cmdData)
}

//	===== Parse and Update TV SmartThings Data =====
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
	
	def pictureModes = mainData["custom.picturemode"].supportedPictureModes.value
	sendEvent(name: "pictureModes",value: pictureModes)
	state.pictureModes = pictureModes
	setupData << [pictureModes: pictureModes]
	
	def soundModes =  mainData["custom.soundmode"].supportedSoundModes.value
	sendEvent(name: "soundModes",value: soundModes)
	state.soundModes = soundModes
	setupData << [soundModes: soundModes]
	
	logInfo("deviceSetupParse: ${setupData}")
}

def statusParse(mainData) {
	def stData = [:]
	if (onOff == "on") {
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
	
		def inputSource = mainData.mediaInputSource.inputSource.value
		if (device.currentValue("inputSource") != inputSource) {
			sendEvent(name: "inputSource", value: inputSource)		
			stData << [inputSource: inputSource]
		}
		
		def tvChannel = mainData.tvChannel.tvChannel.value
		if (tvChannel == "") { tvChannel = " " }
		def tvChannelName = mainData.tvChannel.tvChannelName.value
		if (tvChannelName == "") { tvChannelName = " " }
		if (device.currentValue("tvChannel") != tvChannel) {
			sendEvent(name: "tvChannel", value: tvChannel)
			sendEvent(name: "tvChannelName", value: tvChannelName)
			stData << [tvChannel: tvChannel, tvChannelName: tvChannelName]
		}
		
		def trackDesc = inputSource
		if (tvChannelName != " ") { trackDesc = tvChannelName }
		if (device.currentValue("trackDescription") != trackDesc) {
			sendEvent(name: "trackDescription", value:trackDesc)
			stData << [trackDescription: trackDesc]
		}
	
		def pictureMode = mainData["custom.picturemode"].pictureMode.value
		if (device.currentValue("pictureMode") != pictureMode) {
			sendEvent(name: "pictureMode",value: pictureMode)
			stData << [pictureMode: pictureMode]
		}
	
		def soundMode = mainData["custom.soundmode"].soundMode.value
		if (device.currentValue("soundMode") != soundMode) {
			sendEvent(name: "soundMode",value: soundMode)
			stData << [soundMode: soundMode]
		}
	
		def transportStatus = mainData.mediaPlayback.playbackStatus.value
		if (transportStatus == null || transportStatus == "") {
			transportStatus = "stopped"
		}
		if (device.currentValue("transportStatus") != transportStatus) {
			sendEvent(name: "transportStatus", value: transportStatus)
			stData << [transportStatus: transportStatus]
		}
	}
	
	if (stData != [:]) {
		logInfo("statusParse: ${stData}")
	}
}

//	===== Button Interface (facilitates dashboard integration) =====
def push(pushed) {
	logDebug("push: button = ${pushed}, trigger = ${state.triggered}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	pushed = pushed.toInteger()
	switch(pushed) {
		//	===== Physical Remote Commands =====
		case 2 : mute(); break
		case 3 : numericKeyPad(); break
		case 4 : Return(); break
		case 6 : artMode(); break			//	New command.  Toggles art mode
		case 7 : ambientMode(); break
		case 45: ambientmodeExit(); break
		case 8 : arrowLeft(); break
		case 9 : arrowRight(); break
		case 10: arrowUp(); break
		case 11: arrowDown(); break
		case 12: enter(); break
		case 13: exit(); break
		case 14: home(); break
		case 18: channelUp(); break
		case 19: channelDown(); break
		case 20: guide(); break
		case 21: volumeUp(); break
		case 22: volumeDown(); break
		//	===== Direct Access Functions
		case 23: menu(); break			//	Main menu with access to system settings.
		case 24: source(); break		//	Pops up home with cursor at source.  Use left/right/enter to select.
		case 25: info(); break			//	Pops up upper display of currently playing channel
		case 26: channelList(); break	//	Pops up short channel-list.
		//	===== Other Commands =====
		case 34: previousChannel(); break
		case 35: hdmi(); break			//	Brings up next available source
		case 36: fastBack(); break		//	causes fast forward
		case 37: fastForward(); break	//	causes fast rewind
		case 38: appRunBrowser(); break		//	Direct to source 1 (ofour right of TV on menu)
		case 39: appRunYouTube(); break
		case 40: appRunNetflix(); break
		case 42: toggleSoundMode(); break
		case 43: togglePictureMode(); break
		case 44: setPictureMode("Dynamic"); break
		default:
			logDebug("push: Invalid Button Number!")
			break
	}
}

//	===== Library Integration =====



def simulate() { return false }

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
	log.trace "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 27
} // library marker davegut.Logging, line 28

def logInfo(msg) {  // library marker davegut.Logging, line 30
	if (textEnable || infoLog) { // library marker davegut.Logging, line 31
		log.info "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def debugLogOff() { // library marker davegut.Logging, line 36
	if (logEnable) { // library marker davegut.Logging, line 37
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} // library marker davegut.Logging, line 39
	logInfo("debugLogOff") // library marker davegut.Logging, line 40
} // library marker davegut.Logging, line 41

def logDebug(msg) { // library marker davegut.Logging, line 43
	if (logEnable || debugLog) { // library marker davegut.Logging, line 44
		log.debug "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 45
	} // library marker davegut.Logging, line 46
} // library marker davegut.Logging, line 47

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.Logging, line 49

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
						 errorMsg: error] // library marker davegut.ST-Communications, line 52
		} // library marker davegut.ST-Communications, line 53
	} // library marker davegut.ST-Communications, line 54
	return respData // library marker davegut.ST-Communications, line 55
} // library marker davegut.ST-Communications, line 56

private syncPost(sendData){ // library marker davegut.ST-Communications, line 58
	def respData = [:] // library marker davegut.ST-Communications, line 59
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 60
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 61
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 62
	} else { // library marker davegut.ST-Communications, line 63
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 64
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 65
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 66
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 67
			path: sendData.path, // library marker davegut.ST-Communications, line 68
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 69
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 70
		] // library marker davegut.ST-Communications, line 71
		try { // library marker davegut.ST-Communications, line 72
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 73
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 74
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 75
				} else { // library marker davegut.ST-Communications, line 76
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 77
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 78
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 79
				} // library marker davegut.ST-Communications, line 80
			} // library marker davegut.ST-Communications, line 81
		} catch (error) { // library marker davegut.ST-Communications, line 82
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 83
						 errorMsg: error] // library marker davegut.ST-Communications, line 84
		} // library marker davegut.ST-Communications, line 85
	} // library marker davegut.ST-Communications, line 86
	return respData // library marker davegut.ST-Communications, line 87
} // library marker davegut.ST-Communications, line 88

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
	updateData << [textEnable: textEnable, logEnable: logEnable] // library marker davegut.ST-Common, line 24
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
		case "1" : // library marker davegut.ST-Common, line 50
		case "1min": runEvery1Minute(poll); break // library marker davegut.ST-Common, line 51
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 52
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 53
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 54
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 55
	} // library marker davegut.ST-Common, line 56
} // library marker davegut.ST-Common, line 57

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 59
	def respData = [:] // library marker davegut.ST-Common, line 60
	if (simulate() == true) { // library marker davegut.ST-Common, line 61
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 62
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 63
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 64
	} else { // library marker davegut.ST-Common, line 65
		def sendData = [ // library marker davegut.ST-Common, line 66
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 67
			cmdData: cmdData // library marker davegut.ST-Common, line 68
		] // library marker davegut.ST-Common, line 69
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 70
	} // library marker davegut.ST-Common, line 71
	if (respData.results.status[0] != "FAILED") { // library marker davegut.ST-Common, line 72
		if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 73
			refresh() // library marker davegut.ST-Common, line 74
		} else { // library marker davegut.ST-Common, line 75
			poll() // library marker davegut.ST-Common, line 76
		} // library marker davegut.ST-Common, line 77
	} // library marker davegut.ST-Common, line 78
	return respData // library marker davegut.ST-Common, line 79
} // library marker davegut.ST-Common, line 80

def refresh() { // library marker davegut.ST-Common, line 82
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 83
		def cmdData = [ // library marker davegut.ST-Common, line 84
			component: "main", // library marker davegut.ST-Common, line 85
			capability: "refresh", // library marker davegut.ST-Common, line 86
			command: "refresh", // library marker davegut.ST-Common, line 87
			arguments: []] // library marker davegut.ST-Common, line 88
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 89
	} // library marker davegut.ST-Common, line 90
} // library marker davegut.ST-Common, line 91

def poll() { // library marker davegut.ST-Common, line 93
	if (simulate() == true) { // library marker davegut.ST-Common, line 94
		pauseExecution(200) // library marker davegut.ST-Common, line 95
		def children = getChildDevices() // library marker davegut.ST-Common, line 96
		if (children) { // library marker davegut.ST-Common, line 97
			children.each { // library marker davegut.ST-Common, line 98
				it.statusParse(testData()) // library marker davegut.ST-Common, line 99
			} // library marker davegut.ST-Common, line 100
		} // library marker davegut.ST-Common, line 101
		statusParse(testData()) // library marker davegut.ST-Common, line 102
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 103
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 104
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 105
	} else { // library marker davegut.ST-Common, line 106
		def sendData = [ // library marker davegut.ST-Common, line 107
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 108
			parse: "distResp" // library marker davegut.ST-Common, line 109
			] // library marker davegut.ST-Common, line 110
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 111
	} // library marker davegut.ST-Common, line 112
} // library marker davegut.ST-Common, line 113

def deviceSetup() { // library marker davegut.ST-Common, line 115
	if (simulate() == true) { // library marker davegut.ST-Common, line 116
		def children = getChildDevices() // library marker davegut.ST-Common, line 117
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 118
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 119
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 120
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 121
	} else { // library marker davegut.ST-Common, line 122
		def sendData = [ // library marker davegut.ST-Common, line 123
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 124
			parse: "distResp" // library marker davegut.ST-Common, line 125
			] // library marker davegut.ST-Common, line 126
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 127
	} // library marker davegut.ST-Common, line 128
} // library marker davegut.ST-Common, line 129

def getDeviceList() { // library marker davegut.ST-Common, line 131
	def sendData = [ // library marker davegut.ST-Common, line 132
		path: "/devices", // library marker davegut.ST-Common, line 133
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 134
		] // library marker davegut.ST-Common, line 135
	asyncGet(sendData) // library marker davegut.ST-Common, line 136
} // library marker davegut.ST-Common, line 137

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 139
	def respData // library marker davegut.ST-Common, line 140
	if (resp.status != 200) { // library marker davegut.ST-Common, line 141
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 142
					httpCode: resp.status, // library marker davegut.ST-Common, line 143
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 144
	} else { // library marker davegut.ST-Common, line 145
		try { // library marker davegut.ST-Common, line 146
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 147
		} catch (err) { // library marker davegut.ST-Common, line 148
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 149
						errorMsg: err, // library marker davegut.ST-Common, line 150
						respData: resp.data] // library marker davegut.ST-Common, line 151
		} // library marker davegut.ST-Common, line 152
	} // library marker davegut.ST-Common, line 153
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 154
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 155
	} else { // library marker davegut.ST-Common, line 156
		log.info "" // library marker davegut.ST-Common, line 157
		respData.items.each { // library marker davegut.ST-Common, line 158
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 159
		} // library marker davegut.ST-Common, line 160
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 161
	} // library marker davegut.ST-Common, line 162
} // library marker davegut.ST-Common, line 163

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 165
	Integer currTime = now() // library marker davegut.ST-Common, line 166
	Integer compTime // library marker davegut.ST-Common, line 167
	try { // library marker davegut.ST-Common, line 168
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 169
	} catch (e) { // library marker davegut.ST-Common, line 170
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 171
	} // library marker davegut.ST-Common, line 172
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 173
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 174
	return timeRemaining // library marker davegut.ST-Common, line 175
} // library marker davegut.ST-Common, line 176

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~
