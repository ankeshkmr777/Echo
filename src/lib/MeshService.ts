// src/lib/MeshService.ts
import { insertMessage, MessageRecord } from './store';
import { BleManager } from 'react-native-ble-plx';
import { Platform } from 'react-native';

// interface to represent nearby discovered device
export interface PeerDevice {
    // mac address or unique discovered id
    id: string; 
    // display name of peer
    name: string; 
    // signal strength
    rssi: number; 
}

class BluetoothMeshService {
    private isScanning: boolean = false;
    private connectedPeers: Map<string, PeerDevice> = new Map();
    
    // Allow this to be null if the native module is missing in Expo Go
    private bleManager: BleManager | null = null; 

    constructor() {
        try {
            // Only attempt to initialize BleManager if not on the web
            if (Platform.OS !== 'web') {
                this.bleManager = new BleManager();
            }
        } catch (error) {
            console.warn("Native BLE module missing. Running in UI-only mode.");
        }
    }

    /* initialise bluetooth radio and starts advertising this device
       to nearby users */
    async initialize(): Promise<void> {
        console.log("Mesh service: Initiating bluetooth radio");
        
        if (!this.bleManager) {
            console.warn("Bluetooth bypassed: Native module not available.");
            return;
        }

        const state = await this.bleManager.state();
        if (state === 'PoweredOff') {
            console.warn("Bluetooth is turned off on this device");
            return;
        }
        this.startScanning();
    }
    
    private startScanning() {
        if (this.isScanning || !this.bleManager) return;
        this.isScanning = true;
        console.log("Mesh service: Scanning for peer devices");
        
        this.bleManager.startDeviceScan(null, null, (error, device) => {
            if (error) {
                console.warn("Scan error", error);
                return;
            }
            if (device && device.name) {
                console.log(`Found device ${device.name}`);
                this.connectedPeers.set(device.id, {
                    id: device.id,
                    name: device.name,
                    rssi: device.rssi || 0
                });
            }
        });
    }

    async sendMessage(peerId: string, content: string, senderId: string = 'me'): Promise<MessageRecord> {
        const newMessage: MessageRecord = {
            id: Math.random().toString(36).substring(2, 15),
            peer_id: peerId,
            sender: senderId,
            content: content,
            timestamp: Date.now(),
            status: "pending",
        };
        await insertMessage(newMessage);
        console.log(`Mesh service: Transmitting message to ${peerId}`);
        return newMessage;
    }

    async onMessageReceived(rawPayLoad: string, senderPeerId: string) {
        console.log(`Mesh service: Message received from ${senderPeerId}`);
        const incomingMessage: MessageRecord = {
            id: Math.random().toString(36).substring(2, 15),
            peer_id: senderPeerId,
            /* By setting both peer_id: senderPeerId and sender: senderPeerId 
               on incoming messages, the message is placed in the sender's 
               conversation feed while still recording that they are the one who sent it. */
            sender: senderPeerId,
            content: rawPayLoad,
            timestamp: Date.now(),
            status: 'delivered',
        };
        await insertMessage(incomingMessage);
    }
}

export const MeshService = new BluetoothMeshService();