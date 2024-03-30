In order to run DavMail on OSX Mountain Lion, you will need to disable Gatekeeper temporarily.

Steps:
- Open System Preferences/Security and Privacy and choose to allow applications download from anywhere.
- Uncompress DavMail.app.zip (Double click in Finder)
- Launch DavMail application once
- Back to System Preferences/Security and Privacy, restore original setting
- You may want to move DavMail to your Applications folder


O365Interactive requires OpenJFX, available in latest Zulu JRE FX.
Use the following steps to run DavMail with embedded jre:

- Grab latest Zulu JDK with OpenjFX:
https://www.azul.com/downloads/?version=java-21-lts&os=macos&architecture=arm-64-bit&package=jre-fx#zulu
- Unzip inside app package:
Contents/Frameworks/zulu-jre-fx/Contents/Home
- Uncomment LSEnvironment line in Info.plist:
<key>LSEnvironment</key>
<dict>
    <key>JAVA_HOME</key>
    <string>Contents/Frameworks/zulu-jre-fx/Contents/Home</string>
<dict>


