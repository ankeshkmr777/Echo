package expo.modules.blemesh

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class BleMeshModule : Module() {

  private val centralManager by lazy {
    appContext.reactContext?.let { context ->
      val manager = CentralManager(context)
      
      // Kotlin lambda syntax for a single-method interface
      manager.setListener { peerId ->
        sendEvent("onDeviceDisconnected", mapOf("peerId" to peerId))
      }
      
      manager
    }
  }

  private val peripheralManager by lazy {
    appContext.reactContext?.let { context ->
      val manager = PeripheralManager(context)
      
      // Kotlin object syntax for a multi-method interface
      manager.setListener(object : PeripheralManager.MeshEventListener {
        override fun onMessageReceived(base64Bytes: String) {
          sendEvent("onRawData", mapOf("base64Bytes" to base64Bytes, "payload" to base64Bytes))
        }

        override fun onDeviceDisconnected(peerId: String) {
          sendEvent("onDeviceDisconnected", mapOf("peerId" to peerId))
        }
      })
      
      manager
    }
  }

  override fun definition() = ModuleDefinition {
    Name("BleMesh")

    Events("onRawData", "onDeviceDisconnected")

    Function("startRadio") {
      centralManager?.startScanning()
      peripheralManager?.startAdvertising()
    }
    
    Function("stopRadio") {
      centralManager?.stopScanning()
      peripheralManager?.stopAdvertising()
    }

    Function("writeRaw") { peerId: String, base64Bytes: String ->
      centralManager?.writeRawData(base64Bytes)
    }
  }
}