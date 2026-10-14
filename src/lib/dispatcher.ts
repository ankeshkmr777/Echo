// ==========================================
// 1. DATA CONTRACTS
// ==========================================

// Defines the shape of the message object 
export interface Envelope {
  id: string;
  payload: string;
  hopCount: number;
}

// Defines how we interact with Dev 1's Radio
export interface ITransport {
  send(payload: string): void;
  on(event: 'payload', callback: (data: string) => void): void;
}

// ==========================================
// 2. THE DISPATCHER COMPONENT
// ==========================================

export class EnvelopeDispatcher {
  // The 'Bouncer Notepad': Tracks IDs we have already processed
  private seen: Set<string> = new Set();
  
  // The Waiting Line: Holds messages waiting to be sent
  private outgoingQueue: Envelope[] = [];

  // Injects the radio (transport) and the UI delivery function
  constructor(
    private transport: ITransport, 
    private onDeliverToApp: (envelope: Envelope) => void
  ) {
    // Listen to the radio's 'payload' channel and route data to handleIncoming
    this.transport.on('payload', (rawString) => this.handleIncoming(rawString));
  }

  /**
   * ACTION 1: SENDING OUT (Alice -> Radio)
   * Takes an Envelope, logs it, queues it, and broadcasts it.
   */
  public dispatchOutgoing(envelope: Envelope): void {
    // 1. Mark as seen so we don't process our own broadcast if it echoes back
    this.seen.add(envelope.id);
    
    // 2. Add to outgoing line
    this.outgoingQueue.push(envelope);
    
    // 3. Smash the object into a raw string and fire it over the radio
    this.transport.send(JSON.stringify(envelope));
  }

  /**
   * ACTION 2: RECEIVING (Radio -> Bob)
   * Receives a raw string, inflates it, drops duplicates, and delivers to UI.
   */
  public handleIncoming(rawString: string): boolean {
    let envelope: Envelope;

    // 1. Inflate the raw string back into a TypeScript Object
    try { 
      envelope = JSON.parse(rawString); 
    } catch (error) { 
      return false; // Drop corrupted packets
    }

    // 2. Deduplication check: Drop packet if ID is already in our notepad
    if (this.seen.has(envelope.id)) {
      return false; 
    }

    // 3. Log the new ID into the notepad
    this.seen.add(envelope.id);

    // 4. Deliver the valid Envelope object up to Dev 4's UI
    this.onDeliverToApp(envelope);
    
    return true; 
  }
}