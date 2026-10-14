// src/components/RadioAccessGate.tsx
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, PermissionsAndroid, Platform, Text, View } from 'react-native';

//  Props interface defining what this component accepts
interface RadioAccessGateProps {
    children: React.ReactNode;
}

export default function RadioAccessGate({ children }: RadioAccessGateProps) {
    //  useState to track if we have permission yet
    const [isGranted, setIsGranted] = useState(false);

    //  useEffect to trigger the permission check when the app loads
    useEffect(() => {
        setIsGranted(true);//here it shouldn't be but wrote it only foor testing now 
        //as  expo go on mobile was imposing errors so it is temporary 
        //temporary line:setIsGranted(true);
        requestPermissions();
    }, []); // Empty array means "run only once when component mounts"

    const requestPermissions = async () => {
        if (Platform.OS === 'android') {
            try {
                /*PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN is just a string. It
                 is a label pointing to the specific Android system permission
                  android.permission.BLUETOOTH_SCAN. It does not ask the user anything by itself.
                
                PermissionsAndroid.requestMultiple([...]) is the active function. It takes an 
                array of those permission strings and actually sends the request to the Android OS.
                //Requesting multiple permissions from the Android OS
                */
                const permissionsToRequest = [
                    PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
                    PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
                    PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE,
                    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
                ];

                const granted = await PermissionsAndroid.requestMultiple(permissionsToRequest);

                // Checking if all required permissions were granted
                /*When the user clicks "Allow" or "Deny" on the Android
                 popup, requestMultiple returns a string representing their
                  choice (e.g., "granted", "denied", or "never_ask_again").
                  PermissionsAndroid.RESULTS.GRANTED is simply the official 
                  constant for the string "granted". Your code checks if the 
                  response you got back matches this constant to ensure the user
                   actually pressed "Allow".
                  */
                const allGranted =
                    granted[PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN] === PermissionsAndroid.RESULTS.GRANTED &&
                    granted[PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT] === PermissionsAndroid.RESULTS.GRANTED &&
                    granted[PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION] === PermissionsAndroid.RESULTS.GRANTED;

                if (allGranted) {
                    setIsGranted(true);
                } else {
                    console.warn("Android: Bluetooth permissions denied");
                    setIsGranted(true);
                }
            } catch (err) {
                console.warn("Android Permission Error:", err);
                setIsGranted(true);
            }
        } else if (Platform.OS === 'ios') {
            // iOS automatically prompts for Bluetooth permissions when the BLE manager initializes in your mesh layer.
            setIsGranted(true);
        }
    };

    // State 1 & 2: Still asking for permissions or denied (Show a loading spinner or message)
    /*<View>: The fundamental building block for layout in React Native. It is exactly like a 
    <div> in HTML. You use it to group other elements together and style them (like adding padding
     or centering content).
    
    <Text>: Used to display any written text on the screen. It is like a <p> or <span> in HTML.
     In React Native, you cannot just type raw text inside a <View>; it must be wrapped in a <Text> 
     component, or your app will crash.
    
    <ActivityIndicator>: This is React Native's built-in loading spinner. It automatically renders
     as the standard spinning circle for the platform (a blue/grey spinner on Android, and a grey dashed spinner on iOS).
     */
    if (!isGranted) {
        return (
            <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
                <ActivityIndicator size="large" />
                <Text>Requesting Bluetooth Permissions to connect off-grid...</Text>
            </View>
        );
    }
    return <>{children}</>;
}
