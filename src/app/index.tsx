// src/app/index.tsx
import { useEffect, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from 'react-native';
import { MeshService } from '../lib/MeshService';
import { MessageRecord, getMessagesForPeer } from '../lib/store';

// For testing purposes, we'll hardcode a peer ID representing the room
const ACTIVE_PEER_ID = 'global_mesh_room';

export default function ChatScreen() {
  const [messages, setMessages] = useState<MessageRecord[]>([]);
  const [inputText, setInputText] = useState('');

  // 1. Load messages from the local SQLite database when the screen opens
  const loadMessages = async () => {
    const storedMessages = await getMessagesForPeer(ACTIVE_PEER_ID);
    setMessages(storedMessages);
  };

  useEffect(() => {
    loadMessages();

    //  Check the local database every 2 seconds for new incoming mesh messages
    const refreshInterval = setInterval(() => {
      loadMessages();
    }, 2000);

    // Cleanup interval if the user leaves the screen
    return () => clearInterval(refreshInterval);
  }, []);

  // 2. Handle sending a new message
  const handleSend = async () => {
    if (inputText.trim() === '') return;

    // Send the message through the MeshService (which saves it to SQLite and broadcasts it)
    await MeshService.sendMessage(ACTIVE_PEER_ID, inputText.trim(), 'me');
    
    // Clear the input box and reload the UI
    setInputText('');
    loadMessages(); 
  };
  //  A temporary developer function to test receiving a message from someone else
  const simulateReceive = async () => {
    await MeshService.onMessageReceived("Loud and clear! This is a simulated mesh reply.", ACTIVE_PEER_ID);
    loadMessages(); // Instantly refresh to show the grey bubble
  };

  // 3. Render individual message bubbles
  const renderMessage = ({ item }: { item: MessageRecord }) => {
    const isMe = item.sender === 'me';
    return (
      <View style={[styles.messageBubble, isMe ? styles.myMessage : styles.peerMessage]}>
        <Text style={isMe ? styles.myMessageText : styles.peerMessageText}>
          {item.content}
        </Text>
        <Text style={styles.timestamp}>
          {new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          {isMe && ` • ${item.status}`}
        </Text>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container} 
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={90}
    >
      {/* INVERTED FLATLIST: Renders the newest messages at the bottom of the screen */}
      <FlatList
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={renderMessage}
        inverted={true} 
        contentContainerStyle={styles.listContent}
      />

      {/* MESSAGE INPUT AREA */}
      <View style={styles.inputContainer}>
        <TextInput
          style={styles.textInput}
          value={inputText}
          onChangeText={setInputText}
          placeholder="Type a message..."
          placeholderTextColor="#999"
        />
        <TouchableOpacity style={styles.sendButton} onPress={handleSend}>
          <Text style={styles.sendButtonText}>Send</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

//  Clean, modern styling for the chat bubbles and layout
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 16,
  },
  messageBubble: {
    maxWidth: '80%',
    padding: 12,
    borderRadius: 16,
    marginVertical: 4,
  },
  myMessage: {
    alignSelf: 'flex-end',
    backgroundColor: '#007AFF',
    borderBottomRightRadius: 4,
  },
  peerMessage: {
    alignSelf: 'flex-start',
    backgroundColor: '#E5E5EA',
    borderBottomLeftRadius: 4,
  },
  myMessageText: {
    color: '#fff',
    fontSize: 16,
  },
  peerMessageText: {
    color: '#000',
    fontSize: 16,
  },
  timestamp: {
    fontSize: 10,
    color: 'rgba(255, 255, 255, 0.7)',
    alignSelf: 'flex-end',
    marginTop: 4,
  },
  inputContainer: {
    flexDirection: 'row',
    padding: 12,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderColor: '#e5e5e5',
    alignItems: 'center',
  },
  textInput: {
    flex: 1,
    backgroundColor: '#f0f0f0',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 16,
    maxHeight: 100,
  },
  sendButton: {
    marginLeft: 12,
    backgroundColor: '#007AFF',
    borderRadius: 20,
    paddingVertical: 10,
    paddingHorizontal: 20,
  },
  sendButtonText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 16,
  },
});
