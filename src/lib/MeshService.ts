import { Platform } from 'react-native';
import { bytesToHex } from '@noble/hashes/utils.js';
import { bytesToBase64, base64ToBytes } from './base64';
import { insertMessage, MessageRecord } from './store';
import {
    getOrCreateIdentity,
    sealDirectMessage,
    openDirectMessage,
    IdentityKeyPair,
} from './crypto-core';
import { packEnvelope, unpackEnvelope, EnvelopeType } from './protocol';
import { EnvelopeDispatcher, ITransport } from './dispatcher';

let BleMeshModule: any = null;
try {
    if (Platform.OS !== 'web') {
        BleMeshModule = require('../../modules/ble-mesh').default;
    }
} catch (e) {
    console.warn('BleMesh active module not found, running in mock mode.');
}

class MasterMeshService {
    private identity: IdentityKeyPair | null = null;
    private dispatcher: EnvelopeDispatcher | null = null;
    private onMessageListeners: Array<() => void> = [];

    async initialize(): Promise<void> {
        console.log('[MeshService] Initializing...');
        this.identity = await getOrCreateIdentity();
        console.log('[MeshService] Identity loaded. Public ID:', this.identity.publicId);

        const transportBridge: ITransport = {
            send: (bytes: Uint8Array) => {
                if (BleMeshModule?.writeRaw) {
                    const b64 = bytesToBase64(bytes);
                    BleMeshModule.writeRaw('broadcast', b64);
                } else {
                    console.log('[MeshService] (Radio Mock) Transmitted', bytes.length, 'bytes');
                }
            },
            on: (event: 'payload', callback: (data: Uint8Array) => void) => {
                if (BleMeshModule?.addListener) {
                    BleMeshModule.addListener('onRawData', (event: { peerId: string; base64Bytes: string}) => {
                        const raw = base64ToBytes(event.base64Bytes);
                        callback(raw);
                    });
                }
            },
        };

        this.dispatcher = new EnvelopeDispatcher(transportBridge, (rawEnvelope) => {
            this.handleIncomingEnvelope(rawEnvelope);
        });

        if (BleMeshModule?.startRadio) {
            BleMeshModule.startRadio();
        }
    }

    async sendMessage(peerId: string, content: string, senderId: string = 'me'): Promise<MessageRecord> {
        if (!this.identity) {
            await this.initialize();
        }

        const messageId = Math.random().toString(36).substring(2, 15);
        const timestamp = Date.now();

        const newMessage: MessageRecord = {
            id: messageId,
            peer_id: peerId,
            sender: senderId,
            content: content,
            timestamp,
            status: 'pending',
        };
        await insertMessage(newMessage);
        const sealedPayload = sealDirectMessage({
            sender:  this.identity!,
            recipientXPublic: this.identity!.xPublic,
            body: content,
        });

        const envelopeBytes = packEnvelope({
            type: EnvelopeType.Sealed,
            payload: sealedPayload,
        });

        this.dispatcher?.dispatchOutgoing(envelopeBytes);
        return newMessage;
    }

    private async handleIncomingEnvelope(rawEnvelopeBytes: Uint8Array): Promise<void> {
        try {
            if (!this.identity) return;

            const unpacked = unpackEnvelope(rawEnvelopeBytes);
            if (unpacked.header.type !== EnvelopeType.Sealed) return;

            const decrypted = openDirectMessage({
                recipient: this.identity,
                packedPayload: unpacked.payload,
            });

            const envelopeIdHex = bytesToHex(unpacked.header.envelopeId);
            const incomingMessage: MessageRecord = {
                id: envelopeIdHex,
                peer_id: 'global_mesh_room',
                sender: decrypted.senderPublicId,
                content: decrypted.body,
                timestamp: unpacked.header.timestamp,
                status: 'delivered',
            };

            await insertMessage(incomingMessage);
            this.notifyListeners();
        } catch (err) {
            console.warn('[MeshService] Decryption or verification dropped packet:', err);
        }
    }

    subscribe(callback: () => void): () => void {
        this.onMessageListeners.push(callback);
        return () => {
            this.onMessageListeners = this.onMessageListeners.filter((cb) => cb !== callback);
        };
    }

    private notifyListeners(): void {
        for (const listener of this.onMessageListeners) {
            listener();
        }
    }

    async onMessageReceived(text: string, senderPeerId: string): Promise<void> {
        if (!this.identity) await this.initialize();

        // Create a dummy sealed envelope
        const sealedPayload = sealDirectMessage({
            sender: this.identity!,
            recipientXPublic: this.identity!.xPublic,
            body: text,
        });
        const envelope = packEnvelope({
            type: EnvelopeType.Sealed,
            payload: sealedPayload,
        });

        await this.handleIncomingEnvelope(envelope);
    }
}
export const MeshService = new MasterMeshService();