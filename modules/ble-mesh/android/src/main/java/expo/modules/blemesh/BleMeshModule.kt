package expo.modules.blemesh

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class BleMeshModule : Module() {
  
  // 1. Instantiate your Java objects lazily (they only get created when needed)
  // appContext.reactContext is the "VIP pass" (Context) that your Java constructors require
  private val centralManager by lazy {
    appContext.reactContext?.let { CentralManager(it) }
  }
  
  private val peripheralManager by lazy {
    appContext.reactContext?.let { PeripheralManager(it) }
  }

  override fun definition() = ModuleDefinition {
    Name("BleMesh")

    Events("onRawData")

    // 2. The JavaScript team calls this to turn on the mesh
    Function("startRadio") {
      centralManager?.startScanning()
      peripheralManager?.startAdvertising()
    }
    
    // 3. The JavaScript team calls this to turn off the mesh
    Function("stopRadio") {
      centralManager?.stopScanning()
      peripheralManager?.stopAdvertising()
    }

    // 4. We will wire this up in the next phase (GATT Connection)
    Function("writeRaw") { peerId: String, base64Bytes: String ->
      // The JS side calls this, and we route the payload directly to the Java antenna driver
      centralManager?.writeRawData(base64Bytes)
    }
  }
}