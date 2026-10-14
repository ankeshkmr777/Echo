import * as SQLite from "expo-sqlite";
export interface MessageRecord{
    id:string;  //unique message UUI
    peer_id:string; //conversation identifier (id of receiver of message {final receiver})
    sender:string;// identifier of the sender
    content:string;  //plaintext body of message
    timestamp:number;//unix timestamp in milliseconds (time in ms form jan1 1970)
    status:"pending"|"relayed"|"delivered";//status of the message
}
let dbInstance:SQLite.SQLiteDatabase|null=null;
/**
 * Initializes and returns the local SQLite database singleton.
 * Automatically executes table schema creation on first run.
 */
export async function getDatabase():Promise<SQLite.SQLiteDatabase>{
    if(!dbInstance){
        dbInstance=await SQLite.openDatabaseAsync("chitchat.db");
    
    //// Initialize local tables required for chat persistence
    await dbInstance.execAsync(`
        PRAGMA journal_mode = WAL;
        CREATE TABLE IF NOT EXISTS messages(
           id TEXT PRIMARY KEY NOT NULL,
           peer_id TEXT NOT NULL,
           sender TEXT NOT NULL,
           content TEXT NOT NULL,
           timestamp INTEGER NOT NULL,
           status TEXT NOT NULL

           );
           `);
    }
    return dbInstance;
}
/**
 * Inserts a new sent or received message into the local messages table.
 */
//messages is name of database table created inside database 
export async function insertMessage(msg:MessageRecord):Promise<void>{
    const db=await getDatabase();
    await db.runAsync(
        `INSERT INTO messages (id,peer_id,sender,content,timestamp,status)
        VALUES(?,?,?,?,?,?);`,
        [msg.id,msg.peer_id,msg.sender,msg.content,msg.timestamp,msg.status]
    );
}
/**
 * Retrieves all stored messages for a specific conversation peer,
 * ordered with newest messages first for inverted FlatList feeds.
 */
export async function getMessagesForPeer(peerId:string):Promise<MessageRecord[]>{
    const db=await getDatabase();
    return await db.getAllAsync<MessageRecord>(
        `SELECT * FROM messages WHERE peer_id=? ORDER BY timestamp DESC
        `,[peerId]
    );

}
/**
 * Updates the delivery status tick for an existing message.
 */
export async function updateMessageStatus(
    id:string,
    status:"pending"|"relayed"|"delivered"
):Promise<void>{
    const db=await getDatabase();
    await db.runAsync(
        `
        UPDATE messages SET status =? WHERE id=?;`,
        [status,id]
    );
}




