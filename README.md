# MLDPTerminal_OTA_DFU
## :warning: **USE THIS APPLICATION AT YOUR OWN RISKS** :warning:
I won't be responsible for any module ending up bricked by this application.  
The code is here to show you how a DFU can be done using MLDP via an Android phone.

## Code related
Huge part of this code isn't mine. This is a tweaked version of the application [MLDPTerminal](http://ww1.microchip.com/downloads/en/DeviceDoc/MLDPTerminal8.apk) provided by Microchip.
[Source code here](http://ww1.microchip.com/downloads/en/DeviceDoc/MLDPTerminal%20v3.2-AndroidStudio.zip).

~~I am currently planning to create an app that will allow to do MLDP communication, remote command and DFU. I may also add other tools to it depending on my free time.~~  
This is not planned anymore. We received a message from Microchip in which they advise us not to use OTA in order to do a DFU.  
I know I won't have neither the time and material to develop and test such an application.  
If you want, you can fork this code or take inspiration from it but remember that most of the work is done by Microchip themselves.

## Instruction
**Performing 1.33BEC DFU:**  
1. Configure the RN4020 (at least active MLDP and OTA features and reboot)
2. Choose the device on which you want to perform the DFU in the scan list
3. When connected, switch the OTA button
4. If `OTA` is received in the Incoming text you can click "Send DFU" otherwise, the OTA signal could not be send (either your RN4020 isn't properly configure or you lost connection)
5. ​Keep the device awake during the transfer and **DO NOT EXIT** the application (:warning: Again, you may **brick** the module if something goes wrong :warning:)
6. If you receive `Upgrade OK` you should see the RN4020 reboot. The firmware 1.33BEC should be now installed and everything working.
   If you receive `Upgrade Err` **DO NOT DISCONNECT** the RN4020 from it power supply and do not reboot it. Instead, just disconnect the android phone from the RN4020 and connect to it again then go to step 3 until it works.

**Performing another DFU**:  
If you want to perform a custom DFU (1.23.5 for example), just place the .bin of your firmware in the assets folder and change the filename in the function `doInBackground` from class `sendDFUFile` in the MldpTerminalActivity.java source file.  
Then you'll just have to compile the app again and follow from step 1 the **Performing 1.33BEC DFU** guide above.
