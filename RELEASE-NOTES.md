## DavMail 6.5.1 2025-10-29
Bugfix release to adjust packaging after 6.5.0 major changes, detect Flatpak and adjust settings and log
location accordingly.

### Enhancements
- Caldav: cleanup from audit
- Suppress warning on Subject.doAs in DavMailNTLMScheme
- GUI: switch to GitHub as recommended issue tracker about dialog
- GUI: Update link to https in about dialog
- Settings fix from audit
- Fix https://github.com/mguessan/davmail/issues/387 suppress warning on Subject.doAs

### Documentation
- Doc: update spec file changes, add komac winget command to release guide
- Doc: revert replace on GitHub url
- Doc: add new Fedora KDE Plasma screenshots
- Doc: drop 32 bits package from site documentation

### Build
- Build: exclude jacoco and sonarqube jars from dist/lib
- Build: set discardError on git svn check
- Build: change back to java 11 bytecode level for recent java jdks
- Build: fix missing ini files in windows installer

### Flatpak
- Adjust background color
- Icon without shadow
- Flatpak: change summary according to Flathub rules
- Flatpak: update README.md
- Flatpak: Simplify isFlatpak
- Flatpak: Create an ant target for flatpak release
- Flatpak: Fix launchable and add additional screenshots to appstream file
- Flatpak: adjust settings for flatpak containers

### SWT
- SWT: Remove dependency to SWT in O365InteractiveAuthenticator
- SWT: Refactor O365InteractiveAuthenticator to switch to OpenJFX in case of SWT error


## DavMail 6.5.0 2025-10-23
Release focused on build and distribution improvements, merged and refactored Docker configuration with automatic 
image build based on Github Actions and docker compose samples. For Linux cleaned up the RPM spec file and 
added sysusers configuration for Fedora 43.
Also dropped obsolete 32 bits packages on windows and embedded recent JDK version inside remaining packages.
In addition a brand new O365 interactive authentication implementation based on SWT and Webview2 is available with
native windows authentication support, which means FIDO2 and Windows Hello authentication are now supported. On Linux
SWT embedded browser is based on Webkit.

### Linux
- Linux: RPM spec switch group to Productivity/Networking/Email/Utilities, remove fedora lua dependency, fix java dependency
- Linux: RPM spec remove old el6 code, review java dependency, let ant compile with current jdk version, do not package logrotate configuration on systemd based systems
- Linux: RPM spec merge https://github.com/mguessan/davmail/pull/421 Use sysusers.d for Fedora
- Linux: review launcher
- Linux: merge appstream patch to match Flatpak packaging rules
- Linux: Compute SWT_CLASSPATH in various cases
- Linux: fix regression after spec file refactoring, see https://github.com/mguessan/davmail/issues/420

### Docker
- Docker: fix missing newline at end of file
- Docker: change isDocker check, cgroup is not reliable just check /.dockerenv
- Docker: use IMAGE_LABEL as target image
- Docker: avoid isDocker duplicate log statements
- Docker: entrypoint cleanup
- Docker: update Makefile to match latest changes, encode $ as required
- Docker: Reference docker images on main README page
- Docker: review and update documentation to match latest changes
- Docker: review makefile to match new Dockerfile
- Docker: set DAVMAIL_PROPERTIES in entrypoint and copy template if not exists
- Docker: fix classpath
- Docker: default option is notray
- Docker: review classpath, separate openjfx modules configuration, introduce DAVMAIL_PROPERTIES env variable
- Docker: create template davmail.properties file in /etc, remove servlet dependency
- Docker: create DAVMAIL_PROPERTIES environment to define settings file path in Docker
- Docker: log to console only when running in docker container
- Docker: need git-svn to build and remove swt package from runtime (use OpenJFX instead)
- Docker: OpenJFX is required to build O365InteractiveAuthenticatorFrame, make sure entrypoint is executable
- Docker: call ant to build jar only, adjust entrypoint.sh location
- Docker: initiate merge of https://github.com/mguessan/davmail/pull/409

### Enhancements
- Merge https://github.com/mguessan/davmail/pull/410: Add Russian translation
- Make O365Interactive the default mode on first start
- Adjust default settings template
- Move SWT and JFX available checks to Settings
- Add missing translation messages
- Change user agent on getReleasedVersion as sourceforge blocks default user agent

### Build
- Build: fix jackrabbit dependencies in maven pom
- Build: remove ini configuration from winrun4j wrappers (no need to update exe for next lib upgrade)
- Build: update debian package dependencies
- Build: switch openjfx to 19 in appveyor
- Build: appveyor copy openjfx libs with JDK 19
- Build: appveyor download openjfx
- Build: drop noinstall package in favor of the standalone windows package
- Build: fix task name in github release workflow
- Build: release github workflow fix condition
- Build: release github workflow fix name
- Build: merge release github workflow from https://github.com/mguessan/davmail/pull/409
- Build: set github docker workflow file parameter to ./src/docker/Dockerfile
- Build: create Github workflow to build and push the unstable docker image
- Build: move init depends to compile target
- Settings import cleanup and fix isJFXAvailable
- Build: add is.debian compile target to build on debian with openjfx (including docker)
- Build: include .ini in windows win4j wrapper and refactor java version detection to match more recent JDK versions
- Build: Revert SWT to version 4.20 to build with JDK 8
- Build: update winrun4J wrappers with SWT and include jar in installer
- Build: Drop windows 32 bits packages
- Build: fix uninstaller to properly remove jre
- Build: Embed Zuu JRE inside NSI installer, update DavMail url
- Build: Merge https://github.com/mguessan/davmail/pull/417 Fixed 'java.awt.AWTError: Assistive Technology not found: com.sun.java.accessibility.AccessBridge' in Windows standalone distribution

### Graph
- Graph: cleanup builder
- Graph: handle date based conditions and exdate cancelled occurrences
- Graph: set graphid for dtstart and dtend
- Graph: move encodeFolderName/decodeFolderName to StringUtil
- Graph: refactor setFilter to pass condition directly
- Graph: cleanup from audit
- Graph: introduce davmail.oauth.scope setting to override default scopes
 
### Documentation
- Doc: review README.md
- Doc: remove reference to 32 bits package in README.md

### SWT
- SWT: SWT browser not available under docker, failover to JavaFX
- SWT: catch errors in O365InteractiveAuthenticatorSWT
- SWT: Remove older SWT workarounds with GTK and cleanup code
- SWT: switch back on linux to SWT 4.20 to build with older JDK
- SWT: implement davmail.trayGrayscale for SWT
- SWT: Review O365InteractiveAuthenticatorSWT to properly dispose browser window in all cases
- SWT: fix regression on tray icon with latest SWT 4.37 on windows
- SWT: introduce davmail.oauth.allowSingleSignOnUsingOSPrimaryAccount property to enable SSO with windows Webview2 embedded browser implementation
- SWT: upgrade to 4.37 and add windows SWT jar
- SWT: Scale window icon size to 32
- SWT: switch to 128 pixel icons and improve loadSwtImage to scale to 32 pixels tray
- SWT: allow SWT tray on windows
- SWT: reimplement O365InteractiveAuthenticatorFrame using SWT embedded browser instead of OpenJFX, refactor SwtGatewayTray to separate tray init from thread init

### O365
- O365: set davmail.webview.debug property to dump document in O35InteractiveAuthenticatorFrame
- O365: by default do not send notifications on modified occurrences updates, only send when user is organizer of meeting
- O365: Improve password expiration detection
- O365: fix regression in O365 interactive authentication

### NTLM
- NTLM: Restore davmail.enableJcifs=true as default value

### GUI
- GUI: switch to ColorConvertOp for davmail.trayGrayscale to keep alpha information
- GUI: implement davmail.trayGrayscale to convert tray icon to grayscale

### Caldav
- Fix ICSCalendarValidator, CR, LF and TAB are allowed in full icalendar event


## DavMail 6.4.0 2025-08-31
Includes an experimental Microsoft Graph backend, to enable it see instructions at:
https://github.com/mguessan/davmail/issues/404
Fixed a long-standing IMAP issue on shared mailbox synchronization, improved GCC high tenants support, reviewed
OIDC authentication to prepare graph backend implementation, merged contributions from users, replaced log4j with reload4j.
Restored NTLM JCIFS implementation with davmail.enableJcifs setting based on user feedback, tried to improve NTML logging.

### Linux
- Linux: drop eclipse-swt dependency on Suse, rely on included swt package
- Linux: merge https://github.com/flathub/org.davmail.DavMail/blob/master/davmail-desktop-add-wm-class.patch
- Linux: additional rpm spec file fixes for rhel 9
- Linux: ant-unbound only exist in Fedora Rawhide

### O365
- O365: Handle password expiration and return a specifig error message
- O365: add davmail.tld setting to UI, see https://github.com/mguessan/davmail/issues/284
- O365: authenticator fix regression
- O365: revert authenticator scope change
- O365: improve authenticator error handling
- O365: drop hostname based fingerprint, allow user to force salt fingerprint with davmail.oauth.fingerprint setting, see https://github.com/mguessan/davmail/issues/403
- O365: review O365StoredTokenAuthenticator to take into account separate tokenFilePath
- O365: log token scopes
- O365: Fix for https://github.com/mguessan/davmail/issues/403 Salt generation for refresh token encryption seems unstable (BadPaddingException)
- O365: refactor authentication to handle various cases with Graph API with new OIDC endpoint and the classic endpoint
- O365: cleanup O365Authenticator
- O365: fix token parameters in OIDC mode, resource is not relevant
- O365: catch NullPointerException on javafx test as reported by a user
- O365: refactor tokenUrl build logic
- O365: adjustments for live.com OIDC endpoints

### EWS
- EWS: cleanup from audit
- EWS: try to get a valid outlook.com live token

### IMAP
- IMAP: fix IMAP context folder issue, when using userid/username syntax mailbox context is missing on some IMAP commands
- IMAP: Add log statement in case of progress notifier error
- IMAP: merge https://github.com/mguessan/davmail/pull/406 Simpler and more conformant keepalive feature
- IMAP: fix audit alert Refactor this repetition that can lead to a stack overflow for large inputs.
- IMAP: merge https://github.com/mguessan/davmail/pull/392, add support for iOS 18 comma separated uids in range search
- IMAP: SPECIAL-USE test cases
- IMAP: fix SPECIAL-USE implementation when used as a list-extended selection option, see https://www.rfc-editor.org/rfc/rfc6154 5.2

### NTLM
- NTLM: dump NTLM message details using NTLMMessageDecoder
- NTLM: implement custom NTLM message decoder
- NTLM: add debug info in DavMailNTLMEngineImpl
- NTLM: without JCIFS workstation and domain are not sent with type1 message
- NTLM: Restore JCIFS
- NTLM: introduce davmail.enableJcifs setting to enable JCIFS NTLM engine implementation

### Enhancements
- StringUtilTest add test case on encoding
- Cleanup test case from audit
- Enhancement from audit: rename folder.count to messageCount
- ExchangeSession: cleanup from audit
- Fix https://github.com/mguessan/davmail/issues/400 WARN Invalid 'expires' attribute
- Cleanup irrevelant dependency on debian package
- Upgrade nsi scripts with reload4j 2.0.16
- Maven: switch slf4j dependency to reload4j
- Upgrade slf4j / switch to reload4j
- Upgrade slf4j-api to 2.0.16
- Switch slf4j to reload4j instead of log4j
- Fix typo
- Log4J: fix ant build file
- Log4j: remove show logs option as LF5 is no longer available with reload4j
- Log4j: switching to reload4j, in place replacement for log4j
- Upgrade javamail to 1.6.2
 
### Build
- Maven: force slf4j-api to 2.0.16
- AppVeyor: switch from sonar.login to sonar.token

### Caldav
- Caldav: handle quote escaping in parameters

### Docker
- Docker: merge https://github.com/mguessan/davmail/pull/390 Docker entrypoint script refinements

### Documentation
- Doc: point issue management to Github
- Doc: review appdata doc

### Graph
- Graph: handle cancelled occurrences on events to build EXDATE vCalendar properties
- Graph: make progress on event recurrence implementation, see https://learn.microsoft.com/en-us/graph/api/resources/patternedrecurrence
- Graph: initiate recurrence implementation
- Graph: fix draft handling, draft is set for mapi PR_MESSAGE_FLAGS property combined with read flag by IMAPConnection
- Graph: tasks don't have a changeKey field, retrieve etag value from @odata.etag
- Graph: switch back to custom scopes
- Graph: manage task items returned as part of default calendar requests
- Graph: map taskstatus field to status graphId
- Graph: handle item not found with HttpNotFoundException
- Graph: implement isMainCalendar
- Graph: debug ALTDESC for thunderbird, need to urlencode content
- Graph: implement etag search for events and contacts
- Graph: handle html body and attendees for caldav support
- Graph: move convertClassFromExchange and convertClassToExchange to ExchangeSession
- Graph: Checkpoint on event management, new tasks handling based on new todo endpoint and make progress on calendar events
- Graph: Move some reference tables from EwsExchangeSession to ExchangeSession to make them available in GraphExchangeSession
- Graph: Cleanup from audit
- Graph: Additional entry points for graphid value in ExtendedFieldURI
- Graph: implement optDateTimeTimeZone in GraphObject to convert DateTimeTimeZone json objects
- Graph: new TODO objects in graph require Tasks.ReadWrite OIDC scope
- Graph: implement specific date conversion convertCalendarDateToGraph for graph api
- Graph: implement more condition logic and retrieve VTIMEZONE from properties
- Graph: add a full dump of all timezone definitions as graph does not provide a way to retrieve events as VCALENDAR
- Graph: make progress on graph attributes handling, refactor GraphObject
- Graph: implement fileAs on contacts and fix bday handling
- Graph: handle empty boolean custom property, return null for empty email
- Graph: switch to .default for OIDC scopes, will help with outlook desktop tokens, detect 50128 error code using outlook desktop clientid with live.com account
- Graph: refactor GraphObject handling
- Graph: refactor GraphExchangeSession, cleanup code
- Graph: implement custom graphid on Field/ExtendedFieldURI
- Graph: refactor graph properties handling
- Graph: main contact management implementation, create and update contact, handle attached photo
- Graph: implement HttpPut calls and introduce GraphResponse as a wrapper around json response messages
- Graph: improve graphId compute logic, add namespaceGuid for DistinguishedPropertySetType.Address, introduce isNumber to avoid error on empty number fields
- Graph: minimal form submit for live.com authentication
- Graph: implement live.com authentication with simple username/password
- Graph: add additional scopes to send mail, manage contacts and access shared mailboxes
- Graph: Cleanup from audit
- Graph: replace method names with constants
- Graph: fix approximate message and implement message update (properties only)
- Graph: Handle special folders based on well known names
- Graph: implement contact folders management
- Graph: update TestExchangeSessionFolder unit tests for graph backend
- Graph: Implement calendar folder handling
- Graph: Improve logging and performance in GraphRequestBuilder
- Graph: Implement IOUtil.convertToBytes to convert JSON to byte array without creating a string
- Graph: Enhancements for audit
- Graph: Fix iterator, nextLink page can be empty
- Graph: enable GraphExchangeSession in ExchangeSessionFactory
- Graph: implement getMimeHeaders and MultiCondition
- Graph: initiate delete/copy/move message
- Graph: make progress on graph backend implementation
- Graph: complete properties mapping on message update
- Graph: some more work on message update logic (PATCH call)
- Graph: implement getGraphUrl to handle various graph api endpoints
- Graph: log message download progress, add mailbox attribute to graph message and initiate message flags management
- Graph: refactor draft message handling, apply workaround (delete/create) only for non draft messages
- Graph: review buildMessage logic to parse properties
- Graph: initiate implementation of message handling
- Graph: compute graphid for distinguishedProperty properties (PublicStrings, InternetHeaders,Common)
- Graph: initiate create message implementation, with workaround for draft flag
- Graph: switch to v2.0 OIDC endpoint for graph authentication, adjust token endpoint accordingly


## DavMail 6.3.0 2025-02-26
Merged some contributions provided by users on github, updated embedded jre to 21 for improved TLS 1.3 support,
applied documentation fixes, reviewed rpm build to provide el9 compatibility, implemented channel binding for NTLM
authentication. Also added a more recent Docker file in contribs, worked on GCC high tenants compatibility.
Microsoft Graph backend implementation is in progress but far from complete.

### Caldav
- Caldav: handle multiple categories on TODO items, see https://github.com/mguessan/davmail/issues/372
- Caldav: Merge https://github.com/mguessan/davmail/pull/386 validate and repair Exchange calendar characters

### LDAP
- LDAP: merge https://github.com/mguessan/davmail/pull/353, fix first certificate selection logic
- LDAP: merge https://github.com/mguessan/davmail/pull/353 Add support for retrieving user certificates

### IMAP
- IMAP: fix backslash in password quoted string by: Max-Julian Pogner <max-julian@pogner.at>, finalize merge, call new parsing method from nextToken and remove backslash handling in username
- IMAP: fix backslash in password quoted string by: Max-Julian Pogner <max-julian@pogner.at>, review and switch to ParseException for error handling and chars instead of bytes
- IMAP: merge unit test for patch fix backslash in password quoted string by: Max-Julian Pogner <max-julian@pogner.at>
- IMAP: initial merge of patch fix backslash in password quoted string by: Max-Julian Pogner <max-julian@pogner.at>
- IMAP: fix https://github.com/mguessan/davmail/issues/359, handle RFC822.TEXT

### O365
- O365: add GCC high example settings in default davmail.properties
- O365: https://github.com/mguessan/davmail/pull/380 Remove trailing slash from O365_LOGIN_URL
- O365: merge https://github.com/mguessan/davmail/pull/380 Missed one change
- O365: merge https://github.com/mguessan/davmail/pull/380 Enhance support for the davmail.outlookUrl property setting and fully support GCC-High endpoints
- O365: implement OneWaySMS MFA prompt, see https://github.com/mguessan/davmail/pull/134
- O365: get O365 Login Url from settings for non standard tenants and add additional error use case
- O365: detect live.com token. Note: may not be enough to make DavMail work with live.com accounts
- O365: dropping all workaroungs for OpenJFX bugs that should be fixed in recent versions

### EWS
- EWS: experimental, try to implement recurrence count with NumberOfOccurrences, see https://github.com/mguessan/davmail/issues/373
- EWS: make settings timezoneid higher priority than mailbox configuration, make encode/decode foldername methods public for graph API

### Graph
- Graph: introduce GRAPH_URL and davmail.loginUrl in Settings
- Graph: initiate search filter implementation
- Graph: implement folder recursive search
- Introduce folderClass
- Graph: Create folders unit tests
- Graph: fix folder retrieval by id
- Graph: initial folder retrieval implementation
- Graph: compute graph API property id from name and tag
- Graph: Empty Graph ExchangeSession implementation
- Graph: prepare graph request builder for graph implementation
- Graph: Experimental, introduce davmail.enableGraph property to obtain a token compatible with Microsoft Graph API

### Linux
- Linux: fix ant dependency for fedora
- Linux: davmail.spec to not try to link os provided swt.jar when using embedded one
- Linux: work on el9 compatibility for RPM build
- Linux: Embed davmail-user.conf in source package, see https://github.com/mguessan/davmail/issues/356
- Linux: Create davmail user/group per new conventions in RPM 4.19, merge changes from https://github.com/mguessan/davmail/issues/356
- Build: fix https://github.com/mguessan/davmail/issues/346 unable to run jlink on linux
- Linux: force shutdown with -token option
- Linux: Add OpenJFX case for Kubuntu

### Documentation
- Doc: update windows build documentation, see https://github.com/mguessan/davmail/issues/384
- Doc: merge https://github.com/mguessan/davmail/pull/174 fix docs typo in property davmail.enableKeepAlive
- Doc: improve documentation on how to run DavMail as a windows service
- Doc: update documentation for non default tenants (US, China)
- Doc: provide more information in initial consent for O365 authentication
- Doc: update FAQ on application registration in Entra ID (Azure AD)
- Doc: mention AppIndicator and KStatusNotifierItem Support in Fedora setup instructions

### Docker
- Docker: merge https://github.com/mguessan/davmail/pull/381 by SethRobertson Bug 201: Docker support for building and running

### NTLM
- NTLM: remove CIFS dependency
- NTLM: cleanup code from audit
- NTLM: use ThreadLocal to manage SecureRandom generators
- NTLM: switch to DavMailNTLMSchemeFactory to enable channel binding, see https://github.com/mguessan/davmail/issues/352
- NTLM: retrieve certificate from http context
- NTLM: Duplicate of NTLMScheme from HttpClient to implement channel binding
- NTLM: adjust NTLMEngineImpl to handle channel binding
- NTLM: Duplicate code from NTLMEngineImpl to implement channel binding

### Enhancements
- Fix from audit
- Drop web component
- Drop servlet-api.jar, no longer required
- Drop deprecated DavGatewayServletContextListener (we no longer provide an embedded war file)
- Trying to fix custom jre for TLS 1.3, see https://stackoverflow.com/questions/63964939/tls-1-3-handshake-failure-when-using-openjdk-14-java-module-runtime and https://github.com/mguessan/davmail/pull/388
- fix download-jre for embedded JDK on windows, fetch version 21
- switch davmail linux launcher to zulu JDK 21 for better TLS 1.3 support
- switch main package to zulu JDK 21 for better TLS 1.3 support
- StringUtilTest CRLF => LF, see https://github.com/mguessan/davmail/pull/378
- StringUtil CRLF => LF, see https://github.com/mguessan/davmail/pull/378
- Security: Enable TLSv1.3 between DavMail and Exchange/O365
- PKCS11: review SunPKCS11ProviderHandler
- HttpClient: cleanup from audit
- svn ignore .idea
- Downgrade jackrabbit to 2.20.15 for java 8 compatibility
- Upgrade jackrabbit to 2.21.25 and httpclient to 4.5.14


## DavMail 6.2.2 2024-03-30
Includes some build process fixes, use jlink to build a customized jre for standalone package,
Thunderbird configuration documentation updated with new screenshots.
Also implemented a new -token command line option to launch O365InteractiveAuthenticator and get an authentication token.

### OSX
- OSX: document how to embed Zulu jre inside DavMail app

### Enhancements
- Fix comment in DavMail shell
- Detect missing network connectivity during token refresh to avoid invalidating token
- Upgrade JCIFS to 1.3.19 for NTLM support
- Cleanup from audit
- Drop war package from release files, there are more modern ways to run DavMail in server mode without a webapp container
- From audit: place LOCK.wait in a loop
- Fix from audit, use try with resource
- Cleanup from audit, remove deprecated getSubjectDN, getIssuerDN

### O365
- O365: Improve error message on missing add-exports, see https://github.com/mguessan/davmail/issues/344
- O365: Refactor manual authentication dialog to clearly separate steps
- Remove explicit dependency on O365InteractiveAuthenticator for -token option
- Implement a -token option to launch interactive authenticator and print obtained refresh token, see https://github.com/mguessan/davmail/issues/338
- O365: Try to adjust ItemId to 140 length, see https://github.com/mguessan/davmail/issues/328
- O365: prepare GCC-High/DoD endpoints support, fix regression
- O365: prepare GCC-High/DoD endpoints support, move outlook login url references to Settings
- O365: prepare GCC-High/DoD endpoints support, move outlook url references to Settings

### EWS
- EWS: Properly set errorDetail on xml stream error to raise exception
- EWS: disable IS_SUPPORTING_EXTERNAL_ENTITIES to fix xml parsing security warning

### Caldav
- Fix https://github.com/mguessan/davmail/issues/342 map America/Winnipeg to Central Standard Time
- Caldav: Fix https://github.com/mguessan/davmail/issues/337 take timezone into account when looking for excluded occurrence
- Fix settings implementation, # are allowed in values, see https://github.com/mguessan/davmail/issues/326

### IMAP
- IMAP: fix https://github.com/mguessan/davmail/issues/339 classcast exception on malformed content

### Build
- Maven: update compiler to 3.10.1
- Maven: optimize dependencies
- Maven: exclure httpclient 3 transitive dependency
- Appveyor: build only master branch
- Build: use jlink to create a custom jre to reduce embedded jre size

### Security
- Security: fix security alert on Runtime.getRuntime().exec
- Security: fix security alert on Runtime.getRuntime().exec

### Linux
- Linux: force GTK2 only with SWT
- Linux: switch to Azul JRE 17 with "davmail azul" command
- Linux: Enable OpenJFX on Fedora to make O365Interactive work with Java 17 and later

### Documentation
- Doc: update roadmap
- Doc: Add donation buttons to Github main readme page
- Doc: update Thunderbird documentation messages
- Update release guide, mvn install file no longer required
- Doc: update Thunderbird documentation, as requested in https://github.com/mguessan/davmail/issues/294
- Doc: update documentation for Thunderbird configuration
- Doc: use COPR badge
- Doc: update release guide
- Doc: reference OpenSuse build service and Fedora COPR for RPM based linux distributions
- Update changes file for https://build.opensuse.org/package/show/home:mguessan:davmail/davmail


## DavMail 6.2.1 2024-01-04
Most changes are related to build process and rpm packaging for Fedora/RHEL, also includes a few bug fixes on IMAP

### Security
- Security: Upgrade commons codec to 1.15

### O365
- O365: cleanup from audit
- O365: no longer apply the disable integrity check workaround by default, fixed in openjfx
- O365: add a warning message on FIDO authentication triggered
- O365: allow refresh token persistence without provided password 
- O365: Merge https://github.com/mguessan/davmail/pull/236 Changed authentication link log level

### Linux
- Linux: refactor spec file for fedora, do not call old init service scripts and compile with openjfx
- Linux: Fix systemd condition for fedora in spec file
- Linux: Adjust JDK dependencies for RPM build

### Build
- AppVeyor: Cleanup
- AppVeyor: copy plugin to x86-unicode
- AppVeyor: switch to server 2019
- Appveyor: investigate java versions
- AppVeyor: set ANT_HOME value
- AppVeyor: update ant path
- AppVeyor: update ant download path
- Run Sonar scan under JDK19
- Switch Appveyor build to JDK 19
- Maven: fix urls from https://github.com/mguessan/davmail/pull/225 by Stefan Weil

### IMAP
- IMAP: merge patch from https://github.com/mguessan/davmail/pull/140, return folders including special use folders when query is %
- IMAP: implement fetch macro flags, see https://github.com/mguessan/davmail/issues/314
- IMAP: fix APPENDUID value order

### Enhancements
- Cleanup from audit
- Fix typo
- NTLM: improve logging of NTLM negotiation
- Update Winrun4J wrappers with commons codec 1.15


## DavMail 6.2.0 2023-05-11
Another bugfix release to merge user contributions from Github, refactor Linux build, upgrade dependencies and a few IMAP and Caldav fixes.

### Security
- Update htmlcleaner to 2.29 cf CVE-2023-34624

### Enhancements
- Drop Travis-ci, no longer working for opensource projects
- fix quotes from audit
- Remove space at end of line +0630=Myanmar Standard Time, see https://github.com/mguessan/davmail/issues/309
- Fix https://github.com/mguessan/davmail/issues/271, keep line order and comments on settings save
- Fix from audit
- Maven: convert pom.xml to UTF-8

### IMAP
- IMAP: merge fix suggested on https://github.com/mguessan/davmail/issues/298
- IMAP: merge according to last comment on https://github.com/mguessan/davmail/pull/239
- IMAP: improve bodystructure handling, merge https://github.com/mguessan/davmail/pull/239
- IMAP: implement APPENDUID and advertise UIDPLUS
- IMAP: implement APPENDUID and advertise UIDPLUS
- IMAP: Prepare uidplus, fetch message after create

### Caldav
- Caldav: Fix nullpointer as in https://github.com/mguessan/davmail/issues/303
- Caldav: Fix https://github.com/mguessan/davmail/issues/309 Thunderbird daily sends TZOFFSETTO with optional second value
- Caldav: adjust isLightning test to include Thunderbird user agent, see https://github.com/mguessan/davmail/issues/287

### Linux
- Linux: based on https://github.com/mguessan/davmail/pull/290 by Björn Bidar <bjorn.bidar@thaodan.de>, remove svn commit reference in davmail.spec
- Remove svn tag from folder name inside source packages
- Linux: remove chkconfig from Requires preun and post in davmail.spec as suggested on build.opensuse.org
- Linux: dos2unix davmail.changes
- Linux: update changelog with Björn Bidar <bjorn.bidar@thaodan.de> contribution from https://github.com/mguessan/davmail/pull/289
- Linux: Add back changelog file and convert to OpenSUSE format from https://github.com/mguessan/davmail/pull/289
- Linux: Merge changes from https://github.com/mguessan/davmail/pull/290 except %version that may impact release cycle
- Linux: convert davmail.spec changelog to OpenSUSE format, see https://github.com/mguessan/davmail/pull/290
- Linux: restore spec file with lf and changelog, see https://github.com/mguessan/davmail/pull/289
- Linux: remove downloaded azul jdk package

### ADFS
- ADFS: merge https://github.com/mguessan/davmail/pull/270 by Felix Engelmann, enable ADFS authentication with SAML assertions

### EWS
- EWS: Cleanup from audit
- EWS: fix https://github.com/mguessan/davmail/issues/299 avoid logging large response message content
- EWS: BackOffMilliseconds unit test
- EWS: parse BackOffMilliseconds value on throttling

### O365
- O365: in O365 interactive, use invokeAndWait on failover to manual to avoid multiple instances of popup window
- Upgrade jettison to 1.5.4 in pom.xml
- Upgrade jettison to 1.5.4

### Windows
- Windows: drop explicit reference to sun.security.mscapi.SunMSCAPI, Windows-MY should be available on all windows JDK

### Documentation
- Doc: Update server properties documentation with davmail.oauth.persistToken
- Doc: fix link to SonarCloud


## DavMail 6.1.0 2023-03-19
First release in a long time to publish pending changes, including Kerberos support fix, 
experimental number matching support for upcoming Microsoft authenticator default configuration change,
store new refresh token received at authentication.
In addition, some Log4J specific patches to remove vulnerable classes from library
(even if DavMail is not impacted with standard configuration)

### O365
- O365: implement number matching logic in O365Authenticator
- O365: retrieve number matching value during phone app MFA
- O365: adjust logging level on O365 refresh token error
- O365: change persist token logic to store new refresh token after succesful refresh
- Experimental: store refreshed token when davmail.storeRefreshedToken=true

### Windows
- Windows: Add missing labelReplace active wait with wait/notifyAll in DavService
- Update winrun4j wrappers after lib upgrades

### Linux
- Linux: block davmail azul when davmail script is located under /usr/bin (package installed)
- Linux: fix PosixFilePermissions for writeable only by user
- Linux: try to make .davmail.properties file readable by user only on create
- Linux: improve launch script to take into account script location


### Enhancements
- Add missing label
- Replace active wait with wait/notifyAll
- Upgrade woodstox to 1.4.0 and jettison to 1.5.3
- Merge https://github.com/mguessan/davmail/pull/225 remove (most) http:// links in Maven POM
- Make ScheduledThreadPool thread daemon
- Switch to https for version check
- DavMailIdleConnectionEvictor fix from audit
- Refactor DavMailIdleConnectionEvictor to be less agressive on idle connection checks/purge and use scheduler instead of active polling
- Override SPNegoScheme to take into account DavMail kerberos settings

### Documentation
- Sample syslog configuration
- Update current version in README.md
- OSX: remove reference to Growl in documentation
- Doc: security note on DavMail not vulnerable to CVE-2021-44228
- Doc: add a section on Fedora installation from copr

### Caldav
- Caldav: Do not try to update modified occurrences on Mozilla thunderbird dismiss event

### Security
- Security: Strip packaged log4j jar from JMSSink and JDBCAppender to ensure DavMail is not vulnerable to CVE-2022-23305 & CVE-2022-23302, see https://github.com/mguessan/davmail/issues/250
- Remove JMSAppender, SMTPAppender and SocketServer from Log4J binary as an additional security measure (CVE-2019-17571 CVE-2021-4104), even if DavMail is not vulnerable with standard configuration
- Upgrade Log4J to latest version 1.2.17 in Winrun4J wrappers
- Upgrade Log4J to latest version 1.2.17


## DavMail 6.0.1 2021-12-03
Bugfix release with a few Office 365 enhancements

### O365
- O365: default to MSCAPI on windows for native client certificate access
- O365: name button Send instead of OK in manual authenticator dialog
- OS65: merge https://github.com/mguessan/davmail/pull/158 Added Copy to Clipboard button Manual auth dialog

### Linux
- Linux: improve "davmail azul" error handling
- Linux: Update changelog in RPM spec
- Linux: Fix RPM spec, remote source does not work
- Linux: Merge RPM spec contribution from michals on https://build.opensuse.org, check for systemd support and deploy the right service

### HC4
- HC4: make DavMail Kerberos configuration provider

### EWS
- EWS: merge https://github.com/mguessan/davmail/pull/106 Support servers only offering EWS/Services.wsdl instead of EWS/Exchange.asmx

### Documentation
- Doc: Remove HttpClient migration warning
- Doc: instructions on standalone setup on Linux for best O365 support
- Update readme for Github home page


## DavMail 6.0.0 2021-07-05
First major release in a long time, main change is switch from HttpClient 3 to 4, please report any regression related to this major rewrite.
DavMail now supports more O365 configurations, including access to client certificate to validate device trust.
O365 refresh tokens can now be stored securely in a separate (writable) file.
On Linux, in order to ensure the right java version is used, a command line option to download latest Azul JRE with OpenJFX support was added,
on windows a standalone package contains Azul JRE FX 15, on OSX updated universalJavaApplicationStub to latest version.

### OSX:
- OSX: completely drop Growl support
- OSX: prepare possible path for an embedded jre mode
- OSX: update universalJavaApplicationStub to latest version from https://github.com/tofi86/universalJavaApplicationStub/blob/master/src/universalJavaApplicationStub

### Documentation:
- Doc: merge Clarify the usage of imapIdleDelay https://github.com/mguessan/davmail/pull/116
- Doc: add comment on IDLE and timeout setting
- Doc: link to standalone windows package
- Doc: fix Zulu link
- Doc: remove references to Java 6 in documentation

### Build:
- Appveyor: update ant
- Appveyor: build with jdk15
- Appveyor: purge artifacts for all builds except jdk 8
- Build: run Sonar with JDK 11
- Update junit to 4.13.1 in Maven
- Update junit to 4.13.1

### Linux:
- Linux: Experimental: download Azul JRE FX with command 'davmail azul'
- Linux: merge https://github.com/mguessan/davmail/pull/133 Linux Gnome Desktop: fix systray support
- Linux: Update service file to allow 0-1023 ports binding (https://github.com/mguessan/davmail/pull/117)


### Windows:
- Windows: switch standalone jre to Azul FX 15
- Windows: create a standalone package with Azul JRE FX in order to have a working O365InteractiveAuthenticator
- Winrun4J: prefer embedded VM for standalone package and export sun.net.www.protocol.https
- Winrun4J: update binaries
- Winrun4J: prepare standalone configuration
- Windows: update winrun4j config to require Java >= 8

### IMAP:
- IMAP: fix thread handling from audit
- IMAP: Compute body part size with failover

### O365:
- O365: log token file creation
- O365: cleanup from audit
- O365: Add davmail.oauth.tokenFilePath to sample properties file
- O365: disable HTTP/2 loader on Java 14 and later to enable custom socket factory
- O365: allow user agent override in O365InteractiveAuthenticator, switch default user agent to latest Edge
- O365: with Java 15 url with code returns as CANCELLED
- O365: MSCAPI and Java version 13 or higher required to access TPM protected client certificate on Windows
- O365: merge first commit from https://github.com/mguessan/davmail/pull/134/ OAuth via ADFS with MFA support
- O365: fix store refreshToken call
- O365: introduce davmail.oauth.tokenFilePath setting to store Oauth tokens in a separate file
- O365: switch to try with resource style
- Drop explicit dependency to netscape.javascript package in O365InteractiveJSLogger
- O365: follow redirects on ADFS authentication

### HC4:
- Refactor ExchangeSessionFactory, create HttpClientAdapter in session
- HC4: update winrun4j binaries
- HC4: drop HttpClient 3 dependency in Maven, winrun4j binaries and nsi setup
- HC4: drop remaining HttpClient 3 classes
- HC4: drop DavMailCookieSpec and DavGatewaySSLProtocolSocketFactory (merge in SSLProtocolSocketFactory)
- HC4: drop DavGatewayHttpClientFacade and RestMethod
- HC4: default to Edge user agent
- HC4: Do not enable NTLM in Kerberos mode
- HC4: switch checkConfig to HttpClient 4
- HC4: merge HC4DavExchangeSession to DavExchangeSession
- HC4: cleanup HC4ExchangeFormAuthenticator references
- HC4: merge HC4ExchangeFormAuthenticator to ExchangeFormAuthenticator, extend authenticator interface to return HttpClientAdapter, switch to DavExchangeSession
- HC4: switch O365 authenticators test code to HttpClient 4
- HC4: adjust CreateItemMethod chunked call
- HC4: switch ExchangeSessionFactory to HttpClient 4
- HC4: add a warning about HttpClient 4 migration
- HC4: Enable ssl logging in addition to wire with HttpClient 4
- HC4: switch EWS implementation to HttpClient 4

### EWS:
- EWS: improve isItemId detection to match base 64 encoded id
- EWS: drop NTLM as a failover logic
- EWS: cleanup unused code now that we have a reliable way to retrieve email address with ConvertId
- EWS: drop property davmail.caldavRealUpdate, no longer used
- EWS: Improved uid handling from audit
- EWS: Enable Woodstox workaround for malformed xml with latest Woodstox version

### Enhancements:
- Clear session pool on DavMail restart
- Upgrade to Woodstox 6.2.0 as it's now available on debian, drop Woodstox patched StreamScanner

### Caldav:
- Caldav: merge https://github.com/mguessan/davmail/pull/139 Fix missing XML encode
- Caldav: use Exchange timezone to compute event time in test case
- Caldav: create test cases for recurring events


## DavMail 5.5.1 2019-04-19
Fix regression on domain\username authentication over IMAP and some cleanup

### IMAP:
- IMAP: fix https://github.com/mguessan/davmail/issues/100 regression with domain\username authentication

### O365:
- O365: set jdk.http.ntlm.transparentAuth=allHosts to enable NTLM for ADFS support

### Build:
- dist cleanup
- Update release guide with Fedora copr instructions
- Exclude .sonarwork from source package
- Build: fix release file name


## DavMail 5.5.0 2019-04-15
This release contains a lot of bugfixes, enhancements and some user contributions from Github.
Most activity is related to HttpClient 4 refactoring, this will probably be the latest version based in HttpClient 3.
Minimum Java version is now 8, also dropped Growl support on MacOS.
Notable changes for end users are Oauth token persistence to avoid frequent O365 authentications and OIDC support for
personal outlook accounts.
Known issues: some users reported regressions with form authentication, please report such regressions with log files.

### IMAP:
- IMAP: catch non IOException errors in FolderLoadThread
- IMAP: fix https://github.com/mguessan/davmail/pull/91 Allow user name to be specified as user@domain.com in addition to domain\user to access shared mailbox
- IMAP: unquote CHARSET in IMAP search
- IMAP: cleanup test cases

### Documentation:
- Doc: describe new Office 365 authentication modes
- Doc: remove deprecated enableEWS reference
- Doc: Document new connection modes in template davmail.properties
- Doc: Add all time downloads badge
- Doc: push sonarcloud badges

### Enhancements:
- Apply xml transformer settings recommended by Sonar
- dos2unix template davmail.properties file
- prepare migration to https://davmail.sourceforge.io
- Settings: cleanup from audit
- Latest edge user agent does not work, use Outlook 2013 instead
- Convert HTTP code to Java 8
- Cleanup from audit
- Fix sonar detected vulnerability
- Update to Java 8 and code cleanup
- Comment HttpURLConnectionWrapper
- Fix regression in AbstractServer
- Remove unused label
- Cleanup from audit at Java 8 language level

### Security:
- Enable TLSv1.1 and TLSv1.2

### HttpClient 4:
- HC4: httpClient is not shared between clients, do not track connection state to enable NTLM connection pooling
- HC4: set default connection per route to 5
- HC4: refactor Exchange 2007 test cases
- HC4: Prepare switch, create a temporary HC4WebDav mode
- HC4: ExchangeSession cleanup
- HC4: unit test cleanup
- HC4: make ExchangeSession independent of HttpClient implementation
- HC4: remove reference to old HttpStatus in HttpClientAdapter
- HC4: more HC4ExchangeFormAuthenticator refactoring and fixes
- HC4: test form authentication failure
- HC4: more DavExchangeSession refactoring
- HC4: more form authenticator fixes
- HC4: create ResponseWrapper interface for PostRequest and GetRequest
- HC4: new executeFollowRedirect implementation and get user agent from settings
- HC4: cleanup from audit and fix regression, need to follow redirect after OWA authentication
- HC4: more authentication test case
- HC4: Implement single thread connection evictor
- HC4: improve PostRequest and RestRequest
- HC4: implement executePostRequest with test case
- HC4: new GetRequest implementation with test case
- HC4: more request refactoring
- Refactor RestRequest to use ResponseHandler mode only
- Refactor TestHttpClientAdapter
- HC4: improve RestRequest
- HC4: remove old HttpException and move to HttpResponseException
- HC4: switch to HttpGet
- HC4: add buildHttpException to HttpClientAdapter
- HC4: cleanup from audit
- HC4: keep statusLine in request
- HC4: cleanup to finalize migration
- HC4: convert encodeAndFixUrl
- HC4: Remove old HttpStatus dependency
- Remove HttpException dependency
- HC4: convert Head and Post methods
- HC4: convert GetMethod to HttpGet
- HC4: convert remaining http client 3 methods
- HC4: Implement create/delete folder
- HC4: implement Exchange WebDav search request
- HC4: Fix initial uri in HttpClientAdapter
- HC4: convert internalGetFolder
- HC4: refactor TestCaldavHttpClient4
- HC4: more test cases on session creation
- HC4: convert getWellKnownFolders, first working version of session creation
- HC4: convert checkPublicFolder
- HC4: get httpClientAdapter from HC4ExchangeFormAuthenticator
- HC4: Main test case for HC4DavExchangeSession
- HC4: convert getEmailAndAliasFromOptions
- HC4: convert getMailpathFromWelcomePage
- HC4: baseline DavExchangeSession for HttpClient 4 migration
- HTTP: Improve HttpClientAdapter, enable kerberos support according to setting
- HttpClient4: improve HttpClientAdapter
- HttpClient4: Test timeouts with proxy
- Refactor TestHttpClient4 with try with resource

### Caldav:
- Caldav: fix https://github.com/mguessan/davmail/pull/88 EXDATE timezone issue

### O365:
- O365: fix https://github.com/mguessan/davmail/pull/92 failover for null query with non https URI
- O365: refactor O365Authenticator and fix regressions
- O365: switch to new executeFollowRedirect implementation
- O365: refactor O365Authenticator
- Refactor O365InteractiveJSLogger to work with more JDK versions

### Test:
- Test: improve notification dialog test
- Test: Improve client certificate test
- Improve getReleasedVersion test case
- Test: Improve base test cases
- Add new harmcrest-core junit dependency ant enable IMAP test cases
- Test: update junit to 4.12
- Run a SSL server socket
- Test: cleanup code
- Improve TestDavGateway
- Make AbstractDavMailTestCase abstract
- HttpClient: improve test cases

### Build:
- Fix file name for Appveyor trunk builds
- Fix git svn warning
- try to get svn revision from git
- Set jacoco path in sonar config
- fix test-compile language level
- Upload coverage report to sourceforge for AppVeyor
- Prepare Jacoco coverage report
- Cleanup unused ant check
- Exclude Sonar working directory from package
- Appveyor: try to run sonar from Appveyor build

### EWS:
- EWS: make getPageSize static
- EWS: merge PR Allow to configure EWS folder fetch page size https://github.com/mguessan/davmail/pull/79
- EWS: fix response handling
- EWS: fix O365Authenticator
- EWS: fix regression in O365Token
- EWS: handle malformed id_token
- EWS: refactor authenticators to use davmail.enableOidc
- EWS: create a new davmail.enableOidc setting tp switch to new v2.0 OIDC compliant endpoint https://docs.microsoft.com/en-us/azure/active-directory/develop/azure-ad-endpoint-comparison 
- EWS: Prepare OIDC support, add v2.0 url in interactive authenticator
- EWS: Prepare OIDC support, decode id_token
- EWS: enable davmail.oauth.persistToken by default

### DAV:
- DAV: MOVE returns SC_CONFLICT on missing target folder
- Dav: Update to Java 8

### SMTP:
- SMTP: improve error message handling
- SMTP: fix smtp test cases

### LDAP:
- LDAP: clean test case
- LDAP: fix dn authentication
- LDAP: Ber code cleanup from audit
- LDAP: update LdapConnection to Java 8
- LDAP: make parseIntWithTag protected to call it from LdapConnection
- Use imported Ber implementation instead of com.sun.jndi.ldap
- Import Ber implementation from OpenJDK to avoid compilation warnings

### OSX:
- OSX: drop Growl support 


## DavMail 5.4.0 2019-11-11
Main new feature is experimental support for stored Oauth tokens with davmail.oauth.persistToken=true,
tokens are stored encrypted with client provided password.
Also improved SPECIAL-USE IMAP support and fixed a few regressions related to ExchangeSessionFactory refactoring
and a lot of bug fixed from user feedback.

### Enhancements:
- Add sonar target to ant build
- Sonar configuration
- Add sonarqube-ant-task to lib
- Throw NoSuchElementException in message iterator for iteration beyond the end of the collection
- InterruptedException should not be ignored
- currentVersion is never null
- Make AbstractConnection abstract
- Update default user agent to latest version of Edge on Windows
- Add .gitignore file
- Update StringEncryptor to Java 8
- Update Maven and Ant build to Java 1.8
- Drop Java 7 in travis config
- Add {AES} prefix to encrypted strings
- Improve StringEncryptor compatibility with older jdks
- Ignore stream errors on disconnect, messages cleanup
- Testcase for password based string encryptor
- Implement password based string encryptor
- Refactor settings save to preserve comments
- Force Trusty in Travis config

### Appveyor:
- Appveyor: Update to ant 1.10.7
- Appveyor: test JDK 12 and 13 build

### Security:
- Security: secure XML transformer
- Security: Untrusted XML should be parsed without resolving external data

### SWT:
- SWT: Refactor the synchronisation mechanism to not use a Thread instance as a monitor

### LDAP:
- LDAP: Add a note to Thunderbird directory config on uid=username syntax

### IMAP:
- IMAP: implement RETURN (SPECIAL-USE) in IMAP list command, return special folders only, fix for https://sourceforge.net/p/davmail/bugs/721
- IMAP: allow recursive search on public folders

### Carddav:
- Carddav: iOS does not support VCard 4, detect its old Carddav client and send VCard 3 content, exclude unsupported distribution list items

### Caldav:
- Caldav: do not try to send cancel notifications on shared and public calendars

### EWS:
- EWS: allow O365Manual in headless mode
- EWS: implement command line mode for O365ManualAuthenticator, as suggested in https://github.com/mguessan/davmail/issues/48
- EWS: exchangecookie is not a good check of successful authentication
- EWS: detect direct EWS even if mode is different
- EWS: experimental, store Oauth refresh tokens in davmail.properties when davmail.oauth.persistToken=true
- EWS: fix /public and /archive folders access over EWS
- EWS: improve O365Authenticator error detection 
- EWS: fix access to /public folder
- EWS: Try to improve O365 authentication with ADFS tenants

### Documentation:
- Doc: fix trusterbird link on home page

### Linux:
- Linux: switch spec file to java-1.8.0
- Linux: prepare rhel8 support

### SMTP:
- SMTP: fix #720 Davmail returns 503 instead of 530 when trying to send mail before authentication

## DavMail 5.3.1 2019-08-12
Bugfix release to fix NTLM authentication for some Exchange on premise instances.
Also includes a new OSX handlers implementation required to support recent OSX JDKs.

### Enhancements:
- Reprocess credentials in addNTLM
- Use github download link instead of direct sourceforge link in About dialog
- Improve ExchangeFormAuthenticator logging

### EWS:
- EWS: fix possible bug with username with authenticatorClass
- EWS: add an Open button to O365ManualAuthenticatorDialog in case links are not working
- EWS: fix regression in OWA authentication mode, enable NTLM if required by EWS endpoint

### OSX:
- OSX: comment zulufx jre embed
- OSX: prepare zulufx jre embed
- OSX: drop old OSXAdapter
- OSX: cleanup unused methods
- OSX: no need to register QuitHandler, default is fine
- OSX: implement new Desktop handlers on Java 9 and later, keep compatibility with com.apple.eawt.Application

## DavMail 5.3.0 2019-08-06
Major update with a focus on O365 and MFA support, this release includes a new davmail.userWhiteList
setting to filter users by email or domain. We now have a more modern responsive site thanks to new Maven skin.
Migration to HttpClient 4 is in progress but not finished yet.

### Enhancements:
- Cleanup from audit
- Update Maven POM
- Implement a new davmail.userWhiteList setting to only allow limited users and/or domains, see https://github.com/mguessan/davmail/issues/47
value is a comma separated list of emails or domains (user@company.com or @company.com)
- Cleanup: remove duplicate code

### IMAP:
- IMAP: additional folder test case
- IMAP: Fix #714 StringIndexOutOfBoundsException with NOT UID condition
- IMAP: fix https://github.com/mguessan/davmail/issues/35, Result of of a mailbox search is different between search and uid_search
- IMAP: try to encode invalid character ( and ) in keywords
- IMAP: fix #708 issue, more generic patch when folder name starts with a special folder name
- IMAP: fix #708 issue with folder name that starts with Inbox
- IMAP: encode greater than character in folder name

### HTTP:
- Fix logger and remove old httpClient dependency in HttpClientAdapter
- HTTP: Full Http Client 4 form authentication module
- HTTP: experimental Http Client 4 authenticator
- HTTP: Implement execute with custom local context and manage cookies
- HTTP: cleanup from audit
- HTTP: remove form authentication code from ExchangeSession
- HTTP: Switch to new ExchangeFormAuthenticator
- HTTP: adjust RestRequest for HttpClient 4 Exchange DAV requests
- HTTP: implement HttpClient 4 Exchange DAV requests
- HTTP: prepare major refactoring, extract form authentication from ExchangeSession
- HTTP: migrate O365Token to HttpClient4
- HTTP: remove last dependencies to HttpClient3 in URIUtil
- HTTP: set logging levels for HttpClient 4
- HTTP: improve request implementation
- HTTP: move requests to new package
- HTTP: improve REST request
- HTTP: Accept String urls in GetRequest and PostRequest
- HTTP: switch to GetRequest in getReleasedVersion
- HTTP: Http Client 4 GET and POST request wrappers
- HTTP: a few more test cases
- HTTP: improve HttpClientAdapter interface
- HTTP: switch check released version to HttpClient 4
- HTTP: implement Get and Rest requests with HttpClient 4
- HTTP: reenable basic proxy authentication on Java >= 1.8.111 in HttpClientAdapter
- HTTP: reimplement URIUtil to prepare HttpClient 4 migration
- HTTP: Cleanup from audit
- HTTP: reenable basic proxy authentication on Java >= 1.8.111: jdk.http.auth.tunneling.disabledSchemes=""
- HTTP: Implement JCIFS NTLM authentication with HttpClient 4

### GUI:
- GUI: translate disableTrayActivitySwitch messages
- GUI: merge Add davmail.disableTrayActivitySwitch to disable tray icon activity, see https://github.com/mguessan/davmail/pull/28

### EWS:
- EWS: O365Manual add mode in Settings
- EWS: O365Manual enable in ExchangeSessionFactory
- EWS: O365Manual missing label
- EWS: add davmail.oauth.tenantId setting to GUI and documentation
- EWS: create a new davmail.oauth.tenantId setting to set actual company tenant
- EWS: additional cases for Microsoft account authentication
- EWS: refactor O365 interactive to always use an HttpURLConnectionWrapper
- EWS: Fix error handling in manual authentication failover
- EWS: fix NPE in manual authenticator
- EWS: do not force user agent in O365 interactive authenticator, breaks Microsoft login form browser detection
- EWS: improve Okta support in O365 interactive authenticator
- EWS: prepare tenant independent authenticator: do not hard code /common/
- EWS: always enable interactive authenticator in settings now that we have a failover without JavaFX
- EWS: i18n manual authentication messages
- EWS: Prepare a failover manual authenticator when OpenJFX is not available
- EWS: merge https://github.com/mguessan/davmail/pull/26, Added input names for form authentication
- EWS: do not call addNTLM in ExchangeSessionFactory to avoid kerberos configuration conflict
- EWS: fix regression to correctly detect network down
- EWS: fix regression, do not force user-agent in 0365 interactive authenticator
- EWS: cleanup from audit
- EWS: O36 authenticators cleanup from audit
- EWS: use ConvertId to retrieve current mailbox primary SMTP address, more reliable than ResolveNames
- EWS: migrate O365Authenticator to HttpClient 4
- EWS: remove duplicate code in O365 interactive authenticator
- EWS: improve interactive authenticator, adjust integrity workaround for Okta
- EWS: improve interactive authenticator, adjust integrity workaround and catch javascript errors
- EWS: Apply integrity disable workaround to Okta form second step
- EWS: use URIBuilder instead of URIUtil to build URI
- EWS: fix support for new Okta authentication form, need to disable integrity check
- EWS: drop old Autodiscover failover, need to implement before authentication instead
- EWS: log connection errors in O365InteractiveAuthenticator
- EWS: new AutoDiscoverMethod implementation
- EWS: improve O365 token logging

### Linux:
- Linux: adjust AWT tray icon for Linux Mint Cinnamon
- Linux: Merge patch, add JFX_CLASSPATH when SWT3 is available
- Linux: Fix spec file for copr

### Unix:
- Unix: failover to xdg-open on both Linux and Freebsd

### Documentation:
- Doc: fix title in page
- Doc: improved site skin with collapsible sidebar
- Doc: upgrade Maven Javadoc plugin
- Doc: Switch to modern responsive Maven fluido skin
- Doc: Switch to modern responsive Maven reflow skin

### DAV:
- DAV: cleanup from audit
- DAV: remove dependency to old URIException

### Caldav:
- Caldav: cleanup from audit
- Caldav: send 404 not found instead of 400 for unknown requests
- Caldav: Do not try to update event is X-MOZ-FAKED-MASTER is set
- Caldav: fix test case

### OSX:
- OSX: merge patch #54 Set NSSupportsAutomaticGraphicsSwitching to Yes to prevent macOS GPU access

### SWT:
- SWT: merge duplicate code

## DavMail 5.2.0 2019-02-10
Includes improved ADFS compatibility and support Okta authentication in interactive mode, 
a fix for Thunderbird dismiss issue, a few LDAP and IMAP enhancements (TRYCREATE support).
Also upgraded libraries to prepare HttpClient 4 upgrade.

### EWS:
- EWS: improve O365 mode handling, force url
- EWS: detect Okta authentication and explicitly fail in this case with O365Authenticator
- EWS: O365StoredTokenAuthenticator test case
- EWS: allow cross domain requests for Okta form support in O365Interactive mode
- EWS: try to fix #702, add login.srf to OpenJFX workaround
- EWS: Remove Jetbrains only annotation
- EWS: Fix O365 device login check
- EWS: cleanup from audit
- EWS: Another step in O365 device login
- EWS: detect devicelogin after O365 ADFS authentication, try to follow redirect
- EWS: adjust 0365Token log statement level

### Caldav:
- Caldav: explicitly detect Thunderbird dismiss/snooze events to update only mozilla custom properties
- Caldav: fix #705 daily recurrence issue
- Caldav: experimental, return created item URL in Location header
- Caldav: enable isorganizer field
- Caldav: adjust isOrganizer check (again) to work on all Exchange server versions
- Caldav: workaround for missing DTEND in event, avoid NullPointerException
- Caldav: fix recurrence options and implement interval over EWS
- Caldav: fix montly recurrence handling

### IMAP:
- IMAP: fix #704 implement [TRYCREATE] on folder not found
- IMAP: Fix FETCH RFC822 request for python imap client.
- IMAP: Additional fix for slash in folder name

### Carddav:
- Carddav: fix regression, override getAllContacts to list contacts and distribution lists

### LDAP:
- LDAP: cleanup from audit and support simple rdn authentication, see https://github.com/mguessan/davmail/pull/18
- LDAP: implement hassubordinates attribute
- LDAP: fix contact filter over EWS
- LDAP: fix dn authentication, fix https://github.com/mguessan/davmail/pull/18
- LDAP: improve dn authentication, fix https://github.com/mguessan/davmail/pull/18
- LDAP: implement dn authentication, see https://github.com/mguessan/davmail/pull/18
- LDAP: test case for dn authentication, see https://github.com/mguessan/davmail/pull/18
- LDAP: encode uid value in dn, see https://github.com/mguessan/davmail/pull/18
- LDAP: add mappings for HomePhone and Pager attributes

### Documentation:
- Doc: fix https://github.com/mguessan/davmail/pull/21
- Doc: Improve documentation of client connection timeout, merge https://github.com/mguessan/davmail/pull/20
- Doc: remove alt from OpenHub link
- Doc: merge documentation provided by Geert Stappers
- Doc: Update roadmap
- Doc: update README.md
- Doc: document Android configuration with a DavMail server
- Doc: prepare android setup instructions
- Doc: improve download link in README.md
- Doc: update Debian package description
- Doc: fix openhub link

### HTTP:
- HTTP: switch from URIUtil to URIBuilder in Caldav test cases
- HTTP: more Caldav test cases with HttpClientAdapter
- HTTP: implement caldav report and search test case with HttpClientAdapter
- HTTP: implement Dav request in HttpClient 4 adapter
- HTTP: improve HttpClient 4 adapter, detect relative url
- HTTP: implement HttpClientAdapter follow redirects and parse username
- HTTP: first HttpClientAdapter draft
- HTTP: include HttpClient 4
- Update commons-codec to 1.11 (prepare HttpClient 4 migration)
- HTTP: test HttpClient 4 connection pool management and expiration
- HTTP: More HttpClient 4 test cases for authentication, proxy, redirects and URI handling
- HTTP: Basic HttpClient 4 test cases
- Update jackrabbit to 2.14.6 (latest version to support httpclient 3.1

### Linux:
- Linux: adjust tray icon for XFCE and KDE
- Linux: try to adjust tray icon for XFCE
- Linux: fix swt jar exists test in launch script
- Linux: fix SWT version in POM and update description
- Linux: adjust systemd service to rhel/centos
- Add DavMail systemd service in spec file
- Linux: fix compatibility with older distributions
- SWT: Improve code to make it compatible with older SWT libraries in Ubuntu 18.
- Linux: fix spec file regression

### Maven:
- Maven: reenable site plugins in pom


## DavMail 5.1.0 2018-12-18
Much improved interactive O365 authentication with OpenJFX bug workaround, 
experimental stored Oauth refresh token support. More Linux distributions were tested
to make DavMail work with recent KDE and Gnome environments.

### EWS:
- EWS: more progress on ADFS authentication
- EWS: cleanup warning message
- EWS: experimental, implement davmail.oauth.persistToken to store Oauth refresh token
- EWS: make progress on O365 ADFS authentication, fix method
- EWS: stored token authentication, load token by username
- EWS: make progress on O365 ADFS authentication, enable NTLM and pass credentials
- EWS: O365 authentication, set resource url on token refresh
- EWS: set default access token expiration
- EWS: implement stored access token in addition to refresh token (will only last one hour)
- EWS: experimental, load Oauth refresh token from setting davmail.oauth.refreshToken
- EWS: fix https://github.com/mguessan/davmail/issues/15 empty domain in NTLM authentication
- EWS: revert to 4.9.0 behavior for EWS mode
- EWS: fix regression in token handling
- EWS: allow urn protocol in O365 authenticator
- EWS: fix regression when main authentication relies on OWA and/or ADFS
- EWS: check for errors in returned json token
- EWS: Fix warning message
- EWS: workaround for JavaFX bug, add one more URL
- EWS: call setAlwaysOnTop(true); on page load success
- EWS: in addition to requestFocus, call toFront
- EWS: workaround for JavaFX bug, handle more methods in connection wrapper
- EWS: workaround for JavaFX bug, handle post requests
- EWS: workaround for JavaFX bug, add additional microsoft url
- EWS: workaround for JavaFX bug, fix java 8 regression
- EWS: Add export compiler arg java.base/sun.net.www.protocol.https for webview bug workaround 
- EWS: improve interactive authenticator focus handling and remove reflection calls
- EWS: workaround for JavaFX bug, use reflection to avoid java 9 errors
- EWS: workaround for JavaFX bug, drop reference to internal sun class HttpsURLConnectionImpl
- EWS: workaround for JavaFX bug, disable integrity check on external resources in O365 authentication form
- EWS: javafx test can also trigger NoClassDefFoundError
- EWS: Rename JSLogger
- EWS: improve O365 interactive error handling
- EWS: override console.log to send error messages to Log4J
- EWS: More EWS test cases
- EWS: new authenticator test cases
- EWS: detect when user settings validation is required by Office 365
- EWS: detect manual window close event
- EWS: Make sure we close frame on timeout, improve error message
- EWS: refactor O365 authenticator to do all gui calls in Swing thread
- EWS: cleanup from audit
- EWS: encode slash inside folder names
- EWS: convert date without SimpleDateFormat during load messages to improve performance and reduce memory footprint
- EWS: Send authentication failed instead of generic error in case of username mismatch in O365Authenticator

### Documentation:
- Doc: update project description in README.md
- Doc: update project description
- Doc: adjust IntelliJ link according to JetBrains recommendation
- Doc: revert openhub change, was a target side issue
- Doc: add YourKit Java Profiler logo to home page
- Doc: improve IntelliJ IDEA home page logo
- Doc: fix swt gtk version in documentation
- Doc: fix openhub link
- Doc: add link to https://apps.dev.microsoft.com/
- Doc: direct link to latest release package download list in README.md
- Doc: fix link in server setup documentation

### OSX:
- OSX: upgrade universalJavaApplicationStub to 3.0.4

### Linux:
- Linux: prepare systemd service
- Linux: missing openjfx dependency
- Linux: set cross platform look and feel on Linux, except is swing.defaultlaf is set
- Linux: enable anti aliasing in GUI
- Linux: improve launch scripts to handle more cases (OpenJDK 11 with or without SWT)
- Linux: remove swt4 suggests and revert gtk force, does not work under debian sid
- Linux: Force gtk version no longer required with cross platform look and feel
- Linux: use hi res icon images in frame mode
- Linux: Add JavaFX classpath to launch script
- Linux: add libopenjfx-java dependency to debian package
- Linux: switch swt dependency to suggests

### Caldav:
- Caldav: another NullPointerException fix
- Caldav: fix #694 Null pointer exception writing days of week

### Enhancements:
- Avoid nullpointerexception on missing credentials
- Move isLinux method to Settings
- Revert back to Java 6 build in all cases
- Restore Java 6 compatibility
- Add utility methods in Settings
- Do not try SWT when O365 interactive mode is selected.

### Windows:
- Windows: update winrun4j 64 wrapper to support java > 8, see https://github.com/poidasmith/winrun4j/pull/81

### GUI:
- GUI: dispose notification dialog on close
- GUI: increase default frame size
- GUI: add hi res icon images
- GUI: use setLocationRelativeTo to set frame location

### SWT:
- SWT: O365Interactive is not compatible with SWT, do not try to create SWT tray
- SWT: call GDK.gdk_error_trap_push() to avoid crash
- SWT: Enable debug mode
- SWT: upgrade SWT to 4.9
- SWT: drop deprecated SWT 3 calls and adjust tray icon image to 22px

### Appveyor:
- Appveyor: build with JDK11
- Merge patch #51 Check for javafx in compile classpath


## DavMail 5.0.0 2018-11-21
Major release with Office 365 modern authentication (Oauth2) and MFA support.
DavMail now supports IMAP SPECIAL-USE RFC6154.
On the packaging side, RPM files are now included in source package and more 
distributions are supported by the spec file. An appveyor configuration is in place to
provide up to date trunk builds. Thanks to wberrier DavMail is now available as a
flatpack package, see https://flathub.org/apps/details/org.davmail.DavMail
This release also includes many bug fixes and enhancements, see below.

Known issues/limitations:
- Office 365 interactive authentication is based on OpenJFX (JavaFX),
which is available in Oracle JDK but not in OpenJDK. On windows use latest Oracle JDK (>=9),
on Linux OpenJDK 8 + JavaFX is the best option. This is obviously not available in server mode.
- Office 365 modern authentication does not have those constraints, however it will only work
with native Office 365 authentication, and not with ADFS.

### EWS
- EWS: catch errors in setURLStreamHandlerFactory
- EWS: custom proxy selector, do not return proxies for direct socket connections
- EWS: create a custom proxy selector to manage O365 interactive authentication proxy
- EWS: improve error handling in O365 interactive authenticator, do not implicitly close JavaFX thread
- EWS: cleanup O365 interactive
- EWS: Set http.nonProxyHosts from davmail.noProxyFor in O365 interactive authentication
- EWS: improve error handling in O365 interactive authentication
- EWS: implement proxy support in O365 interactive authentication
- EWS: username with @ is email
- Do not try form authentication with direct EWS
- EWS: Force dispose of interactive frame
- EWS: improve interactive authentication error handling
- Fix main test case to support new authentication modes
- EWS: Enable DavMail custom SSLSocketFactory in O365 interactive authentication 
- EWS: Add Oauth authentication section in DavMail settings interface
- EWS: Experimental ADFS authentication, not yet functional
- EWS: log page content on error in O365Authenticator
- EWS: register a stream handler for msauth protocol
- EWS: Allow clientId override in interactive authenticator
- EWS: Send authentication failed on phone MFA denied/no response
- EWS: enable progress bar on first page load only
- EWS: Office 365 unit test with loop
- EWS: make sure httpclient connections are closed, remove duplicated code
- EWS: use renewable token in EwsExchangeSession
- EWS: refactor O365 authentication to implement token refresh
- EWS: improve headless Office 365 authenticator error handling
- EWS: implement progress bar in interactive authentication frame
- EWS: check username in Office365 interactive authenticator
- EWS: Encode username in Office365 authenticator
- EWS: exclude JavaFX authenticator from Maven pom
- EWS: Remove reference to JavaFX authenticator in ExchangeSessionFactory
- EWS: Reorganise authenticators
- JavaFX dependencies are Java 11 only, revert and exclude JavaFX authenticator from Maven build
- Add JavaFX scene web dependency
- Add JavaFX swing dependency in POM
- EWS: add jettison dependency in pom
- Do not try interactive authentication in server mode
- EWS: Merge non interactive Oauth2 authentication
- EWS: Office365 modern authentication (Oauth2) with phone application MFA support
- EWS: Implement REST/Json method for Oauth authentication
- EWS: send username to interactive authentication frame
- EWS: implement interactive OAuth2 authentication (still experimental)
- EWS: Add jettison library for Oauth support
- EWS: First working prototype of interactive Oauth2 authentication

### Enhancements
- Improve SWT not available message
- Detect headless to force server mode, do not allow O365 interactive authentication in this case
- Javafx cleanup
- Fix empty setting handling: return default setting on empty value
- Implement headless choose client certificate and PKCS11 password prompt
- Package hi res image
- Merge #50 Assume notray in server mode 
- Display connection mode help as a tooltip
- Merge DesktopBrowser: add support for xdg-open directly, see https://github.com/mguessan/davmail/pull/5
- Workaround for login.microsoftonline.com cookie domain
- i18n new davmail.mode setting
- Drop davmail.enableEws to create a new davmail.mode setting that can be EWS, WebDav, O365, O365Modern, O365Interactive or Auto
- Another JavaFX message fix
- Fix is.javafx default value
- Default is.javafx value
- Improve version check message
- Add a JavaFX check message
- Drop JavaFX runtime and use conditional build instead
- Add JavaFX runtime as a compile time dependency
- Remove last jsmooth dependency
- Adjust default davmail.properties for server mode usage
- Drop jarbundler
- Add jettison dependency to windows wrappers and installers
- Fix RFE #101: Add a new davmail.userAgent setting to let users force DavMail user agent
- Add oraclejdk11 and openjdk11 to Travis CI targets
- Try to add Javafx dependency for OpenJDK 11
- Make message info level in ant build

### Linux
- Linux: Move spec file to root
- Fix relative path in launch script
- Copy davmail launch script to dist
- Linux: Drop old davmail.sh script
- Linux: merge external source files in main source tree
- Linux: Move init files from contribs to src/init
- Linux: compile with JavaFX on Fedora
- Linux: force Java 7 on RHEL 6 and do not deploy appstream on openSUSE_Leap_42.3
- Linux: drop reference to old architecture specific package
- Remove old hardcoded uids reference
- Linux: drop dependency to LSB functions in init script
- Linux: merge pull request https://github.com/mguessan/davmail/pull/4 include appdata file in rpm and deb packages
- Linux: merge davmail.sh to use a single script in all cases
- Linux: improve wrapper according to audit
- Linux: adjust desktop categories according to OpenSuse constraints, see https://en.opensuse.org/openSUSE:Packaging_desktop_menu_categories
- Linux: Simplify DavMail wrapper
- Linux: make spec file compatible with more distributions
- Linux: Additional notes on running DavMail with systray on Ubuntu 18
- Linux: merge RPM and Debian desktop files
- Linux: use simple name instead of path in desktop file
- Linux: drop desktopentry ant task
- Linux: move old desktop file to src/desktop
- Linux: Prepare desktop file merge
- Linux: merge pull request https://github.com/mguessan/davmail/pull/2 remove deprecations and duplicate main categories in desktop file, missing lf
- Linux: merge pull request https://github.com/mguessan/davmail/pull/2 remove deprecations and duplicate main categories in desktop file
- Linux: Add changelog entry for release in spec file
- Linux: fix spec file changelog date
- RPM: update init and logrotate from build.opensuse.org

### Caldav
- Caldav: another fix for #344 Problem with Calendar and tasks, fix properties list
- Caldav: fix for #344 Problem with Calendar and tasks, calendar:MyResponseType is also calendar only on Exchange 2007
- Caldav: fix for #344 Problem with Calendar and tasks, Exchange 2007 does not accept ismeeting property request on non calendar items

### Documentation
- Doc: Convert release notes to markdown format
- Doc: add contribute section in README.md
- Doc: fix appveyor link
- Add download links to README.md
- Doc: fix typo
- Doc: update linux instructions, remove obsolete content
- Doc: reference official debian package and build.opensuse RPM packages in server setup documentation
- Doc: Drop piwik reference from site, no longer available on Sourceforge
- Doc: Add appveyor badge in README.md
- Doc: Add an FAQ entry on Office 365 modern authentication and MFA
- Doc: adjust indentation to match pull request
- Doc: appdata file from https://github.com/mguessan/davmail/pull/3
- Doc: make image link relative in README.md
- Doc: update release notes
- Add Sourceforge download badge to README.md

### IMAP
- IMAP: implement #341 imap SPECIAL-USE

### Appveyor
- appveyor: add Java 10 in matrix
- Build: use -trunk suffix for all artifacts
- appveyor: get artifacts
- Back to ant dist
- appveyor: fix for nsis 3
- appveyor: separate makensis from build file
- appveyor: copy processwork nsis plugin
- appveyor: switch from compile to dist target
- appveyor: disable test
- appveyor: fix ANT_HOME
- appveyor: debug
- appveyor: fix ant path
- Try to create an appveyor build descriptor

### Carddav
- Carddav: prefer urlcompname (client provided item name) for contacts over EWS

### OSX
- OSX: restore OSX greyscale icons


## DavMail 4.9.0 2018-09-05
Includes a lot of enhancements, library upgrades, improved Linux desktop support, code cleanup
and a brand new Carddav distribution list support.
DavMail repository is now synced with Github, including Travis CI integration.

### Enhancements:
- Update Jcharset to 2.0
- Upgrade JavaMail to 1.5.6
- Fix maven dependencies, reference local jars for libraries missing in main Maven repository
- Sample config to log connections in a separate log
- Provide command to launch DavMail without SWT
- Cleanup from audit
- Fix from audit: remove duplicate code
- Remove old repositories from Maven pom and add stax2-api dependency
- Remove dependency to xercesImpl-2.8.1.jar
- Drop jsmoothgen-ant-0.9.9-7-mgu2.jar, replaced with WinRun4J
- Upgrade to Woodstox 5.1.0, waiting for pull request to drop patch, see https://github.com/FasterXML/woodstox/pull/56
- Fix from code audit
- Allow console logging in server mode
- Implement -server command line option
- Implement compile target for Java 9 and later

### Linux:
- Linux: Allow JDK 11 with Debian package
- Linux: disable system tray on Ubuntu 18

### Carddav:
- Carddav: Add unit test to check CRLF conversion in multiline properties
- Carddav: by jbhensley, drop carriage returns from property value
- Carddav: distribution list / contacts unit tests
- Carddav: by jbhensley, fix vCard PHOTO property. Tested on iOS 11.2.6 and Outlook 2013 
- Carddav: Detect empty picture data
- Carddav: Use cn as default sn for distribution lists
- Carddav: avoid NullPointerException with empty distribution lists and prefer user provided photo
- Carddav: search for members on all email attributes
- Carddav: Merge contact and distribution list search
- Carddav: implement distribution list create and update
- Carddav: Merge DistributionList with Contact
- Carddav: First step at distribution list implementation, retrieve DL and members

### Github:
- Add link to github repo
- Add Travis build status
- Fix old developerConnection and remove prerequisites according to Travis CI log
- First try at travis CI config
- Added : /trunk/README.md

### IMAP:
- IMAP: fix from code audit
- IMAP: fix #689 Double space in UID FETCH response
- IMAP: include Conversation History in standard folder list
- IMAP: fix patch #49 mixed case INBOX select by google

### EWS:
- EWS: fix duplicate bcc definition
- EWS: improve error handling, get field names on update item error

### Caldav:
- Caldav: workaround for invalid RRULE with both COUNT and UNTIL values leading to ErrorMimeContentConversion failed error
- Caldav: cleanup from audit
- Caldav: fix timezone failover
- Caldav: unit test for Korganizer duplicate timezone bug
- Caldav: workaround for Korganizer duplicate timezone bug
- Caldav: Fix recurrence enumeration values

### Documentation:
- Doc: add FAQ entry for Office 365


## DavMail 4.8.6 2018-06-14
Bugfix release with latest Caldav EWS enhancements, also includes fixes for old standing bugs in bug tracker.

### IMAP:
- IMAP: Fix #631 IMAP SEARCH CHARSET US-ASCII fails

### Caldav:
- Caldav: fix #687 can't move event to trash in a shared mailbox
- Caldav: fix multivalued field update, send DeleteItemField instead of SetItemField with an empty value when field has no value, should fix bug #682
- Caldav: Detect X-MOZ-LASTACK and X-MOZ-SNOOZE-TIME updates to avoid sending notifications on dismiss with Thunderbird

### Documentation:
- Doc: update roadmap

### Enhancements
- Fix #476, try to avoid deadlock with a connection manager object lock
- Fix #456 longstanding proxy handling issue
- Update spec file
- Add GPLv2 license at root


## DavMail 4.8.5 2018-04-10
More Caldav fixes, drop SWT on windows and try to improve tray support detection on various Linux distributions.

### Caldav:
- Caldav: test notification dialog
- Caldav: do not throw exception on invalid email in getFreeBusyData
- Caldav: EWS isorganizer is Exchange 2013 and later only, switch to myresponsetype
- Caldav: fix #306, do not try to retrieve textbody on Exchange < 2013
- Caldav: do not try to update etag if latest response item is empty
- Caldav: fix #679, invalid date exception on recurring event with an end date update
- Caldav: fix #346, map America/Chicago to Central Standard Time instead of Central America Standard Time

### Enhancements:
- Drop redline library
- Disable tray on Gnome
- Drop platform specific Linux packages, drop SWT on windows, remove dist-rpm (user build.opensuse.org instead), upgrade SWT to 4.6 on Linux
- Upgrade htmlcleaner to 2.21, see https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=891916
- Fix popMarkReadOnRetr setting save
- Update winrun4j wrappers

### Linux:
- Refactor icon handling, automatically adjust color/size to match common default themes on Linux and add a new davmail.trayBackgroundColor setting to let users set their own theme color
- Make spec file compatible with RHEL 7 / Centos 7

### Documentation:
- Doc: cleanup supported versions


## DavMail 4.8.4 2018-04-03
The main change in this release is the new davmail.caldavAutoSchedule setting to let users choose between
client or server side calendar notification management. Most other fixes are also related to Caldav refactoring.

### Caldav:
- Caldav: Add davmail.caldavAutoSchedule to GUI
- Caldav: introduce a new setting davmail.caldavAutoSchedule to enable or disable automatic scheduling in EWS mode
- Caldav: try to fix #674, do not send notifications on event update
- Caldav: EWS refactoring, avoid converting simple events to meetings
- Caldav: Restore calendar-schedule behavior in Webdav mode
- Caldav: in EWS mode, ignore urlcompname and always use itemid as item name, except if explicitly requested
- Caldav: isMeeting EWS flag is not reliable, check for attendees with displayto and displaycc
- Caldav: Fix isorganizer detection in deleteItem

### IMAP:
- IMAP: fix regression on search NOT KEYWORD

### Documentation:
- Documentation: fix typo in FAQ
- Doc: Improve Thunderbird LDAP documentation

### Enhancements:
- EWS : cleanup from audit
- Exclude log files from build


## DavMail 4.8.3 2018-01-28
More EWS Caldav refactoring, fix regressions noticed in 4.8.2 and merge a lot of user provided patches.
New calendar event handling is not yet enabled by default, please use davmail.caldavRealUpdate=true to 
check this new experimental implementation.

### Caldav:
- Caldav: add cancelled translation
- Caldav: EWS refactoring, map status field, except CANCELLED not supported by Exchange
- Caldav: implement editable notifications on meeting cancel
- Caldav: do not send notifications if user canceled in edit dialog
- Caldav: Make sure we set all notification options on event update
- Caldav: Revert status conversion that triggers regressions and enable notifications on create
- Caldav: test multiline value handling in VCalendar
- Caldav: test case for loadVtimezone and searchTasksOnly
- Caldav: in loadVtimezone, delete existing temp folder first to avoid errors
- Caldav: EWS refactoring, make sure we send meeting notifications
- Caldav: fix #666 trailing "nn" added to tasks description synced from exchange
- Caldav: EWS refactoring, only update reminder info on meetings when not organizer
- Caldav: Fix meeting response body, send and save copy
- Caldav: EWS refactoring, implement edit notifications comment
- Caldav: fix task update regression

### Enhancement:
- Merge #47, support soTimeout and connectionTimeout on exchange
- Merge patch #46: Maven patch to add resources in the target/davmail.jar
- Test: fix initial server and logging settings
- Copy release notes to dist on release
- Change test cases to load credentials from a separate test.properties file
- Remove old jsmooth config file
- Apply patch to fix #601 invalid davmail.server.certificate.hash format

### Documentation:
- Doc: fix #320 Wrong link in documentation 

### IMAP:
- IMAP: Accept US-ASCII as charset in search condition
- IMAP: fix #40 KEYWORD support for spring-integrations

### OSX:
- OSX: Update universalJavaApplicationStub to 2.1.0

### Windows:
- Windows: Create a 64 bits installer for windows

### Carddav:
- Carddav: merge patch #44 Allow disabling reading photo from contact data 


## DavMail 4.8.2 2018-01-02
EWS Caldav refactoring in progress, enabled through new davmail.caldavRealUpdate setting
to avoid regressions as seen in 4.8.1.

### Caldav:
- Caldav: EWS refactoring, handle mozilla alarm fields
- Caldav: EWS refactoring, implement modified occurrences
- Caldav: EWS refactoring, implement excluded dates (deleted occurrences)
- Caldav: optionally enable new EWS caldav implementation with davmail.caldavRealUpdate
- Caldav: Improve meeting response detection to avoid NullPointerException
- Caldav: EWS refactoring, implement reminder update


## DavMail 4.8.1 2017-12-12
Last release before major EWS caldav refactoring, includes only the first behaviour change:
do not delete existing meeting on Accept/Decline, just send answer to organizer. Also includes
central directory photo handling in Carddav service, improved Java 9 support and many other
bug fixes.

### Caldav:
- Caldav: temporarily disable EWS refactoring
- Caldav: EWS refactoring, implement multiple day recurrence and end date
- Caldav: EWS refactoring, first recurrence implementation step, improve ignore etag handling
- Caldav: EWS refactoring, manage attendees
- Caldav: prepare calendar refactoring, do not delete/add received meeting items, just send an Accept/Decline message
- Caldav: fix regression, busy status is case sensitive in EWS
- Caldav: fix #657 Tentative events shows as accepted in Thunderbird with user provided patch
- Caldav: experimental davmail.ignoreNoneMatchStar to let DavMail overwrite existing event automatically processed by Exchange.

### Carddav:
- Carddav: implement get photo from Active Directory

### Enhancements:
- update winrun4j wrappers
- Upgrade slf4j to 1.7.25 (patch #45)
- Fix 654: trim OWA/EWS url
- Ant: improve java version check
- Remove useless debug statement

### Smartcard:
- Smartcard: improve Java 9 error handling, no longer need a temporary file
- Smartcard: try to implement code compatible with all java versions including Java 9

### IMAP:
- IMAP: implement ON search filter
- IMAP: detect icedove header fetch to improve performance
- IMAP: make sure we never return null even with broken 0 uid message

### POP:
- POP3: Improve documentation on trash/sent folders purge, change default value from 90 to 0 (disable)

### Linux:
- Linux: check system tray with gtk_status_icon_get_geometry only with Unity to avoid regression with other window managers

### SWT:
- SWT: wait 10s for tray icon to be created

### OSX:
- OSX: fix Info.plist path
- OSX: fix JavaApplicationStub, do not expand folder classpath
- OSX: revert change to JavaX new key, does not work
- OSX: Update Info.plist to match new universalJavaApplicationStub


## DavMail 4.8.0 2017-03-23
This new release includes a lot of fixes and enhancements from user feedback, including
improved Exchange categories handling, up to date TLS settings to match current requirements,
various Caldav enhancements, and a fix for a major bug on IMAP large message handling.
An experimental connection logging feature is also available.

### Debian:
- Improve generated debian package to match official package, add keywords, move icon and adjust categories in desktop file

### RPM:
- update RPM default config file
- Remove ant-antlr from spec file

### Enhancement:
- Use Office365 url as default davmail.url value
- Remove Sun (Oracle) JDK dependency in unit test
- Experimental: compile Junit tests
- Change default url to https://outlook.office365.com/EWS/Exchange.asmx
- Log all connections disconnect
- Log all connections and logon success / failure
- Improve TLS settings: disable Client-initiated TLS renegotiation with jdk.tls.rejectClientInitiatedRenegotiation and force strong ephemeral Diffie-Hellman parameter with jdk.tls.ephemeralDHKeySize
- Remove sun.security.ssl.allowUnsafeRenegotiation=true system property as it's hopefully no longer required by iCal
- Fix broken davmailservice64.exe

### Documentation:
- Doc: document IMAP tags to Exchange categories custom mappings
- Doc: Add TLS settings documentation from support request #289
- Doc: fix maven generated site dash encoding

### Caldav:
- Caldav: Fix #643 VTODO PRIORITY 0 fails, map it to Normal importance
- Caldav: accept all meeting item types in calendar (MeetingMessage, MeetingRequest, MeetingResponse, MeetingCancellation)
- Caldav: fix #639, task description is not visible in Thunderbird, try to get description from text body
- Caldav: fix #628, remove METHOD: PUBLISH from events retrieved from Exchange
- Caldav: make sure retrieved item name is always the same as requested item name (e.g. for tasks stored value ends with .EML when requested value ends with .ics)
- Caldav: Additional recurrence search test

### IMAP:
- IMAP: flags to category conversion unit tests and make standard flags case insensitive
- IMAP: make IMAP flag to category lookup case insensitive
- IMAP: fix huge cache issue, chunk IMAP fetch triggers multiple full message download
- IMAP: fix regression after #41 IMAP wildcard LIST supportwith unit test
- IMAP: apply patch #41 IMAP wildcard LIST supportwith unit test
- IMAP: mark message seen only if unseen
- IMAP: fix #629 Read email doesn't stay read. According to IMAP RFC: The \Seen flag is implicitly set
- IMAP: merge patch from #634, copy mail doesn't preserve tag/category 
- IMAP: fix regression in mime message handling and rename mimeBody to mimeContent
- IMAP: Fix #633 Compatibility with javamail 1.5.6, store byte array instead of SharedByteArrayInputStream

### Carddav:
- Carddav: do not send empty EmailAddresses collection tag

### EWS:
- EWS: Change isrecurring property to PidLidRecurring (0x8223)
- EWS/Webdav: implement exists filter condition

### SWT:
- SWT: Try to detect if system tray is indeed available

### OSX:
- OSX: Update universalJavaApplicationStub to version 2.0.1
- OSX: Upgrade jarbundler to 3.3.0

### SMTP:
- SMTP: append a line feed to avoid thunderbird message drop


## DavMail 4.7.3 2016-11-22
Another bugfix release, mostly from user feedback. Also improve Windows installer to let users
choose whether they want to run DavMail automatically at logon.

### Enhancement:
- Improve windows installer, make auto start at logon optional
- Update release guide with opensuse build env
- update icon cache
- Update winrun4j wrappers icon to 128x128

### EWS:
- EWS: workaround for invalid cookie domain on Office365

### DAV:
- DAV: merge patch from #232 Outlook-created appt does not go through. OWA-created one does

### IMAP:
- IMAP: additional fix for #626, workaround for from: header not searchable over EWS
- IMAP: fix for #626, workaround for to: header not searchable over EWS

### SMTP:
- SMTP: Merge patch 627 by Peter Chubb, server returns incorrect code on authentication failure 


## DavMail 4.7.2 2016-04-09
Bugfix release, detect Exchange throttling to temporarily block requests and a few Carddav fixes.

### EWS:
- EWS: handle Exchange throttling, suspend all requests according to server provided delay
- EWS: send DavMailException instead of authentication exception on EWS not available error

### Enhancements:
- 128x128 DavMail icon
- Add a new davmail.httpMaxRedirects setting
- DAV: add a hidden davmail.disableNTLM setting

### Carddav:
- Carddav: fix another regression on contact create with empty field
- Carddav: remove email over EWS unit test
- Carddav: fix email address removal over EWS

## DavMail 4.7.1 2015-12-19
Bugfix release, mainly for Carddav regression over EWS, also includes an NTLM support enhancement.

### Enhancement:
- Improve NTLM support try to send hostname as workstation name instead of UNKNOWN
- Fix notification dialog message
- Prepare ExchangeSessionFactory refactoring
- Fix typo in french translation
- Fix broken Sourceforge link in About dialog

### Carddav:
- Carddav: fix regression on contact update with empty field triggering DeleteItemField

## DavMail 4.7.0 2015-11-05
This new release contains a lot of fixes from user feedback, a new -notray command line
option to force window mode and avoid tricky tray icon issues on Linux and native
smartcard support on Windows.

### Caldav:
- Caldav: Map additional priority levels
- Caldav: fix missing LAST-MODIFIED in events

### Enhancements:
- Improved tray icon with alpha blend
- Fix imports
- Prepare mutual SSL authentication between client and DavMail implementation
- Implement -notray command line option as a workaround for broken SWT and Unity issues
- Change warning messages to debug in close method
- Improve client certificate dialog, build description from certificate
- Exclude client certificates not issued by server provided issuers list

### IMAP:
- IMAP: Additional translations and doc for new IMAP setting
- IMAP: Merge patch by Mauro Cicognini, add a new setting to always send approximate message in RFC822.SIZE to avoid downloading full message body
- IMAP: fix regression with quotes inside folder names
- IMAP: handle quotes inside folder names correctly

### OSX:
- OSX link local address on loopback interface
- Exclude arguments starting with dash to avoid patch 38 regression on OSX

### Documentation:
- Doc: Document -notray option
- Switch to OpenHub instead of Ohloh

### EWS:
- EWS: prepare distribution list implementation
- Fix #254 davmail.exchange.ews.EWSException: ErrorIncorrectUpdatePropertyCount

### Linux:
- Refresh davmail.spec, make RPM noarch
- Handle missing or broken SWT library

### Windows:
- Windows: Make MSCAPI keystore type available in Settings for Windows native smartcard support
- Instantiate MSCAPI explicitly to access Windows Smartcards
- Enable native Windows SmartCard access through MSCAPI (no PKCS11 config required)

### Carddav:
- Carddav: Test case for comma in ADR field
- Carddav: Do not replace comma on ADR field, see support request 255
- Caldav: Ignore missing END:VCALENDAR line on modified occurrences
- CardDav: Add empty property test case


## DavMail 4.6.2 2015-08-19
Another bug fix release with some efforts on packaging.

### Packaging:
- Compute distribution packages checksums
- Maven: set mimimum Maven version and fix FindBugs filter
- Maven: add Gtk lib in repo to avoid ClassNotFound
- Maven: exclude non DavMail classes from FindBugs report
- Maven: Update POM to Maven 3
- Separate prepare-dist ant task
- Separate jar ant task
- RPM: Change log for 4.6.1 and remove ant-nodeps dependency for Fedora >=19 compatibility
- RPM: first step to a noarch package, externalize SWT dependency
- RPM: Add rcdavmail link, mark logrotate config file
- RPM: Fix License and URL

### Enhancements:
- Fix davmailconsole.exe
- Switch to TLS in DavGatewaySSLProtocolSocketFactory
- Improve refresh folder logic, ctag stamp is limited to second, check message count
- Try to support Citrix NetScaler authentication form
- Improve Java version check
- Update compile level to 1.6
- Remove unneeded catch section

### WebDav:
- DAV: avoid NullPointerException trying to access Exchange 2013 in Dav mode

### IMAP:
- IMAP: refactor IMAP test cases
- IMAP: ignore Draft flag on update, Draft is readonly after create
- IMAP: fix new IMAP tokenizer
- IMAP: rewrite tokenizer to manage quoted folder names and complex search
- IMAP: Fix #591 Properly escape quotes in folder names
- IMAP: additional IMAP test cases

### EWS:
- EWS: davmail.enableChunkedRequest default value is now false, as IIS does not support chunked requests
- EWS: Make chunked content optional in CreateItemMethod with new davmail.enableChunkedRequest property
- Use EWS path in davmail.properties template file

### Doc:
- Doc: add Indicator SystemtrayUnity to linux doc
- Fix Javadoc

### OSX:
- OSX: merge patch 38, allow commandline options to run multiple instances.

### SMTP:
- SMTP: use content chunk to send large messages


## DavMail 4.6.1 2015-02-17
Bugfix release to fix recent regression with Office 365,
also includes a few Linux and IMAP enhancements.

### Linux:
- RPM: exclude Growl library from RPM package
- Add genericname to desktop entry
- RPM: Fix warning the init script refers to runlevel 4 which is admin defined. No distribution script must use it
- Detect and log message for Unity users
- RPM: Fix JAVA HOME detection for openSUSE_13.2
- RPM: update spec file from OpenSuse build by Dmitri Bachtin and  Achim Herwig

### SWT:
- SWT: improve tray init, preload image and add a delay on first message

### Enhancements:
- Add a few more logging statements

### IMAP:
- Fix #36 Endless loop when using IMAP IDLE feature with SSL sockets, replaced thread sleep with a short timeout on socket read

### EWS:
- EWS: update checkEndPointUrl, send get root folder request instead of static wsdl request no longer available on Office365


## DavMail 4.6.0 2015-01-27
Bugfix release with many IMAP enhancements over EWS, implement batch move items,
also includes a brand new generic OSX package to handle new OSX java behaviour.

### OSX:
- OSX: refactor OSX package based on universalJavaApplicationStub
- Replace Java application stub with https://github.com/tofi86/universalJavaApplicationStub/blob/master/src/universalJavaApplicationStub

### Doc:
- Doc: update OSX setup documentation
- Doc: additional Linux instructions for Ubuntu 14
- Fix #31 A typo in davmail.properties example

### EWS:
- EWS: improve main calendar folder test
- EWS: fix batch move
- EWS: Adjust paged search for folders
- EWS: implement batch move items
- EWS: improve folder paged search
- Prepare batch move implementation
- EWS: force NTLM in direct EWS mode
- EWS: implement batch move method
- EWS: switch to GetMethod to check endpoint
- EWS: take paging into account in appendSubFolders
- EWS: fix ErrorExceededFindCountLimit on FindFolder requests
- EWS: avoid NullPointerException in fixAttendees

### Linux:
- Allow Java 8 and default jre in debian package

### IMAP:
- IMAP: fix 587 log and skip broken messages

### Caldav:
- Caldav: fix #98 Support of Contacts in CardDav REPORT 
- Fix #35 duplicates in updated reoccurring events 

### Enhancements:
- Fix potential CVE-2014-3566 vulnerability
- From audit: remove throws statement
- Adjust KerberosHelper logging message
- Fix for #534 Kerberos Authentication doesn't seem to be work cross domain

### LDAP:
- LDAP: reset icon after search


## DavMail 4.5.1 2014-07-20
Bugfix release to fix Exchange 2013 regressions and wrong Europe/London timezone mapping.

### DAV:
- DAV: Another email address failover

### Caldav:
- Caldav: Fix GMT Standard Time mapping to Europe/London
- More timezones
- Caldav: do not request additional properties for MeetingCancellation and MeetingResponse
- Caldav: merge 33, apply myresponsetype partstat on all Exchange versions
- Caldav: fix 569 and patch 32, avoid NullPointerException with Exchange 2013

### EWS:
- EWS: Avoid null in log message

### Enhancements:
- Merge patch 34: Fix false positive when searching for user alias and email 

### Doc:
- Update FAQ on EWS endpoint not available error


## DavMail 4.5.0 2014-06-03
Includes EWS performance enhancements, improved Exchange 2013 support and many fixes detected by Coverity audit tool

### EWS:
- EWS: adjust declined item handling
- EWS: used paged search with static search condition, send a single request when folderSizeLimit is enabled
- EWS: Allow Item and PostItem elements in message folders
- EWS: Improve Exchange 2013 support
- EWS: avoid mime content String conversion
- EWS: Improve javadoc and make ItemId serializable
- EWS: expect UTF-8 in options responses
- EWS: remove unused field
- EWS: Force encoding in mimeContent decode
- EWS: Avoid /owa form request in direct EWS mode

### Documentation:
- Add davmail.defaultDomain to template properties file
- Doc: Change default port in Thunderbird directory config screenshot
- Update settings image

### IMAP:
- IMAP: fix 564, Moving / copying messages in public mailbox 
- IMAP: implement separate thread folder load on STATUS request to avoid client timeouts
- IMAP: fix 209, use isEqualTo instead of contains to search keywords on Exchange 2010
- IMAP : various enhancements from audit, switch to enum and avoid NullPointerException

### OSX:
- OSX: force working directory to application root

### Enhancements:
- Upgrade svnkit to 1.8
- Update WinRun4J wrappers
- Upgrade WinRun4J to 0.4.5
- Kerberos: make sure access to client login context is synchronized
- Kerberos: synchronize access to clientLoginContext
- Make MessageWrapper static
- Revert Java 7 only changes
- Do not try WebDav mode if owa url ends with /ews/exchange.asmx
- Additional code fixes from audit, do not try to get time zone from options page in direct EWS mode
- Force encoding in message create thread
- Prepare WoodStox Xml10AllowAllEscapedChars setting implementation
- Apply Base64 refactoring to all classes
- Apply new base64 methods to CaldavConnection
- Refactor base64 encode/decode methods
- Improve contact picture error handling
- Improve session factory log statements
- Avoid null in log statement
- New experimental davmail.exchange.maxConnections setting to limit concurrent connections to Exchange server

### Caldav:
- Caldav: fix bug in VCalendar dtend check

### POP:
- Refactor PopConnection, use enumeration instead of int

### Coverity:
- From coverity: listFiles may return null
- From coverity: avoid null dereference in VProperty
- From coverity: avoid null dereference in getFolderPath
- From coverity: check null image in FrameGatewayTray
- From coverity: more encoding fixes
- From coverity: synchronize HttpClient cookies access
- From coverity: synchronize FileAppender creation
- From coverity: URI.getPath may return null
- From coverity: ImageIO.read may return null
- From coverity: trayItem.getImage may return null
- From coverity: client.getInetAddress() may return null
- From coverity: createSaslServer may return null
- From coverity: use UTF-8 encoding in Hex conversion methods
- From coverity: force encoding to UTF-8 on socket output stream
- From coverity: set encoding on String to bytes conversion
- From coverity: use getParamValue instead of getParam().getValue() to avoid null dereference
- From coverity: editor pane font can be null
- From coverity: check null after ImageIO.read

### UI:
- UI: small fixes on ui code from audit
- Refactor SWT tray dispose management on exit
- Handle missing resource in loadSwtImage

### DAV:
- DAV: decode base64 content as ASCII


## DavMail 4.4.1 2014-01-30
Includes mostly EWS support enhancements, Sogo carddav issue workaround, new
IMAP uid based paging implementation to handle concurrent folder changes
and a few other bug fixes.

### Documentation:
- Doc: update donation link to let user choose currency
- Doc: Update iCal Caldav setup for OSX Mavericks
- Doc: update server setup documentation, use noinstall package on Windows
- Doc: Update news url in release guide

### EWS:
- EWS: workaround for user reported issue, less strict filter in isItemId
- EWS: Override authentication mode test: EWS is never form based
- EWS: new paging implementation based on imap uid sort to avoid issues on concurrent changes on searched folder
- EWS: in direct EWS mode, try to use ResolveNames to get current user email address
- EWS: Another try for checkEndPointUrl, head on /ews/exchange.asmx and follow redirects to wsdl
- EWS: improve ItemId vs user provided item name detection

### Enhancements:
- From coverity: avoid null dereference when no network interface is available
- From coverity: set encoding on byte array to String conversion
- From coverity: fix resource leak
- Update svnant libraries
- Fix reauthentication issue: separate domain from username in credentials
- Fix NullPointerException in thread "Shutdown" - tray disposal - server mode
- Fix authentication failure after session expiration

### Caldav:
- Caldav: Fix 555 another broken Israeli timezone
- Caldav: ignore invalid BEGIN line inside object (Sogo Carddav issue)


## DavMail 4.4.0 2013-11-13
Added folder size limit setting to let users avoid IMAP timeouts and reduce memory footprint.
Also contains many documentation updates, including updated OSX instructions for Mavericks and
some more bugfixes on IMAP, Caldav (iOS 7 user agent) and SMTP.

### Documentation:
- Doc: update roadmap
- Doc: reference Marcin Dulak as contributor (RPM package maintainer)
- Doc: Document OSX Mavericks IMAP account creation
- Doc: improve FAQ
- Doc: Fix OSX download instructions
- Doc: Update OSX doc
- Doc: additional FAQ comment on shared calendar hierarchy
- Doc: update smtp screenshot
- Doc: update more Thunderbird screenshots
- Doc: update Lightning screenshots
- Doc: document calendar.caldav.sched.enabled in main Lightning setup doc

### Enhancements:
- Additional IMAP unit tests
- Add davmail.folderSizeLimit to UI and documentation
- Fix AbstractConnection.readContent, see https://sourceforge.net/p/davmail/bugs/538/

### SMTP:
- SMTP: create a new davmail.smtpStripFrom boolean property to force From: header removal 

### IMAP:
- IMAP: Make flags case insensitive on append
- IMAP: improve uidNext implementation
- Italian IMAP flag translation thanks to puntogil@libero.it
- IMAP: fix 538, send capabilities untagged response to avoid timeout on large message APPEND
- IMAP: Implement davmail.folderSizeLimit

### Caldav:
- Caldav: change user agent test to include all iOS versions


## DavMail 4.3.4 2013-09-09
Added a new OSX Java7 package, IMAP header management regression fixes.
Also fixed a few bugs reported by users and improved documentation.

### Documentation:
- Doc: Reference Alexandre Rossi as Debian package maintainer
- Doc: Update FAQ shared mailbox path
- Doc: update svn repository location in build doc
- Force language on donations link
- Doc: OSX LaunchDaemon

### Enhancements:
- Add trust="true" to scp command
- Italian translation from  gil cattaneo https://bugzilla.redhat.com/show_bug.cgi?id=894413
- Allow identical username/userid in multiple factor authentication form
- Rethrow DavMailException on connect exception
- Fix NullPointerException on server unavailable
- New experimental davmail.popCommonDeleted flag to switch to a different property on old Exchange 2003 servers

### Caldav:
- Caldav: flag ORGANIZER participant status as ACCEPTED instead of NEEDS-ACTION
- Caldav: do not overwrite X-MICROSOFT-CDO-BUSYSTATUS if TRANSP is not provided
- Merge patch to set sensitivy on VTODO

### OSX:
- OSX: Get application path from library path with Java7 launcher
- OSX: move libgrowl to library path
- OSX: build Java 7 package
- Customized OSX app launcher messages
- Java7 OSX app launcher

### EWS:
- EWS: fix 537, detect 507 Insufficient Storage

### WebDav:
- DAV: set SO timeout on connection

### IMAP:
- IMAP: new header fix, do not rely on messageheaders attribute on full headers request, load message
- IMAP: improve invalid message header test
- IMAP: fix invalid message header filter
- IMAP: write message without headers on BODY[TEXT] fetch


## DavMail 4.3.3 2013-06-13
Make keep alive optional new davmail.enableKeepalive setting and fix regressions in IMAP handler.

### Enhancements:
- Update Maven POM to new Sourceforge project site
- Rename new setting to davmail.enableKeepalive and include in settings GUI

### IMAP:
- IMAP: make keepalive spaces optional with new davmail.imapEnableKeepalive setting
- IMAP: interrupt EWS folder load on client timeout
- IMAP: fix missing headers with Outlook
- IMAP: Detect invalid content in message header field
- IMAP: skip Microsoft Mail Internet Headers Version 2.0 in message headers field
- IMAP: remove additional logging


## DavMail 4.3.2 2013-06-06
Another bugfix release.

### IMAP: Fix regression on IMAP select folder with thunderbird


## DavMail 4.3.1 2013-06-05
Bugfix release to fix regressions with some IMAP clients and enhanced FetchMail support.

### OSX:
- OSX: switch back to single archive and add a comment

### IMAP:
- IMAP: fix double header content and optimize header fetch with Fetchmail
- IMAP: fix regression in append envelope
- IMAP: fix multithreaded folder load implementation to support more IMAP clients

### POP:
- POP: fix 3613743, remove additional +OK during message RETR

### Linux:
- dos2unix on davmail.spec
- Update davsvn


## DavMail 4.3.0 2013-05-21
New keep alive mechanism to avoid most IMAP and POP client timeouts: load large messages
in a separate thread and send a character on client connection every ten seconds. Also
includes some bug fixes, documentation enhancements and experimental Exchange 2013 support.

### OSX:
- OSX: Add a readme.txt file to OSX package to help users temporarily disable Gatekeeper

### Documentation:
- Doc: improve OSX setup doc for Mountain Lion
- Update SSL doc, spaces in library path may break Sun PKCS11
- Update linux setup doc for Ubuntu 13 users
- Update server setup doc with detailed davmail.properties file

### Caldav:
- Caldav: New workaround for Lightning bug: sleep for 1 second on server unavailable error

### IMAP:
- Interrupt message load thread on client connection exception
- IMAP: try to avoid timeout on large message FETCH with a KeepAlive space character
- IMAP: try to avoid timeout on folder SELECT with a KeepAlive space character
- Improve message list count implementation
- Always sort by IMAP uid desc

### POP:
- POP: load big messages in a separate thread

### EWS:
- EWS: Fix regression in checkEndPointUrl, get /ews/services.wsdl
- Fix for Exchange 2013 support
- EWS: implement SortOrder

### Enhancements:
- Improve DavMail shell scripts
- Set default file path to /var/log/davmail.log in reference davmail.properties
- Set a default log file size in reference davmail.properties
- Use reference davmail.properties in war file
- Disable broken dist-rpm
- Reference server davmail.properties file
- Add disableUpdateCheck to default davmail.properties file
- Exclude WinRun4J from debian package
- Exclude libgrowl and winrun4J from war package
- Exclude winrun4J from linux packages
- Set davmail.logFileSize to 0 to use an external rotation mechanism, e.g. logrotate
- Merge latest changes from Marcin Dulak
- Archive jsmooth wrappers
- Fix version in spec file


## DavMail 4.2.1 2013-04-11
Improved Kerberos support and a few bug fixes reported on tracker.

### Kerberos:
- Add enable Kerberos checkbox to DavMail GUI
- Kerberos read KRB5CCNAME environment variable to set ticket cache path
- Kerberos implement graphical callback on missing token
- Kerberos: Renew almost expired tickets and detect expired TGT in cache => try to relogin 
- Kerberos: Handle client context timeout, try to recreate context
- Improve KerberosHelper implementation, prepare credential delegation support

### Enhancements:
- Try to fix 3606267: New debian dependency with wrong package name
- Fix 3602588, allow oracle-java7-jre
- Fix regression: disable console appender in gui mode
- Use NewIbmX509 on IBM JDK instead of NewSunX509 SSL algorithm implementation
- Fix 3602351, detect missing item

### EWS:
- EWS: do not catch socket exception in executeMethod
- EWS: workaround for Nokia N9 Caldav implementation bug

### DAV:
- DAV: throw error on broken connection

### SMTP:
- SMTP: do not allow send as another user on Exchange 2003

### IMAP:
- IMAP: exclude Mutt header request from size optimization
- IMAP: change kerberos login error message
- IMAP send error on authentication failed

### Documentation:
- Doc: Additional Kerberos documentation
- Initial Kerberos documentation

### Caldav:
- Caldav: do not send 401 on authentication error in Kerberos mode


## DavMail 4.2.0 2013-02-26
Contains some enhancements on iOS 6 support, Debian package encoding issue fix
and partial Kerberos support (workstation mode) to provide transparent Exchange
authentication.

### Kerberos:
- Kerberos: implement server side security context and token handling
- Kerberos: server side login module
- Improve Kerberos logging and implement command line callback
- Do not set preemptive authentication in Kerberos mode
- Enable Kerberos authentication scheme with davmail.enableKerberos setting
- Kerberos authentication implementation: SpNegoScheme to implement Negotiate authentication scheme, KerberosHelper to handle ticket access and KerberosLoginConfiguration to replace JAAS configuration file

### Enhancements:
- Fix accept certificate message
- Make davmail.sh executable in platform independent package
- Update desktop entry comment
- Update RPM spec file from build.opensuse.org (marcindulak)
- Add libswt-cairo-gtk-3-jni to debian package dependencies
- Clear cookies created by authentication test
- Upgrade jackrabbit-webdav and htmlcleaner in davmailconsole wrapper
- Upgrade jackrabbit-webdav to 2.4.3
- Upgrade htmlcleaner to 2.2
- Exclude Jsmooth, nsi, OSX and contribs (with binary) from source only package
- Prepare source only package

### Bugfix:
- Check file encoding in build file
- Refactor StringUtil and encode ~ in urlcompname

### IMAP:
- IMAP: Implement custom IMAP flags to keywords mapping in settings

### Caldav:
- Caldav: add iOS6 user agent


## DavMail 4.1.0 2012-09-26
Bugfix release with improved IMAP support, including IMAP flags mapping to Outlook categories,
enhanced IMAP noop/idle support, fixed emClient Caldav support and many Caldav and EWS fixes.

### Documentation:
- Doc: update roadmap
- Doc: new FAQ entry, Exchange RSA two factor authentication form

### Caldav:
- Caldav: do not try to load tasks MIME body
- Caldav: workaround for 3569922: quick fix for broken Israeli Timezone issue
- Caldav: remove urlencoding workaround for emClient >= 4
- Caldav: Ignore 401 unauthorized on public event, return 200
- Caldav: Rename TZID also in RECURRENCE-ID
- Caldav: force 403 forbidden instead of 401 on unauthorized update to public folder item
- Caldav: Fix 3569934 NullPointerException on broken PROPFIND request
- Caldav: Fix 3567364, regression on from/to/cc handling in calendar related to IMAP search enhancement. Separate mapping for message fields/headers

### IMAP:
- IMAP: send updated flags on folder refresh
- IMAP: fix keyword handling to avoid sending \Seen as keyword
- IMAP: retrieve message count on folder
- IMAP: apply flag to keyword conversion in SEARCH, refresh folder before search
- IMAP: improve keyword support, map $label1 to 5 from Thunderbird to Outlook categories
- IMAP: fix keywords implementation, make it case insensitive, implement KEYWORD search
- IMAP: implement generic FLAGS mapping to Outlook categories
- IMAP: fix 3566412, range iterator is on folder messages, not messages returned from search

### EWS:
- EWS: Get primary smtp email address with ResolveNames in direct EWS mode

### Enhancements:
- Allow Java 7 to build DavMail
- Prepare message keywords/categories support

### WebDav:
- Dav: implement multivalued property suppord in ExchangeDavMethod

### Web:
- Web: Fix 3566941 Imap protocol is not activated by default in .war


## DavMail 4.0.0 2012-09-10
Includes full Exchange 2007 and 2010 support with EWS implementation, 
fixed OSX Mountain Lion support, switched Windows wrappers to WinRun4J 
and additional enhancements and bugfixes.

### IMAP:
- IMAP: workaround for broken message headers on Exchange 2010
- IMAP: log content if less than 2K
- IMAP: improve Exchange 2010 header search, use direct header names to implement substring search on some headers
- IMAP: additional fix for Exchange 2010 header search, use PR_TRANSPORT_MESSAGE_HEADERS
- IMAP: Exchange 2010 does not support header search, workaround to avoid duplicate items in Drafts folder with Thunderbird
- IMAP: fix 3553942, unexpected imap NIL response
- IMAP: detect and ignore missing message to avoid NullPointerException
- IMAP: improve bodystructure error handling

### Documentation:
- Doc: fix image swap
- Doc: update roadmap
- Doc: add Developed with Intellij Idea link

### Caldav:
- Caldav: encode semicolon in urlcompname
- Caldav: fix attendees in modified occurences
- Caldav: additional timezone names for Exchange 2010
- Caldav: additional timezones available in Exchange 2007
- Caldav: Partial fix for missing items on Exchange 2010
- Caldav: fix OSX Mountain Lion (iCal 6) support

### Enhancement:
- Merge patch 3488553: Make davmail.jar executable
- Merge patch from 3562031, advanced noProxyFor handling
- Display released version in about frame when different from current version
- Fix 3562031, implement davmail.noProxyFor setting to exclude hosts from proxy settings
- Merge preauthentication page patch
- Prepare pre authentication page merge in ExchangeSession
- Implement javascript redirect in executeFollowRedirects
- Prepare javascript redirect merge (multiple authentication pages)
- Try to improve shutdown hook

### Windows:
- Update download url in 64 bit wrappers to http://java.com/en/download/manual.jsp
- Add davmailservice64.exe WinRun4J service wrapper
- Replace 64 bits jsmoothgen with WinRun4J wrapper
- 64 bits Winrun4J wrapper
- Fix Winrun4J service wrapper implementation, launch a non daemon thread
- Win: switch to Winrun4J wrappers

### OSX:
- OSX: Add a note on Gatekeeper for OSX Mountain Lion users

### EWS:
- EWS: fix davmail.acceptEncodingGzip setting handling

## DavMail 3.9.9 2012-07-10
Bugfix release with major IMAP changes to improve sync performance,
many Caldav enhancements and bugfixes and some documentation updates.

### Caldav:
- Caldav: encode ? in urlcompname
- Caldav: fix 3534615, patch allday dates only on Exchange 2007
- Caldav: implement full contact folder dump at /users/<email>/contacts/
- Caldav: implement task priority over EWS
- Caldav: remove unsupported attachment reference to avoid iPhone/iPad crash
- Caldav: reintroduce davmail.caldavDisableTasks setting to disable tasks support
- Caldav: fix encode pipe | to %7C in urlcompname
- Caldav: encode pipe | to %7C in urlcompname
- CalDav: Fix 3512857, avoid double path encoding in DavExchangeSession.loadVtimezone()
- Caldav: improve Exchange 2007 EWS meeting support
- Caldav: rebuild meeting attendees only for Exchange 2007, Exchange 2010 ics parser is correct

### Enhancements:
- Fixes from audit
- store davmail.log in user home folder to avoid crash on first start when current directory is not writable by user
- Add WinRun4J to Maven POM and update windows service documentation
- Switch to WinRun4J for Windows service wrapper
- Fix 3494770: Add missing antlr runtime
- Upgrade svnkit for subversion 1.7 compatibility

### IMAP:
- IMAP: Fix 3534801, workaround for missing From header
- IMAP: fix 3441891, workaround for Exchange 2003 ActiveSync bug
- IMAP: experimental implementation of header only FETCH, do not download full message content and send approximate RFC822.SIZE (MAPI size)
- IMAP: avoid full message download on OSX Lion flags request with content-class header
- IMAP: exclude IDLE from infinite loop detection
- IMAP: add date header to rebuilt message
- IMAP: Force UTF-8 on message rebuild
- IMAP: implement RFC822 fetch request

### GUI:
- GUI: force alwaysOnTop on dialogs to make sure they are visible
- GUI: always bring dialog windows to front

### Documentation:
- Doc: add a new FAQ entry on shared mailbox access over IMAP
- Doc: Update doc to include Java 7
- Doc: small fix in Linux setup doc
- Doc: Update Linux instructions for Ubuntu 12 Natty
- Doc: New review
- Doc: update Thunderbird POP account setup doc
- Doc: Update SSL setup documentation on PKCS12 passwords
- Doc: add a note on hidden folders on OSX Lion
- Doc: Fix new thunderbird doc

### OSX:
- OSX: new hide from Dock setting available directly in UI (DavMail restart needed)

### Carddav:
- Carddav: Fix 3511472, implement fileas over EWS
- Carddav: Skip carriage return in ICSBufferedWriter

### EWS:
- EWS: disable gzip encoding if WIRE logging is at DEBUG level
- EWS: fix 3263905 ErrorInvalidPropertyRequest, do not update message:IsRead on appointments
- EWS: make isMainCalendar case insensitive
- EWS: revert chunked inputstream inside gzip and create new setting davmail.acceptEncodingGzip
- EWS: handle chunked inputstream inside gzip
- EWS: improve error message handling, log error description
- EWS: improve error handling on socket exception
- EWS: avoid NullPointerException in broken message rebuild

### WebDav:
- Dav: decode permanenturl to avoid double urlencoding issue
- Dav: decode url returned on saveappt cmd in DavExchangeSession.loadVtimezone()

## DavMail 3.9.8 2012-02-21
Prepare 4.0 release with improved Exchange 2010 support, added IMAP MOVE extension support,
include a new windows noinstall package and implement captcha authentication support.

### Documentation:
- Doc: update roadmap
- Doc: add a statement on adding NSIS to system path in build instructions
- Doc: update Thunderbird IMAP setup instructions for Thunderbird 10
- Doc: update java package reference
- Doc: update address book setup instructions for OSX Lion
- Doc: add Growl reference in OSX setup

### Enhancements:
- Fix nsis script: delete stax api jar on uninstall
- Fixes from audit
- New redline ant task definition fix
- Exclude Junit from binary packages
- Create Windows noinstall package
- Implement a new davmail.clientSoTimeout setting to adjust or disable connection timeout
- Improve message on invalid OWA uri
- Fix notification dialog test
- Improve Pinsafe captcha display
- workaround for broken form with empty action
- Implement ISA server PINsafeISAFilter support (captcha image)
- Upgrade Redline RPM
- Add StreamScanner.java from Woodstox 4.1.2
- Upgrade to Woodstox 4.1.2
- Fix 3454332: davmail.sh script missing shebang 
- add trust=true in upload-site

### IMAP:
- IMAP: fix search date format for Exchange 2010 support (ErrorInvalidValueForProperty)
- IMAP: implement SEARCH TEXT on from, to, cc, subject and body
- IMAP: send error on COPY/MOVE when message iterator is empty
- IMAP: implement MOVE RFC draft http://tools.ietf.org/id/draft-krecicki-imap-move-00.html
- IMAP: fix 3480516, () instead of NIL on empty envelope header
- IMAP: Fix 3479993, backslash in header

### SMTP:
- SMTP: fix 3489007, Sparrow AUTH PLAIN authentication support

### Caldav:
- Caldav: force context Timezone on Exchange 2010
- Caldav: add missing timezones from Exchange 2007 over WebDav
- Caldav: let users edit outgoing notifications for meeting requests
- Caldav: fix NullPointerException on addressbook request
- Caldav: workaround for broken items with \n as first line character

### POP:
- POP: add a new setting to mark messages read after RETR

### EWS:
- EWS: fix ErrorInvalidValueForProperty on search undeleted with Exchange 2010, set type Integer on PidLidImapDeleted and junk 0x1083
- EWS: new fix to improve failover on error retrieving MimeContent
- EWS: improve failover on error retrieving MimeContent
- EWS: Fix 3471671, workaround for Exchange invalid chars

### LDAP:
- LDAP: improve invalid dn message

### OSX:
- OSX: make nodock mode the default
- OSX: make sure davmail.jar is first in classpath

### DAV:
- Dav: set contact email type to SMTP
- Dav: add email type MAPI properties

### Carddav:
- Carddav: avoid NullPointerException on broken contact
- Carddav: fix regression on address book handling on Snow Leopard
- Carddav: decode urlcompname before search to retrieve contacts with & in url


## DavMail 3.9.7 2012-01-10
Another bugfix release with new stax based webdav search method implementation to reduce memory footprint with large folders,
exclude non event items from calendar to avoid errors, some EWS fixes on tasks handling and a few documentation updates

### WebDav:
- Dav: fix regression in new Stax implementation
- Dav: new stax based WebDav requests implementation to reduce memory usage, enabled on Search requests
- Dav: switch back to mailbox path on Exchange 2003 for CmdBasePath

### Caldav:
- Caldav: Experimental patch to support spaces in calendar or contacts path on OSX, see 3464086
- Caldav: Create a new davmail.caldavEnableLegacyTasks to allow access to tasks created in calendar folder by previous DavMail versions
- Caldav: drop davmail.caldavDisableTasks setting, retrieve only events from calendar
- Caldav: Change field update order for Exchange 2007 over EWS
- Caldav: apply date filter to tasks
- Caldav: new timezone for Mexico
- Caldav: fix 3433584, encode comma in LOCATION field

### IMAP:
- IMAP: fix double slash in folder path
- IMAP: return all search results uids on a single line for Wanderlust
- IMAP: new davmail.imapIncludeSpecialFolders setting to access all folders including calendar and tasks over IMAP
- IMAP: fix wanderlust support, allow lower case fetch params

### Documentation:
- Doc: Added DavMail hangs on 64-bit Linux FAQ entry
- Doc: add documentation for davmail.logFileSize option

### Enhancements:
- Change default use system proxies value to false
- Avoid NullPointerException on WebdavNotAvailableException
- Fix upload-version target site

### EWS:
- EWS: fix Exchange 2010 SP1 support
- EWS: use archivemsgfolderroot as archive root
- EWS: enable preemptive authentication on non NTLM endpoints
- EWS: add Exchange2010_SP1 support for online archive

### LDAP:
- LDAP: avoid NullPointerException during SASL authentication

### Carddav:
- Carddav: encode star in urlcompname


## DavMail 3.9.6 2011-10-30
Another bugfix release to improve iPad 2 and Debian based Linux support. 
Also includes new protocol mode options (EWS, WebDav or Auto), experimental Exchange online archive 
support, IMAP UTF-8 search parameter and many Caldav fixes

### Enhancements:
- Add a new upload-version ant target to upload version.txt
- Workaround for broken servers that send invalid Basic authentication challenge
- Add exchangecookie to the list of authentication cookies for direct EWS access
- Add a new auto value to davmail.enableEws setting to avoid unwanted switch from WebDav to EWS on temporary Exchange connection issue
- Encode # in urlcompname
- Fix bug on ITEM_PROPERTIES value on EWS/WebDav mode switch
- Add new Default button to reset log levels
- Implement a new option to let users disable all GUI notifications
- Additional exception trace exclusion 
- Revert 1.7 test on SWT, tray implementation is still broken on Linux

### Documentation:
- Doc: reformat urls in FAQ
- Doc: add a note to help users with broken Unity desktop manager on Ubuntu
- Doc: Fix typo in project description
- Doc: additional note on Caldav setup in Thunderbird and new external review
- Doc: document new disable balloon notifications setting
- Doc: Update roadmap
- Doc: New reviews
- Doc: Update iCal doc to match both Snow Leopard and Lion
- Doc: Update FAQ
- Doc: Update DavMail settings screenshot

### Linux:
- Allow openjdk-7-jre dependency in deb package 
- Fix 3418960: Update dependencies for Ubuntu 11.10, add libswt-gtk-3-java

### Caldav:
- Caldav: apply iCal 5 workaround to iOS 5
- Caldav: new timezone in rename table
- Caldav: try to merge Exchange 2010 and 2007 filters
- Caldav: additional unit tests
- Caldav: fix 3426148 decode and encode comma in RESOURCES field value
- Caldav: Fix complex timezones sent by clients, leave only latest STANDARD and DAYLIGHT definition
- Caldav: Fix 3420240, retrieve description from tasks over Dav

### EWS:
- EWS: add new DistinguishedFolderId value for Exchange archive support
- EWS: throw exception on 400 Bad request answer

### IMAP:
- IMAP: fix 3426383, implement CHARSET in SEARCH command, allow ASCII and UTF-8
- IMAP: fix 3353862, long file names encoding in BODYSTRUCTURE

### SMTP:
- SMTP: adjust workaround for misconfigured Exchange server that return 406 Not Acceptable on draft message creation, look inside multipart messages

## DavMail 3.9.5 2011-10-03
Bugfix release to avoid Growl plugin crash on OSX, make DavMail work with both
Snow Leopard and Lion. Also includes DIGEST-MD5 implementation for OSX Lion
Directory Utility support, however iCal attendee completion is still broken.

### OSX:
- Fix crash in Growl plugin on OSX: do not sent SSL content to Growl

### EWS:
- EWS: rebuild broken message (null MimeContent) from properties
- EWS: improve error logging on invalid character
- EWS: fix tasks field order, send Extended Properties first to match EWS schema
- EWS: replace extension before looking for items in task folder
- EWS: Fix 3407395, do not set mailbox on FolderIds returned by Exchange

### LDAP:
- LDAP: fix DIGEST-MD5 authentication and adjust dn context for OSX Lion, still experimental
- LDAP: fix DIGEST-MD5 SASL authentication for OSX Lion
- LDAP: experimental SASL DIGEST-MD5 implementation for OSX Lion Directory Utility support

### Caldav:
- Caldav: need to send principal-URL on principal path, only for OSX Lion
- Caldav: allow direct access to task folder

### Enhancements:
- Force toFront and requestFocus to bring windows to front
- Additional proxy selector logging
- Fixes from audit
- Upgrade Log4J to 1.2.16

## Documentation:
- Doc: Add saveInSent reference in FAQ

## DavMail 3.9.4 2011-09-13
First release with full Exchange tasks (VTODO) support, automatically convert VTODOs to tasks
inside default task folder. Also includes many bugfixes on iCal OSX Snow Leopard support
(note: need to recreate the calendar to fix sync), mixed Exchange 2010/2003 architecture support and
IMAP DRAFT and UNDRAFT search condition support

### Caldav:
- Caldav: fix regression
- Caldav: implement update folder
- Caldav: fix regression on Snow Leopard
- Caldav: more general fix for misconfigured Exchange server, replace host name in url also over Caldav
- Caldav: additional fix for CRLF in urlcompname
- Caldav: additional task fields over EWS and fix urlcompname decoding
- Caldav: implement task categories over EWS
- Caldav: update additional MAPI properties for tasks
- Caldav: implement startdate and duedate on tasks
- Caldav: implement task categories
- Caldav: Need to encode % in urlcompname
- Caldav: implement task percent complete and status over WebDav
- Caldav: improve task support over WebDav, rename .ics to .EML and implement priority (importance)
- Caldav: do not try to get ICS content from tasks
- Caldav: encode @ in path only for iCal 5 (OSX Lion)
- Caldav: implement supported-report-set

### Enhancements:
- Do not always log stacktrace in handleNetworkDown
- Fix IllegalArgumentException in fixClientHost when scheme is null
- Temporary fix: log exception stack trace in handleNetworkDown
- Temporary fix: log exception in handleNetworkDown
- Another init script
- Small failover fix
- Improve client host update fix
- Test various event count

### IMAP:
- IMAP: implement DRAFT and UNDRAFT search conditions, fix 3396248
- IMAP: fix failover for misconfigured Exchange server, replace host name in url
- IMAP: fix regression in EwsExchangeSession.createMessage
- IMAP: Fix 3383832, set ItemClass to send read receipt over EWS to avoid ErrorObjectTypeChanged 

### EWS:
- EWS: Allow null value in StringUtil.decodeUrlcompname
- EWS: use isrecurring with Exchange 2010 and instancetype with Exchange 2007
- EWS: revert 3317867 XML1.1 header workaround to fix 3385308

### DAV:
- Dav: check checkPublicFolder calls
- Dav: Avoid returning null in getCmdBasePath

### Documentation:
- Doc: fix OSX iCal setup documentation for OSX Lion

### LDAP:
- LDAP: send error on DIGEST-MD5 bind request

## DavMail 3.9.3 2011-07-31
New release with improved iCal 5 (OSX Lion) support, partial VTODO conversion to Outlook tasks
 and many other bugfixes including event move item url encoding and event filter over EWS fix

### Caldav:
- Caldav: new fix for @ encoding
- Caldav: fix regression, do not encode @ in calendar-user-address-set
- Caldav: fix principal-URL response
- Caldav: encode @ in current-user-principal
- Caldav: force @ encode to %40 for iCal 5
- Caldav: new CRLF in urlcompname patch for EWS, use _x000D__x000A_ as encoded value
- Caldav: implement merged folder ctag over WebDav
- Caldav: fix noneMatch handling over WebDav
- Caldav: implement tasks delete over WebDav
- Caldav: implement tasks support over WebDav
- Caldav: send principal-URL for OSX Lion
- Caldav: first duedate implementation on tasks, fix delete task and concat ctag to detect changes on both calendar and tasks folders
- Caldav: implement percent complete and status VTODO updates
- Caldav: implement task support over EWS
- Caldav: decode destination path on move
- Caldav: encode LF to %0A in urlcompname
- Caldav: check Depth before search
- Caldav: Task folder flag
- Caldav: move remove quotes for Evolution to EWS mode only
- Caldav: fix regression on iCal FreeBusy handling
- Caldav: implement PROPFIND on single item
- Caldav: remove quotes on etag for Evolution
- Caldav: first Task (VTODO) implementation step
- Caldav: allow infinity as Depth value

### Enhancements:
- Fix DoubleDotInputStream
- Improve system proxies and move item logging
- Refactor buildSessionInfo to use /public first and mailbox path as failover for galfind requests
- Fix bug in removeQuotes
- Fix 3315942, patch cleanup
- Fix server certificate label
- Fixes from audit
- Upgrade SWT to 3.7
- Fix 3315942, merge patch provided by Jeremiah Albrant: Ask user to select client certificate
- Improve message download progress logging, switch icon every 100KB
- Remove unused SwtAwtEventQueue class
- Implement davmail.smtpSaveInSent option and reorganize tabs
- Fix 3153691: Username with apostrophe
- Patch by Manuel Barkhau: exclude private events flag
- Reformat and fixes from audit

### EWS:
- EWS: new recurring event filter implementation, exclude recurrence exception in results
- EWS: fix new Exchange 2010 ItemId length support
- EWS: Fix for some Exchange 2010 ItemIds different length
- EWS: workaround for Exchange bug, replace xml 1.0 header with xml 1.1 and log message download progress
- EWS: implement gzip encoding on response

### DAV:
- Dav: update httpClient host after login

### IMAP:
- IMAP: need to include base folder in recursive search, except on root
- IMAP: Fix 3151800, force UTF-8 in appendEnvelopeHeaderValue

### Documentation:
- Doc: Add davmail.smtpSaveInSent description in doc


## DavMail 3.9.2 2011-06-07
This release includes some documentation updates, implement IMAP Recent flag, 
Caldav support enhancements, 64 bits wrapper on windows, hanging issue with SWT 
on Linux 64 and many other bugfixes.

### LDAP:
- LDAP: cancel search threads on connection close

### Enhancements:
- Adjust system proxy log statement
- Jsmooth patch with 64 bits skeletons
- Additional statement on proxy load
- SWT: register error handler early
- Serialize session creation in workstation mode to avoid multiple OTP requests
- SWT: register error handler to avoid application crash on concurrent X access from SWT and AWT
- Revert LookAndFeel changes, switch to System.setProperty to set default LAF
- SWT: make sure we don't start AWT threads too early
- Update Jsmooth patch with 64bits exe support
- SWT: delayed AWT frames creation to reduce memory usage
- Experimental 64 bits windows exe
- Add a log file size field in UI

### DAV:
- Dav: back to old path in Destination header behavior
- DAV: switch icon on large message download
- Dav: Log message download progress
- Dav: new patch to reset session timeout with a GET method on /owa/
- Dav: experimental, try to reset session timeout with a GET method
- Dav: do not try property update failover on 507 SC_INSUFFICIENT_STORAGE

### OSX:
- OSX: Avoid sending empty message to Growl

### IMAP:
- IMAP: test custom header search
- IMAP: workaround for Exchange 2003 search deleted support
- IMAP: fix 3303767, do not send line count for non text bodyparts
- IMAP: another fix for 3297849, ENVELOPE formating error/bogus quotes
- IMAP: fix 3297849, ENVELOPE formating error/bogus quotes
- IMAP: Fix nullpointer in broken message handling
- IMAP: fix infinite loop detection
- IMAP: detect infinite loop on the client side
- IMAP: implement Recent flag on new messages based on read flag and creation/modification date
- IMAP: fix 3223513 default flags on append

### Documentation:
- Doc: How to run multiple instances of DavMail
- Doc: FAQ note, iCal does not support folder names with spaces or special characters
- Doc: Add reference to default windows domain setting in FAQ
- Doc: additional Thunderbird and DavMail review
- Doc: add Duplicate messages in Sent folder FAQ entry
- Doc: add Piwik code to DavMail site
- Doc: New (french) review
- Doc: document custom certificate authority handling
- Doc: improve initial setup documentation
- Doc: describe the usual paths to use in OWA url field
- Doc: update imapAutoExpunge flag doc
- Doc: update roadmap
- Doc: document public folder access in Lightning

### Caldav:
- Caldav: allow tab as folding character, see RFC2445
- Caldav: Fix NullPointerException in getTimezoneIdFromExchange
- Caldav: instancetype is null on Exchange 2010, switch to isrecurring in EWS FindItem
- Caldav: Disable schedule-inbox for all Lightning versions
- Caldav: prepare xmoz custom property support over DAV

### EWS:
- EWS: fix UID and RECURRENCE-ID, broken at least on Exchange 2007 with recurring events
- EWS: fix 3105534 GetUserAvailability default timezone compatibility with Exchange 2010
- EWS: new failovers on Timezone settings: use davmail.timezoneId setting or default to GMT Standard Time


## DavMail 3.9.1 2011-03-22
Another bugfix release, mainly on EWS Caldav support (fix 404 not found).
Also implemented Microsoft Forefront Unified Access Gateway support.

### Documentation:
- Doc: additional FAQ entry on shared calendars

### Caldav:
- Caldav: first check that email address is valid to avoid InvalidSmtpAddress error on FreeBusy request and new timezone name mapping
- Caldav: New fix for fix 3190219, regression on quote encoding since 3165749 fix
- Caldav: rethrow SocketException to avoid event not available on client connection close or DavMail listener restart
- Caldav: Fix timezone name
- Caldav: fix 3190219, regression on quote encoding since 3165749 fix

## Exchange Web Services:
- EWS: fix 3190774, LDAP galfind email address handling, use Mailbox value instead of EmailAddress1/2/3
- EWS: fix NullPointerException in item getContent
- EWS: fix 404 not found with Exchange 2010 calendars

## Enhancements:
- Convert shell script to unix LF
- Implement Microsoft Forefront Unified Access Gateway logon form compatibility

### IMAP:
- IMAP: fix 3201374 envelope superflous space
- IMAP: fix LOGOUT implementation to improve SquirrelMail compatibility

### OSX:
- OSX: Avoid sending null message to Growl


## DavMail 3.9.0 2011-02-22
Making progress towards 4.0 and full EWS support, some issues remaining on recurring
events. This is mainly a bugfix release, with some Caldav enhancements, huge memory usage fix
on IMAP and a workaround for Linux 64 bits futex issue (deadlock on first connection).

### POP:
- POP: test new double dot implementation
- POP: Fix from Stefan Guggisberg, handle invalid CR or LF line feeds in DoubleDotOutputStream

### Caldav:
- Caldav: additional timezone names in table
- Caldav: 3132513, implement well-known url, see http://tools.ietf.org/html/draft-daboo-srv-caldav-10
- Caldav: implement a new setting to disable task (VTODO) support: davmail.caldavDisableTasks and probably exclude most broken events
- Caldav: throw exception on empty event body (EWS)
- Caldav: fix multivalued param support in VProperty and always quote CN values

### Documentation:
- Doc: Update release guide
- Doc: Additional FAQ entry on public calendar access with iCal
- Doc: Add Manchester wiki review

### LDAP:
- LDAP: dump BER content on error
- LDAP: fix 3166460, do not fail on NOT (0xa2) filter

### Bugfix:
- New workaround for bug 3168560, load system proxy settings in static block
- Fix 3161913 klauncher says davmail.desktop misses trailing semicolon
- Restore stax-api jar for Java 1.5 compatibility
- Fix 3150426 huge memory usage with IMAP
- Workaround for bug 3168560, synchronize system proxy access
- New NTLMv2 patch: provide fake workstation name and adjust Type3 message flags

### EWS:
- EWS: Fix 3165749, exception with quotes in meeting subject and EWS

### Webdav:
- Dav: log search response count


## DavMail 3.8.8 2011-01-11
Yet another bugfix release with many EWS support enhancements and fixes,
many documentation improvements (still need to update Thunderbird/OSX instructions
to latest versions though).

### Documentation:
- Doc: change Maven site plugin version
- Doc: additional external links
- Doc: Add anew reviews page
- Doc: Update POM and release guide
- Doc: move advanced settings to a separate page to keep getting started page simple
- Doc: add SWT bug reference to FAQ, on Ubuntu, notify text conflicts with default theme
- Doc: full iPhone setup instructions
- Doc: update war deployment description
- Doc: Additional smartcard PKCS11 setup instructions with NSS and Coolkey examples

### Caldav:
- Caldav: Additional timezone mappings
- Caldav: workaround for Exchange 2010 bug, \n in timezone name generates invalid ICS content
- Caldav: improve timezone rename error message
- Caldav: fix floating timezone in iCal: rename TZID for maximum iCal/iPhone compatibility
- Do not send Exchange 2003 appointment creation request to Exchange 2007

### EWS:
- EWS: return HttpNotFoundException on event not found to trigger Lightning workaround
- EWS: fix instancetype field definition: Integer instead of String (fix Caldav filter over EWS)
- EWS: improved email/alias failover fix
- EWS: fix regression in comment
- EWS: additional failover mail build on logon form failure
- EWS: store X-MOZ-SEND-INVITATIONS property to fix no notification issue with Lightning
- EWS: fix Caldav inbox handling over EWS
- EWS: improve timezone handling
- EWS: Update Field list
- EWS: fix 3098008, implement result paging to handle message folders with more than 1000 messages 
- EWS: exclude non message types from searchMessages
- EWS: fix email mapping on LDAP response
- EWS: add BusinessCountryOrRegion contact field

### Enhancements:
- Additional session create log statement
- New multiple user fields implementation: expect userid|username as login value
- Improve connection pool handling: do not pool simple checkConfig and getVersion connections.
- Implement OTP form with multiple username fields (username and userid)
- Contribution from Geert Stappers: start/stop script
- Improve NTLM authentication detection
- Always use private connection manager to avoid session conflict
- Fixes from audit
- Update javamail to 1.4.3
- Adjust Mime decoder settings (fix)
- Adjust Mime decoder settings
- Workaround for space in cookie name
- Use a_sLgnQS instead of a_sLgn first to support new OWA 8.3.83.4
- Additional NTLM flags to match Firefox flags
- Add UTF-7 support with jcharset
- Failover for misconfigured Exchange server, replace host name in url

### SMTP:
- SMTP: fix 3132569, always remove From header to avoid 403 error on send
- SMTP: workaround for misconfigured Exchange servers: failover on Draft message creation through properties. Warning: attachments are lost

### IMAP:
- IMAP: Fix 3137275 Imap header fetch bug

### WebDav:
- Dav: make sure Destination contains full url and not only path, may fix SMTP send and IMAP copyMessage on Exchange 2003

### Carddav
- Carddav: Update contact test

## DavMail 3.8.7 2010-11-24
Bugfix and performance release with new Woodstox parser to reduce memory
footprint in EWS mode, more Caldav broken events fixes and IMAP regression
fixes.

### Documentation:
- Doc: Update Carddav setup doc
- Doc: ssl setup doc update from kerstkonijn

### Enhancements:
- Unzip contribs content
- Update rpm ant task parameters to create valid rpm package
- Workaround for malformed cookies with space in name
- From Geert Stappers: add includeantruntime="false" to avoid ant 1.8 warning
- Workaround for invalid redirect location
- Improve error handling: detect redirect to reason=0 as session expired
- Suggestion from Geert Stappers: add svn:ignore property
- RPM spec from Marko Myllynen

### Caldav:
- Caldav: Fix timezone support with Exchange 2010 SP1
- Caldav: use rebuild event from MAPI properties failover in all error cases
- Caldav: add requestFocus() to bring notification dialog to foreground
- Caldav: added edit notifications checkbox in settings frame

### IMAP:
- IMAP: include current folder in recursive search
- IMAP: encode source path in copyMessage
- IMAP: new test case to show Thunderbird perf issue
- IMAP: Fix 3109303 Handle null string during mail fetch
- IMAP: fix nullpointerException in header fetch
- IMAP: fix 3106803, IMAP client stuck scanning Inbox, fix header and body fetch in same request
- IMAP: throw error on 440 Login Timeout to avoid message corruption

### LDAP:
- LDAP: do not log error on OSX groups request

### EWS:
- EWS: Upgrade woodstox version to use enhanced base64 conversion (reduced memory usage)
- EWS: allow autodiscover after authentication failure
- EWS: fix contact email update

### OSX:
- OSX: search and replace on existing file, spotted by Geert Stappers

## DavMail 3.8.6 2010-11-07
First release with automatic EWS mode detection, also includes many bugfixes
on LDAP support over EWS, IMAP enhancements, Exchange 2010 SP1 cookie bug workaround
and a brand new UI frame to let users edit Caldav notifications.

### LDAP:
- LDAP: fix galfind search: add uid in response and use cn in fullsearch filter
- LDAP: additional EWS attributes
- LDAP: additional attributes for iPad

### Enhancements:
- Fix 3103349: Cannot login if display name contains [brackets], regression after first patch
- Fix 3103349: Cannot login if display name contains [brackets]
- configFilePath is null in some test cases
- Added passcode as token field for RSA support
- Add DavMail version in welcome IMAP and SMTP header
- Update test case
- Handle exceptions on invalid UTF-8 characters or unexpected content triggered by XmlStreamReader.getElementText (based on patch 3081264)
- Add exchange 2010 PBack cookie in compatibility mode
- Novell iChain workaround

### POP:
- POP: add version in welcome banner

### Caldav:
- Caldav: Fix bug in Dav mode with broken events dtstart -> dtend
- Caldav: fix french notification message
- Caldav: protect ':' in VCALENDAR property params
- Caldav: initial edit notification implementation
- Caldav: Create fake DTEND on broken event
- Caldav: fix nullpointer in VCalendar on missing DTEND
- Caldav: implement main calendar folder rename
- Caldav: use i18n calendar name as display name for iCal
- Caldav: avoid renaming default calendar to null

### EWS:
- Ews: improve ResolveNames implementation, parse addresses and phone attributes
- EWS: implement failover on OWA authentication failure (e.g. with outlook.com)
- Ews: improve invalid item in calendar error handling
- EWS: improve resolveNames logging
- EWS: add enableEws flag in UI settings frame
- EWS: automatically detect Webdav not available and set davmail.enableEws flag

### IMAP:
- IMAP: failover in message copy on 404 not found
- IMAP: Fix append with no optional parameters
- IMAP: additional test cases
- IMAP: fix from kolos_dm: implement fake line count in BODYSTRUCTURE and [] block in IMAPTokenizer
- IMAP: fix from kolos_dm: implement attachment name in BODYSTRUCTURE
- IMAP: improve logging, do not log message content on 404 or 403
- IMAP: fix from kolos_dm: In-Reply-To is not email header and unfold header to remove CRLF in ENVELOPE response
- IMAP: merge fix from Kolos, search command with message sequence set
- IMAP: implement index (non uid) COPY
- IMAP: workaround for broken message (500 error), rebuild mime message from properties
- IMAP: send error on idle command without selected folder (Outlook)

### Documentation:
- Doc: fixes and updates on ssl setup and build
- Doc: update roadmap
- Doc: Update architecture image
- Doc: update ssl server certificate doc
- Doc: Document PKCS12 self signed certificate creation to enable SSL in DavMail
- Doc: iPhone screenshots

### SWT:
- SWT: Custom AWT event queue to trap X errors and avoid application crash
- SWT:enable debug mode

## DavMail 3.8.5 2010-09-27
Includes much progress on Caldav over EWS support, a few regression fixes 
and improved IMAP BODYSTRUCTURE implementation for complex messages.

### Bugfixes:
- Fix regression in Exchange 2007 over Dav session

### Enhancements:
- Detect and submit language selection automatically
- More fixes from audit
- Fixes from audit
- Restore cookies on error
- Improve buildSessionInfo failover
- Fix ssl trustmanager error handling
- Enable Webdav/Galfind failover on Exchange 2007
- Workaround for basic authentication on /exchange and form based authentication at /owa

### Caldav:
- Caldav: detect invalid events with empty dtstart property
- Caldav: implement mozilla alarm flags X-MOZ-LASTACK and X-MOZ-SNOOZE-TIME over EWS
- Caldav: EWS, rebuild attendee list from properties
- Caldav: test principal request
- Caldav: fix 3067915 getRangeCondition too restrictive
- Caldav: implememnt Busy flag over EWS and refactor create code
- Caldav: fix create allday event over EWS and check if current user is organizer
- Caldav: Fixed regression in allday event handling
- Caldav: improve EWS implementation
- Caldav: improve timezone error handling
- Caldav: remove empty properties
- Caldav: avoid invalid X-CALENDARSERVER-ACCESS and CLASS
- Caldav: avoid empty X-CALENDARSERVER-ACCESS and CLASS
- Caldav: reinsert the deleteBroken check
- Caldav: fix VProperty parser
- Caldav: additional VCalendar properties for rebuilt item: VALARM (reminder)
- Caldav: additional VCalendar properties for rebuilt item: RRULE, EXDATE, CLASS
- Caldav: failover for broken event, rebuild VCalendar content from raw properties
- Caldav: fix 3063407, regression in sendPrincipal

### Carddav:
- Carddav: fix null value in email address
- Carddav: fix email address handling over EWS

### Exchange Web Services:
- EWS: fix 3047563 double inbox
- EWS: more caldav ews fixes

### SMTP:
- SMTP: rewrite getAllRecipients to disable strict header check
- SMTP: new try at encoding fix: set mailOverrideFormat and messageFormat

### Documentation:
- Upgrade maven site-plugin and update release guide

### IMAP:
- IMAP: fix 3072497 Imap server too picky about case
- IMAP: improve BODYSTRUCTURE implementation, make it recursive
- IMAP: implement partial header fetch

### LDAP:
- LDAP: new attribute mapping
- LDAP: cache current hostname value in sendComputerContext to improve iCal address completion performance
- LDAP: additional ignore attributes
- LDAP: add gidnumber to attribute ignore list
- LDAP: fix regression on iCal 3 search completion

### SWT:
- SWT: allow libswt-gtk-3.6-java on debian, available from ppa:aelmahmoudy/ppa

## DavMail 3.8.4 2010-09-08
Yet another bugfix release with more regressions fixes on SMTP,
a few LDAP fixes and a caldav timezone update. 

### Documentation:
- Doc: Update release guide
- Doc: Update swt version in maven pom

### SMTP:
- SMTP: try to force IMS encoding mode according to message contenttype
- SMTP: switching back to Draft then send mode over DAV for calendar messages
- SMTP: switching back to Draft then send mode over DAV
- SMTP: new duplicate message-id detection implementation, no need to search Sent folder

## LDAP:
- LDAP: improve EWS filter support
- LDAP: another gallookup detection fix to improve address completion in thunderbird

### Carddav:
- Carddav: improve OSX client detection

## Enhancements:
- Fixes from audit

### Caldav:
- Caldav: accept login as alias in caldav principals path
- Caldav: basic move item implementation
- Caldav: adjust Lightning bug workaround
- Caldav: yet another timezone fix, adjust Outlook created event time before allday conversion
- Caldav: fix regression on meeting response subject

## DavMail 3.8.3 2010-09-02
Another bugfix release with major regressions fixed:
missing calendar meeting messages and delivery status notification on
some external addresses. Also includes improved autodiscover support.

### Enhancements:
- Disable broken rpm generation
- Fix test cases
- Upgrade swt to 3.6
- workaround for TLS Renegotiation issue, 
  see http://java.sun.com/javase/javaseforbusiness/docs/TLSReadme.html    
- Switch back to StreamReader.next instead of nextTag
- Fix autodiscover support
- Merge patch 3053324: Implement per service SSL flag (patch provided by scairt)
- Fix XMLStreamUtil regression
- Refactor XMLStreamUtil

### Exchange Web Services:
- EWS: improve autodiscover implementation
- EWS: fix possible NullPonterException
- EWS: implement autodiscover to find actual EWS endpoint url

### Caldav:
- Caldav: extend Lightning broken tests to all 1.* versions
- Caldav: switch back to contentclass to get calendarmessages over webdav
- Caldav : revert previous changes and fix meeting cancel support (IPM.Schedule.Meeting.Canceled)
- Caldav: move to trash on processItem
- Caldav: fix request parser regression on nextTag
- Caldav: improve filter handling, support VTODO/VEVENT comp-filter
- Caldav: make timezone name retrieval more robust

### SMTP:
- SMTP: make duplicates check optional with davmail.smtpCheckDuplicates setting
- SMTP: always remove From header with Exchange 2007 and 2010
- SMTP: Improve message on MAIL FROM without authentication
- SMTP: experimental, advertise 8BITMIME

### IMAP:
- IMAP: implement shared mailbox access

### Documentation:
- minor doc fix
- Doc: Additional Exchange Webdav setup documentation
- Add ohloh widget on home page
- Doc: a few doc fixes and update roadmap

## DavMail 3.8.2 2010-08-25
Bugfix release with improved Exchange 2010 IMAP support, CardDav fixes and
improved error handling

### Enhancements:
- Disable SWT on Java 7
- Update debian package description and categories
- fix 2995990: Add support for already authenticated users
- Fix missing hide password in log over IMAP
- More session creation enhancements, fix public folder test when /public is 403
- Refactor email and alias retrieval: always use options page with Exchange 2007
- Improve socket closed error handling
- Try default form url on authentication form not found
- Add Java Service Wrapper contribution from Dustin Hawkins

### Caldav:
- Caldav: move delete broken event logic to DavExchangeSession
- Caldav: delete broken events when davmail.deleteBroken is true
- Caldav: improve event logging, include subject

### IMAP:
- IMAP: handle 507 InsufficientStorage error
- IMAP: fix regression in NOT DELETED filter

### Documentation:
- Doc: Update OSX directory setup documentation

### DAV:
- DAV: Encode apos in urlcompname used in DAV search request

### EWS:
- EWS: fix single message in folder with Exchange 2010 bug
- EWS: implement loadVTimezone for Exchange 2010

### SMTP:
- SMTP: fix regression on bcc handling
- SMTP: convert Resent- headers, see 3019708

### LDAP:
- LDAP: avoid galLookup in iCal searches

### Carddav:
- Carddav: Fix email update over EWS

## DavMail 3.8.1 2010-08-18
Includes a full refactoring of Vcalendar content handling, much progress on
Exchange Web Services support, LDAP optimizations and many other bufixes.

### Exchange Web Services:
- EWS: hard method: delete/create on update
- EWS: Fix DeleteItem for CalendarItem
- EWS: implement loadVtimezone, get user timezone id from OWA settings
- EWS: Fix FieldURIOrConstant test
- EWS: separate domain from userName in NTLM mode
- EWS: MultiCondition galFind
- EWS: implement basic galFind search
- EWS: implement resolvenames response parsing
- EWS: fix subfolder search on Exchange 2010
- EWS: implement user availability (freebusy) and shared folder access
- EWS: implement sendEvent
- EWS: force urlcompname only on create
- EWS: implement ResolveNames method
- EWS: Apply workaround to events
- EWS: workaround for missing urlcompname on Exchange 2010, use encoded ItemId instead
- EWS: rename equals to isEqualTo and format search date
- EWS: dynamic version detection
- EWS: Exchange 2010 message handling
- EWS: Exchange 2010 folder handling
- EWS: Exchange 2010 compatibility: add test cookie, access /ews/exchange.asmx endpoint

### Caldav:
- Caldav: Fix missing TZID in DTSTART from iPhone
- Caldav: return reoccuring events on time-range request
- Caldav: Fix METHOD on create from iPhone
- Caldav: need to encode colon (:) in urlcompname search, implement a last failover on item search
- Caldav: implement 2899430, change the subject line when replying to invites
- Caldav: workaround for Lightning 1.0b2 bug
- Caldav: disable caldav inbox with Lightning 1.0b2
- Caldav: fix regression in fixVCalendar (missing organizer)
- Caldav: skip empty lines
- Caldav: Fix regressions in Vcalendar handling
- Caldav: fix nullpointer in VCalendar
- Caldav: fix regressions and do not filter on outlookmessageclass
- Caldav: major refactoring of event content handling and notifications
- Caldav: switch to new VCalendar parser/patcher
- Caldav: implement VALARM in VCalendar
- Caldav: more vcalendar patches
- Caldav: start new VCalendar fixICS implementation
- Caldav: call fixICS on download
- Caldav: reenable Lightning 1.0b2 bug workaround
- Caldav: failover for 404 not found on items containing '+' in url, search item by urlcompname to get permanenturl

### LDAP:
- LDAP: create a separate thread only for person/contact searches
- LDAP: implement galFind MultiCondition over webdav and improve search by mail
- LDAP: need to galLookup when search attribute is not in galfind result
- LDAP: another search attribute mapping fix
- LDAP: code cleanup and some galfind search fixes
- LDAP: fix 3043659, include entries starting with Z
- LDAP: Improve sizeLimit handling and ignore attributes
- LDAP: a few more attribute fixes
- LDAP: move galLookup to DavExchangeSession
- LDAP: progress on EWS LDAP implementation and refactoring
- LDAP: fix regression on OSX directory request on iCal start: filter invalid imapUid condition
- LDAP: use sizeLimit in contactFind
- LDAP: Fix OSX directory search on uid

### Enhancements:
- Improve error handling
- Add custom cookie policy to support extended host name
- Fixes from audit

### Bugfixes:
- Fix regression in getAliasFromMailboxDisplayName
- Deb: Fix regression in debian desktop link

### DAV:
- Dav: disable galFind on error

### SMTP:
- SMTP: compare actual email address, not email with alias
- SMTP: no need to remove From header with new sendMessage implementation

### SWT:
- SWT: fix 2992428, hide instead of dispose on close

### Carddav:
- Carddav: refactor VCard handling to merge with VCalendar code
- Carddav: disable contact picture handling on Exchange 2007
- Carddav: implement range search

## DavMail 3.8.0b2 2010-07-26
Fixes the most obvious regressions in 3.8.0b1 and some documentation
updates on Carddav. Note for EWS only users: add davmail.enableEws=true in
davmail.properties

### Caldav:
Caldav: fix sendEvent regression, conflict on outbox notifications
Caldav: improve HttpNotFound message
Caldav: Refactor getItem
Caldav: fix MKCALENDAR http status code: return 201 instead of 207
Caldav: Another request parsing bug: handle empty elements
Caldav: fix regression in REPORT requests parsing

### Carddav:
Carddav: additional TEL properties
Carddav: add fburl field

### Documentation
Doc: fix carddav thunderbird doc
Doc: update left menu
Doc: set source encoding to UTF-8 in maven pom
Doc: update roadmap
Doc: Basic OSX setup instructions
Doc: thunderbird carddav setup with SOGO connector
Doc: Update homepage and project description

## DavMail 3.8.0b1 2010-07-25
First public release after major refactoring to implement Exchange 2010 and Exchange 2007 without
Webdav support. This implementation is based on Exchange Web Services. EWS support is not yet
complete: global address list search and free/busy support is missing.
This release includes the new Carddav service sponsored by French Defense / DGA through 
project Trustedbird. OSX notifications will now use Growl if available.

### Carddav:
- Carddav: another urlcompname encoding fix
- Carddav: generate OSX compatible VCARD photo and change addressbook-home-set with OSX Address Book
- Carddav: use new ExchangePropPatchMethod in full contact create/update
- CardDav: use new ExchangePropPatchMethod to create haspicture boolean property
- Carddav: improve error logging on photo update failure
- Carddav: use email1 as default email on update
- Carddav: fix multiple mail MAPI properties handling
- Carddav: fix GET request on folder support for SOGO
- Carddav: encode contact picture url
- Carddav: return 404 not found on missing folder
- Carddav: fix line folding in generated VCARD
- Carddav: Fix regression in single value multiline properties
- Carddav: add gender property
- Carddav: adjust bday to timezone
- Carddav: another anniversary property candidate
- Carddav: Add Anniversary support
- Carddav: Fix bday generation
- Carddav: fix iPhone BDAY parser
- Carddav: adjust fields accepting multiple values
- Carddav: fix semicolon encoding in compound value
- Carddav: workaround for iPhone categories encoding
- CardDav: do not encode simple (not compound) properties
- Carddav: fix regression in VCardWriter
- Carddav: always encode values
- Carddav: protect semicolon
- Carddav: iPhone personalHomePage support
- Carddav: ignore key prefix in VCARD
- Carddav: resize contact picture
- Carddav: Fix lower case param names
- Carddav: add contact create or update log statement
- Carddav: handle param values as parameter list
- Carddav: encode photo href
- Carddav: fix regression on VCARD photo detection
- Carddav: use urlcompname value instead of path to get contact details
- Carddav: fix case insensitive param values
- Carddav: add haspicture to test case
- Carddav: Implement picture delete and private flag over EWS
- Carddav: handle picture delete
- Carddav: fix boolean field handling
- Carddav: Remove missing properties on update
- Carddav: implement CLASS (private) flag
- Carddav: convert image to jpeg over EWS
- Carddav: implement photo update over WebDav
- Carddav: implement photo handling over EWS
- Carddav: implement categories support in EWS mode
- Carddav: implement categories
- Carddav: get SMTP email address
- Carddav: move value decoding back to VCardReader
- Carddav: decode multiline values
- Carddav: encode comma and \n in values
- CardDav: make getContactPhoto more robust
- Carddav: iPhone iOS4 compatibility
- Carddav: implement contact photo support (readonly)
- Carddav: implement quoted param value support
- Carddav: bday, assistant, manager and spouse properties
- Carddav: other address and homeposteofficebox properties
- Carddav: instant messaging and role properties
- Carddav: more properties
- Carddav: Implement phone, address and email properties
- Carddav: handle multiple values on a single line and add new properties
- CardDav: fix contact folder path handling and add create contact unit test
- Carddav: refactor Contact creation and create VCardReader
- CardDav: move Contact getBody to ExchangeSession and add more attributes support
- CardDav: map contact fields
- CardDav: improve automatic address book setup for OSX
- CardDav: implement OSX AddressBook requests: current-user-privilege-set property, current-user-principal on root request, addressbook-home-set on principal request, addressbook-multiget REPORT request with address-data response, urn:ietf:params:xml:ns:carddav namespace

### Enhancement:
- Disable preemptive authentication when adding NTLM scheme
- Fixes from audit
- Force log file encoding to UTF-8
- Add new davmail.logFileSize setting
- Use linux friendly path separator in jsmooth config files
- Fixes from audit
- Major refactoring: use straight inpustream instead of reader everywhere
- Disable ConsoleAppender in gui mode
- Add missing Junit jar
- Cleanup: System.setProperty of httpclient.useragent no longer needed
- Improve item not found logging
- Log gateway stop at info level
- Improve empty keystore password handling to avoid NullPointerException
- Fix 2999717 redirect console to /dev/null in desktop file

### Exchange Web Services:
- EWS: fix urlcompname encoding issues
- EWS: fix folder name ampersand encoding issue
- EWS: return 403 forbidden on ErrorAccessDenied
- EWS: xml encode values
- EWS: use UTF-8 to decode request on error
- EWS: send extended properties first on update
- EWS: format datereceived date
- EWS: fix bug in UnindexedFieldURI
- EWS: update createMessage bcc handling to match sendMessage 
- EWS: implement bcc support in sendMessage
- EWS: implement send message (SMTP)
- EWS: fixes from audit
- EWS: fix CalendarItem creation, no need to wrap ics in a MIME message
- EWS: implement calendar event create or update, processed field, subfolder path handling 
- EWS: fix internaldate conversion
- EWS: convert read flag to boolean and noneMatch/etag to detect create or update on items
- EWS: use UnindexedFieldURI for read flag
- EWS: fixes for Caldav and Carddav compatibility
- EWS: fix folder id regression
- EWS: fix country contact property mapping
- EWS: implement getItem and various contact handling fixes
- EWS: map all contact properties
- EWS: implement more contact and event methods
- EWS: implement copy method
- EWS: datereceived flag support
- EWS: handle bcc field
- EWS: various flag handling fixes, implement message delete
- EWS: implement getContent
- EWS: fix iconIndex flag property
- EWS: implement create and update message
- EWS: fix single value in MultiCondition handling
- EWS: rely on uid (PR_SEARCH_KEY) instead of permanentUrl to detect imap uid changes
- EWS: implement searchMessages
- EWS: fix bug in MultiCondition search
- EWS: fix from audit
- EWS: implement folder handling, including the new MoveFolderMethod
- EWS: move mailbox folder urls to DavExchangeSession
- EWS: use searchContacts in contactFind
- EWS: fix regression in deleted flag handling
- EWS: refactor contactFind, use new Condition API
- EWS: still more WebDav code to DavExchangeSession
- EWS: move more WebDav code to DavExchangeSession
- EWS: Various fixes after refactoring on DASL request generation
- EWS: in progress refactoring of contacts and events handling
- EWS: implement folder ctag, remove deprecated foldername property
- EWS: move WebDav message write and delete to DavExchangeSession
- EWS: move WebDav code to DavExchangeSession
- EWS: refactor IMAP search, use Conditions classes instead of string search filder
- EWS: Use int values to create ExtendedFieldURI propertyTags
- EWS: map folder path to and from IMAP
- EWS: implement NotCondition and public folder access
- EWS: implement IndexedFieldURI and InternetMessageHeader
- EWS: refactor search to use classes instead of String filters
- EWS: implement MultipleOperandBooleanExpression (And, Or, Not conditions)
- EWS: refactor folder search, create abstract getFolder methods
- EWS: start ExchangeSession refactoring to extract Dav calls
- EWS: refactor options, use enums
- EWS: implement basic SearchExpression restriction
- EWS: Implement CreateFolder, DeleteFolder and CreateItem, refactor options
- EWS: retrieve and decode MIME content
- EWS: add standard field additional property, implement IncludeMimeContent in GetItem, add DeleteItemMethod
- EWS: Generic item property mapping
- EWS: refactor EWS code
- EWS: experimental HttpClient based EWS methods

### Caldav:
- Caldav: fix time-range filter support in EWS mode
- Caldav: move calendar on displayname update
- Caldav: partial MKCALENDAR implementation
- Caldav: implement time-range request
- Caldav: add missing dtstart field
- Caldav: improve 404 error handling
- Caldav: fix regression in processItem
- Caldav: UTF-8 encode report body
- Caldav: catch any exception in reportItems
- Caldav: Process request before sending response to avoid sending headers twice on error
- Caldav: Workaround for Lightning/1.0b2 href encoding bug in REPORT requests
- Caldav: move processItem logic back to CaldavConnection
- Caldav: Workaround for emClient broken href encoding
- Caldav: remove buildCalendarPath method
- Caldav: allows mixed case contentType in event MIME message (fix Unable to get event error)
- Caldav: fix 3014204 missing timezone
- Caldav: fix 2902372 private flag handling undex iCal 4 (OSX 10.6 Snow Leopard)
- Caldav: send current-user-principal on principals folder for iCal
- Caldav: workaround for iCal bug: do not notify if reply explicitly not requested
- Caldav: add CRLF after END:VCALENDAR to comply with RFC
- Caldav: fix regression in getItem, allow urn:content-classes:calendarmessage contentClass
- Caldav: Fix Carddav etag handling (additional Head request) and implement card delete
- Caldav: Implement Carddav create (only a few attributes mapped)
- Caldav: Implement basic Carddav search requests

### DAV:
- Dav: more property update fixes
- Dav: patch filter on invalid Exchange Webdav response
- Dav: new ExchangePropPatchMethod to handle custom exchange propertyupdate and invalid response tag names
- Dav: refactor getContentReader and fix regression on null date value
- Dav: fix nullpointer in DavExchangeSession
- Dav: handle null properties with new createMessage
- Dav: another datereceived fix
- Dav: switch back to DAV:uid, used mainly in POP service (case sensitive)
- Dav: fix bug 3022451 in new search filter implementation with empty sub conditions
- Dav: Add folder unit tests
- Dav: add private and sensitivity fields
- Dav: implement timezone mapping for Exchange 2007, should fix the allday issue with Outlook
- Dav: use search expression to request ishidden
- Dav: fix regression in deleteItem
- Dav: fix regression 3020385 on folder handling
- Dav: Refactor folder search to use searchItems
- Dav: use Email1EmailAddress mapi property to get mail attribute, add uid attribute
- Dav: fix from audit
- Dav: add unit tests, move buildCalendarPath logic to getFolderPath

## Bug fixes:
- Use private MultiThreadedHttpConnectionManager with NTLM to avoid persistent authentication on connection issues
- Fix regression in AbstractConnection: return null instead of empty string on closed connection
- Fix 3001579: improve NTLM support

### IMAP:
- IMAP: add uidNext MAPI property (not available under Exchange 2003)
- IMAP: fix deleted flag handling over Webdav
- IMAP: fix flag handling in createMessage
- IMAP: new seen flag test case
- IMAP: fix regression on imap uid restore
- IMAP: fix 3023386, support BODY.PEEK[1.MIME] partial fetch
- IMAP: new unit tests and fix $Forwarded flag removal
- IMAP: implement deleted/undeleted search as condition instead of post filter
- IMAP: add IMAP unit test
- IMAP: fix 3014787 remove property over WebDav
- IMAP: implement last message (simple *) fetch range
- IMAP: send required "* SEARCH" on empty search response
- IMAP: Add a new hidden davmail.deleteBroken setting to delete broken messages
- IMAP: implement a new imapAutoExpunge setting to delete messages immediately over IMAP

### SMTP:
- SMTP: send message directly without creating a Draft message to preserve Message-id
- SMTP: fix log message
- SMTP: fix 3024482, avoid duplicate messages with gmail
- SMTP: Fix DoubleDotInputStream pushback size
- SMTP: last CRLF is not included in message content

### Documentation:
- Doc: javadoc and code cleanup
- Doc: fix default domain label
- Doc: new FAQ entry on OSX auto start "Login Items"
- Doc: typos fixes from Raphael Fairise
- Doc: update release guide
- Doc: add a new mail.strictly_mime FAQ entry to enable quoted-printable

### POP:
- POP: fix regression in TOP command
- POP: fix message termination, append CRLF only when necessary
- POP: replace deprecated write method, use DoubleDotOutputStream instead
- POP: allow space in username

## LDAP:
- LDAP: fix contact attributes reverse mapping
- LDAP: improve contact attribute mapping and add a few new properties
- LDAP: fix attribute map
- LDAP: fix regression after EWS refactoring
- LDAP: use imap uid as ldap uid
- LDAP: use PR_SEARCH_KEY instead of DAV:uid as uid string

### OSX:
- Exclude growl from non OSX packages
- Fix growl build project name
- OSX: implement growl support
- include jnilib in OSX package
- libgrowl-0.2 with libgrowl.jnilib compiled on OSX Snow Leopard
- set libgrowl version to 0.2
- rename generated jar with version, exclude test classes and create Manifest with Michael Stringer author
- Improve Growl exception handling, remove System.out and a few fixes from audit
- Initial growl import from http://forums.cocoaforge.com/viewtopic.php?f=6&t=17320

## DavMail 3.6.6 2010-05-04
This release is mainly focused on IMAP enhancements, including IDLE (RFC2177)
aka "Push Mail" support and other protocol compliance fixes, particularly on
partial fetch. NTLMv2 is also supported thanks to the JCIFS library.

### Documentation:
- Doc: update doc and roadmap
- Doc: adjust settings message
- Doc: improve server/client certificates description
- Doc: new FAQ entry on message deleted over IMAP still visible through OWA
- Doc: fix maven site generation

### IMAP:
- IMAP: send BAD instead of BYE on exception
- IMAP: fix 2992976, implement complex index and uid range in SEARCH
- IMAP: Handle exception during IDLE
- IMAP: add a new setting to enable/disable IDLE
- IMAP: use getRawInputStream instead of writeTo to avoid MIME message changes, cache message body in SharedByteArrayInputStream
- IMAP: poll folder every 30 seconds in IDLE mode, clear cached message
- IMAP: implement IDLE extension (RFC2177)
- IMAP: fix 2971184, do not decode content in partial fetch (replace getDataHandler with PartOutputStream)

### Enhancements:
- Exclude redline lib from distribution packages
- Use https in default Exchange url
- Make sure log messages are not localized
- Remove unused messageId field
- Do not shutdown connection manager on restart
- Allow Exchange server to use gzip compression
- Sample SocketAppender configuration
- Improve NTLM mode detection
- JCIFS based NTLMv2 implementation
- Hardcode /owa/ path in getAliasFromOptions and getEmailFromOptions for Exchange 2007, improve failure message
- Improve xmlEncode, use compiled static patterns

### Caldav:
- Caldav: fix 2992811, missing timezones
- Caldav: fix 2991030 tasks disappeared
- Caldav: add VTODO to supported-calendar-component-set response
- Caldav: fix regression in getAllDayLine()
- Caldav: make shared calendar test case insensitive
- Caldav: 0 or no value in caldavPastDelay means no limit

## DavMail 3.6.5 2010-04-13
This release includes a major refactoring of the IMAP FETCH implementation
to improve performance and provide RFC compliant partial fetch. The Carddav
support sponsored by french DGA through project TrustedBird is now included
in the roadmap. Private events filter on shared calendar is also available
and DavMail can now retrieve proxy settings directly from system configuration.

### SMTP:
- SMTP: implement AUTH LOGIN username (with optional initial-response, see RFC2554)

### IMAP:
- IMAP: Keep a single message in MessageList cache to handle chunked fetch, reenable maxSize in ImapConnection.
- IMAP: implement subparts partial fetch
- IMAP: Fix message write, double dot only for POP, not IMAP
- IMAP: Do not advertise not yet supported custom flags
- IMAP: fix from audit
- IMAP: major FETCH implementation refactoring, make code simpler and more efficient
- IMAP: add BODY.PEEK[index] support
- IMAP: improve partial fetch support
- IMAP: fix 2962071, quote folder names in STATUS response
- IMAP: allow partial part fetch
- IMAP: fix regression on unknown parameter handling
- IMAP: implement part fetch (BODY[1]) 
- IMAP: detect unsupported parameter
- IMAP: fix 2973213, escape quotes in subject
- IMAP: fixes to improve JavaMail support

### Doc:
- Doc: move CardDav reference before architecture schema
- Doc: update project description in Maven pom and ant package
- Doc: update project description and RoadMap, announce CardDav support sponsored by french DGA through project Trustedbird
- Doc: update roadmap

### Enhancements:
- Add a new setting to disable startup notification window (contribution from jsquyres)
- Improve getAliasFromOptions to retrieve alias with custom dn
- Workaround for NTLM authentication only on /public
- Add a new setting to retrieve proxies from system configuration
- Fix empty setting behavior: return null instead of empty string
- Sort properties file
- Fix new RPM ant task definition
- Improve public folder url check
- Experimental rpm package build

### Carddav:
- Carddav: refactor folder handling code to prepart CardDav support

### Caldav:
- Caldav: fix broken inbox, missing instancetype in search request and add is null in search query
- Caldav: do not try to access inbox on shared calendar (to avoid 440 login timeout errors and session reset)
- Caldav: exclude private events on shared or public calendar PROPFIND
- Caldav: fix regression on invalid events handling, just warn on broken events
- Caldav: drop timezone when converting allday events to client

## DavMail 3.6.4 2010-02-21
Well, yet another bugfix release, with improved IMAP support,
SMTP enhancements to support Eudora, NTLM proxy authentication
support and other bug fixes

### SMTP:
- SMTP: fix 2953552, allow RSET in AUTHENTICATED state
- SMTP: bug id 2953554, implement NOOP

## LDAP:
- LDAP: Enable tray icon on LDAP connection

## Bug fixes:
- Fix regression in 3.6.3: basic authentication broken in checkConfig
- GUI: Fix client certificate setting switch

### Enhancements:
- Change debian package dependence to accept openjdk-6-jre and libswt-gtk-3.5-java
- Fix from audit
- Improve log message on HTTP header error
- Implement NTLM HTTP proxy support
- Improve logging of expired sessions
- Support multiple forms in form based authentication logon page
- Catch error on SWT exit
- Enable NTLM on Proxy-Authenticate return code with only NTLM available

### Documentation:
- Doc: Document davmailservice.exe usage
- Doc: Document Force ActiveSync setting in Getting Started
- Doc: Add an FAQ entry on DavMail settings location
- Doc: Update release notes and guide

### IMAP:
- IMAP: new patch from Gellule to fix disappearing messages issue
- IMAP: rethrow SocketException after error in handleFetch

### Caldav:
- Caldav: new fix for invalid events
- Caldav: add a hidden davmail.caldavDisableInbox to allow users to disable Caldav Inbox with Thunderbird 3 and Lightning
- Caldav: improve broken events logging
- Caldav: Follow redirects on GET with permanentUrl


## DavMail 3.6.3 2010-01-24
Another bugfix release, mostly documentation updates, some regressions
in 3.6.2 in error handling fixed, a new IMAP workaround to completely
hide the uid change issue, emacs IMAP support and new UI settings for
previously hidden parameters.

### Bug fix:
- Fix logging settings handling in webapp mode

### Enhancements:
- Improve error handling: detect SocketException to avoid client socket closed errors
- Implement file based (PKCS12 and JKS) client certificates in addition to smartcard support

### Documentation:
- Doc: update roadmap
- Doc: remove replace token and search page
- Doc: added Gellule as Java Contributor
- Doc: add a security section in the FAQ
- Doc: update FAQ with Exchange prerequisites details
- Document client keystore file settings

### IMAP:
- IMAP: brand new IMAP uid workaround and refresh folder on Expunge from Gellule
- IMAP: implement LIST "" "*%" for emacs
- IMAP: another fix for the message uid bump issue
- IMAP: fix 2934922, implement (NOT DELETED) in search filter
- IMAP: extend thunderbird changed uid workaround to all contexts

### GUI:
- Add new setting davmail.defaultDomain to set default windows domain
- Prepare new advanced options

### Caldav:
- Caldav: add davmail.forceActiveSyncUpdate option to the settings frame
- Caldav: add davmail.caldavAlarmSound option to the settings frame (used to force conversion of Caldav alarms to AUDIO supported by iCal)
- Caldav: fix 2884864, send notifications to all participants on CANCEL
- Caldav: Fix invalid event handling, exclude events from returned list

### SMTP:
- SMTP: implement RSET (reset) command to avoid connection timeout with Evolution

## DavMail 3.6.2 2010-01-11
New bugfix release, with improved OSX tray icon, Kontact
support, a new workaround for thunderbird IMAP no message error,
public folders on a separate server support, improved ActiveSync
support and some documentation enhancements.

### LDAP:
- LDAP: fix bug 2919463, escape quotes in search filter
- LDAP: fix Kontact ldap filter parsing, allow LDAP_FILTER_PRESENT in subfilter

### Documentation:
- Doc: fix script replace
- Doc: new download and build pages
- Doc: update roadmap
- Doc: update doc
- Doc: add search icon
- Doc: Update roadmap
- Doc: Add roadmap to site menu

### IMAP:
- IMAP: workaround for thunderbird random issue with no message found, keep previous message list to cope with recent message uid change.
- IMAP: try to support public folders hosted on a separate server (302 redirect on PROPFIND)
- IMAP: fix date parsing error, see bug 2878289
- IMAP: fix 2878289, implement extended MIME header search in http://schemas.microsoft.com/mapi/string/{00020386-0000-0000-C000-000000000046}/ namespace
- IMAP: improve error logging on 500 internal server error
- IMAP: Improve error handling, do not fail on message retrieval error, just send error message
- IMAP: implement EXPUNGE untagged response on NOOP to avoid NO message not found on Exchange message message uid change
- IMAP: implement RFC822.HEADER for Sylpheed

### Caldav:
- Caldav: do not send fake inbox for public calendars to iCal
- Caldav: id 2900599, implement optional attendees in notifications
- Caldav: fix bug 2896135, iCal login fails at iCal startup
- Caldav: Send root instead of calendar href as inbox to fix iCal regression
- Caldav: Exclude events with a null instancetype and no ICS content
- Caldav: Workaround for Lightning 1.0pre public calendar, send calendar href as inbox/outbox urls
- Caldav: Convert DISPLAY to AUDIO only if user defined an alarm sound in settings (davmail.caldavAlarmSound)
- Caldav: fix NullPointerException in notifications
- Caldav: Fix bug 2907350, multiple calendar support issue with iCal
- Caldav: another timezone fix
- Caldav: Improve error handling on invalid events
- Caldav: another timezone fix
- Caldav: do not return invalid message content
- Caldav: move failover for Exchange 2007 plus encoding issue to Exchange session
- Caldav: a brand new ActiveSync fix, set contentclass to make ActiveSync happy, but also set PR_INTERNET_CONTENT to preserve custom properties. Also get etag from updated event.
- Caldav: major refactoring, move all ICS methods to Event inner class
- Caldav: fix bug 2902358, encode messageUrl in PropPatch with forceActiveSyncUpdate=true
- Caldav: improve MIME message headers in createOrUpdateEvent

### Bugfixes:
- Fix last open session failover: do not append @hostname when alias contains @
- Revert to message url as default, use permanentUrl as failover
- Always use NTCredentials for proxy authorization
- Another NTLM fix: activate NTLM only on 401 unauthorized in executeGetMethod

### Enhancements:
- Fix from audit (spelling errors)
- Add search page and change default package name for default svn builds
- Improve message logging
- Fixes from audit
- Additional Jsmooth settings
- Force flags parameter to 4 in Form Based Authentication
- Jsmooth patch to allow -X jvm options

### OSX:
- OSX: replace inverted active icon
- OSX: new Mac OS X only icons

## DavMail 3.6.1 2009-11-24
This is a bugfix release with fixed regressions from 3.6.0
and a few enhancements from user feedback.

### Documentation:
- Doc: switch download links to generic link
- Doc: Update roadmap
- Doc: add roadmap

### Bugfixes:
- Fix regression in Form Based Authentication: detect Exchange 2007 UserContext cookie
- Host is mandatory for NTLMScheme, get current hostname for proxy authentication

### Enhancements:
- Experimental: reactivate NTLM authentication but leave authentication preemptive mode to allow basic authentication.
- Move PKCS11 registration to a separate class to avoid ClassNotFoundException
- Experimental OTP (token) based authentication
- Vista png icons support for JSmooth
- Fix from audit
- New upload-dist ant task to upload new release files

### Caldav:
- Caldav: failover for Exchange 2007 plus encoding issue, search event by displayname to get permanent Url
- Caldav: Additional timezones
- Caldav: Revert commit 765, VTODO events have a null instancetype
- Caldav: additional timezone
- Caldav: Remove MAILTO: in addition to mailto: in getFreeBusy
- Caldav: Bug 2898469 do not UrlEncode draft url twice to avoid 404 not found on send event message

## DavMail 3.6.0 2009-11-15
This release contains a lot of enhancements, both bug fixes
and new features from user feedback on 3.5.0, including improved
Evolution LDAP support, LDAP abandon support (faster searches with
Evolution and OSX), experimental windows service wrapper, improved
form based authentication support and ENVELOPE IMAP command support.  
I wish to thank Dan Foody for his valuable contributions on
OSX Snow Leopard support (attendee completion in iCal and complex
LDAP filters handling).
Also added an architecture schema on DavMail home page to quickly
describe DavMail features.

### LDAP:
- LDAP: implement cn=* filter for Evolution
- LDAP: run searches in separate threads to implement ABANDON, will make searches faster with some clients (Evolution and OSX address book)
- LDAP: implement startsWith on Contact search, only objectclass=* is a full search
- LDAP: fix for iCal4 attendee completion,  send localhost if current socket is local, else send fully qualified domain name
- LDAP: major refactoring from Dan Foody to improve complex filters handling
- LDAP: improve contact search, reencode uids to hex to avoid case sensitivity issues

### Documentation:
- Doc: Set Dan Foody as main java contributor
- Doc: improve DavMail logo
- Doc: add new Logo, improve internet explorer compatibility
- Doc: Add an architecture schema on site welcome page
- Doc: Improve getting started documentation, explain Exchange 2003 and 2007 paths (/exchange/ and /owa/) 
- Doc: fix site style
- Doc: fix maven site title

### SMTP:
- SMTP: fix by Marc Macenko, case sensitive RCPT TO: 
- SMTP: allow lower case commands
- SMTP: experimental: remove Content-Type on create message to avoid 406 not acceptable with some Exchange servers.

### Bugfixes:
- Fix 2887947: Exchange server with a username hidden field

### Enhancements:
- Check for released version in a separate thread and set timeout to ten seconds
- Refactor message url encoding
- Upgrade Jmooth wrappers, add -Xrs jvm option to davmailservice wrapper to avoid service stop on user session logout (http://sourceforge.net/projects/jsmooth/forums/forum/297041/topic/2370742)
- Fix regression from revision 811
- Refactor ExchangeSession, use StringUtil to simplify code
- Remove username duplicate check, as formLogin now resets values before POST
- Start refactoring: StringUtil class
- Fix classpath in jsmooth wrappers to use new javamail
- Allow custom form with userid/pw fields in form based authentication
- Improve form based authentication, look for Exchange session cookies sessionid and cadata
- Fix test
- Upgrade JavaMail to 1.4.1
- New create folder method
- Fix FBA authentication, reset query string in getAbsoluteURI()
- New abstract JUnit test case class
- Detect redirect form instead of logon form, follow redirect to logon form
- Add an upload-site ant task
- Fixes from audit
- Fix settings default values and update doc
- Drop icon activity switches under 250ms to avoid fast flickering on OSX, add new switch icon in IMAP fetch iterations
- Improve script based form redirect to handle more cases
- Refactor ExchangeSession to allow independent session creation.
- Allow directory in logFilePath settings, add /davmail.log suffix in this case
- Allow follow redirects on /public GET requests

### Caldav:
- Caldav: use permanenturl for Caldav to avoid encoding issues
- Caldav: do not close connection on 401 authorization required, may help iCal authentication
- Caldav: Additional Allday fix for Exchange 2007 and Outlook, implement a failover with a new davmail.timezoneId setting.
- Caldav: fix regression on create event, missing CRLF in mime message
- Caldav: Fix regression on public calendar folders linked to multiple calendar support for iCal
- Caldav: use chunked response to send calendar folder content as ICS to avoid timeout
- Caldav: Experimental GET ics on folder and fix regression on public folder access
- Caldav: get current VTIMEZONE body from OWA to create Allday events compatible with Outlook. Users still need to select the same Timezone in Outlook and OWA.
- Caldav: Fix Timezone value
- Caldav: Create a new setting davmail.forceActiveSyncUpdate to let users choose to force ActiveSync event. Note: custom iCal or Lightning ICS properties are lost if this option is enabled.
- Caldav: Some Exchange servers redirect to a different host for freebusy, use wide auth scope
- Caldav: Another fix from Dan Foody: improve dumpICS debug option
- Caldav: need to check session on each request, credentials may have changed or session expired
- Caldav: fix regression after ActiveSync patch, PROPPATCH on contentClass removes all custom ICS properties
- Caldav: improve getICSValue, do not return values inside VALARM section
- Caldav: do not send events with a null instancetype (may be the cause of iCal failure)
- CalDav: Send sub folders for multi-calendar support under iCal
- Caldav: fix path translation to Exchange for calendars in sub folders under /calendar
- Caldav: Added supported-calendar-component-set to calendar response
- Caldav: added a debug trace when requested calendar is not user calendar (maybe shared, but often url mistake in Caldav client)
- Caldav: fix Bug 2686125, PROPPATCH event after PUT to trigger activeSync PUSH, tested with iPhone 3 using activeSync

### IMAP:
- IMAP: use permanenturl instead of href to avoid url encoding issues
- IMAP: Revert convert absolute IMAP path to relative path, breaks Caldav
- IMAP: Convert absolute IMAP path to relative path and detect ISA server cookie starting with cadata (instead of equals cadata)
- IMAP: use upper case NIL in ENVELOPE
- IMAP: improve MimeMessage handling, drop after fetch to avoid keeping full message in memory
- IMAP: fix new ENVELOPE feature, must return encoded values
- IMAP: implement store by id and ENVELOPE
- IMAP: update message flag only if changed to avoid unneeded message uid bump, may fix Evolution and Apple Mail constant reload issue
- IMAP: implement search by id
- IMAP: send default BODYSTRUCTURE on MIME encoding error
- IMAP: improve complex content-type handling in BODYSTRUCTURE
- IMAP: fix deleted flag handling, switch to official Exchange IMAP property http://schemas.microsoft.com/mapi/id/{00062008-0000-0000-C000-000000000046}/0x8570
- IMAP: detect HttpNotFoundException on folder select
- IMAP: improve public folder error handling
- IMAP: fix space at end of folder name
- IMAP: Fix regression on LIST INBOX
- IMAP: experimental public folder access
- IMAP: switch to http://schemas.microsoft.com/exchange/contentstate to handle deleted flag (DAV:isdeleted did not work with some Exchange servers).
- IMAP: implement undelete message

## DavMail 3.5.0 2009-09-22
This release improves OSX Snow Leopard support, thank to
contributions from Dan Foody. Contact searches are also
available now in addition to global address book searches
over LDAP. IMAP with iPhone should now work correctly with
most messages, Evolution IMAP read flag is fixed.
Also added an experimental windows service support
and a lot of other bug fixes and enhancements. 

### Doc:
- Doc: Fix FAQ whitespaces
- Doc: improve javadoc and code cleanup
- Doc: New OSX settings screenshot
- Doc: update release guide
- Doc: improve index and build doc
- Doc: detailed WIRE debug log file creation

### Windows:
- Windows: Include DavMail service in windows package
- Windows: create windows service exe
- Windows: create windows service exe

### IMAP:
- IMAP: test session expiration on each command, get a new session on expiration
- IMAP: improve error logging on network down
- IMAP: fix bug 2845530 implement FLAGS.SILENT command
- IMAP: fix absolute (public) path handling
- IMAP: fix BODYSTRUCTURE, build message on full buffer, do not rely on partial buffer (header, body, ...)
- IMAP: fix bug 2835529 FETCH with unordered range
- IMAP: send default BODYSTRUCTURE on mime parsing failure
- IMAP: Improve IMAP bodystructure error logging
- IMAP: Send bodystructure with headers for iPhone request (BODYSTRUCTURE BODY.PEEK[HEADER]) 
- IMAP: send BODY[TEXT] for BODY.PEEK[TEXT] request, may improve iPhone support
- IMAP: First fix for bug 2840255, do not follow redirects on message FETCH

### Caldav:
- Caldav: fix conflict between X-MICROSOFT-CDO-BUSYSTATUS, X-MICROSOFT-CDO-ALLDAYEVENT and ORGANIZER ics patches
- Caldav: check credentials on each request
- Caldav: Disable broken sub calendar folders code
- Caldav: Do not fail on Inbox access denied, just return an empty folder
- Caldav: fix multi calendar Exchange path for sub folders
- Caldav: Experimental, fix sub calendar folders path
- Caldav: Experimental, send sub calendar folders on propfind with depth 1
- Caldav: Handle multi line description in calendar message body
- Caldav: merged contribution from Dan Foody,
- Caldav: convert sound to display alarms and back
- Caldav: remove additional organizer attendee line
- Caldav: remove RSVP=TRUE if PARTSTAT is not NEEDS-ACTION
- Caldav: add dump ICS logging feature
- Caldav: add a text/plain body to calendar messages
- Caldav: create a subject for calendar messages
- Caldav: fixed some encoding issues in Dan's code
- Caldav: Additional timezones
- Caldav: failover to DAV:comment instead of CALDAV:schedule-state on some Exchange servers

### LDAP:
- LDAP: iCal fix to suit both iCal 3 and 4:  move cn to sn, remove cn
- LDAP: iCal: do not send LDAP_SIZE_LIMIT_EXCEEDED on apple-computer search by cn with sizelimit 1
- LDAP: copy uid to apple-generateduid for iCal attendee search 
- LDAP: Make sure we do not send empty description field, replace " \n" with null
- LDAP: fix thread name
- LDAP: exclude non contact entries from search, fiw map key and sn copy for iCal
- LDAP: fix contact search, do not send unrequested attributes
- LDAP: improve Contact search filter support
- LDAP: Additional Contact attributes
- LDAP: refactor contact find, generic attribute mapping
- LDAP: experimental contact search support

### OSX:
- OSX: Prepare hide from Dock option
- OSX: crazy workaround from Dan Foody to fix attendee search on OSX Snow Leopard
- OSX: iCal4 (OSX Snow Leopard fixes)

### Bug Fixes:
- Fix regression in Form Based Logon: fix script based logon URI creation with path and query
- Another network down fix: DavMailException is not network down
- Improve Form Based Logon: fix script based logon URI creation
- Improve Form Based Logon: use full URI instead of path in PostMethod, also force trusted=4
- Simplify HttpClient creation to avoid password decoding bug in commons httpclient ('+' in password decoded as ' ')

### Enhancements:
- Improve HttpException error logging
- Fixes from checkStyle audit
- Adjust checkStyle settings
- Improve error handling on invalid URL
- Various fixes from FindBugs audit
- Fix from audit: synchronize access to HttpConnectionManager
- Refactor ExchangeSession: do not follow redirects with GET methods
- Fix from audit
- Do not pass DavMailAuthenticationException to handle network down
- Custom form (txtUserName, tstUserPass) support
- Another network down fix from Dan Foody
- Merged another patch from Dan Foody on network down detection
- New settings method: return log file directory

## DavMail 3.4.0 2009-08-14
This release includes iPhone 3 Caldav support, upgrade to SWT 3.5,
Palm Pre IMAP fixes, improved shared/public calendar support
and a lot of bug fixes.

### Doc:
- Doc: Code cleanup and improve javadoc
- Doc: Update doc
- Doc: Upgrade maven site plugin and improve style
- Doc: Update maven pom inceptionYear

### Bug:
- Bug: Do not try to set Nimbus Look And Feel on Linux with Gtk

### Enhancements:
- Remove NTLM authentication, breaks Basic authentication (missing domain in username)
- Set NTLM as last authentication scheme
- Experimental: reenable NTLM authentication
- Upgrade SWT to 3.5
- Use getFolderPath in getSubFolders
- Make API more consistent: createMessage must get a folder path, not URL
- Enhancement: Patch 2826966 from Eivind Tagseth, Make davmail.sh work from any location

### IMAP:
- IMAP: Need to reset index for Palm pre
- IMAP: case insensitive search operators
- IMAP: Fix bug 2835529, implement SEARCH ALL SINCE for Palm Pre

### Caldav:
- Caldav: improve error handling, 440 means 403 forbidden on Exchange
- Caldav: Fix shared calendar support for Lightning
- Caldav: additional patch for Outlook created recurring events
- Caldav: set X-MICROSOFT-CDO-BUSYSTATUS according to TRANSP field
- Caldav: implement a timezone translation table for iPhone 3, revert organizer patch (breaks notifications with Lightning)
- Caldav: another iPhone fix, remove organizer line if user is organizer
- Caldav: generic timezone patch for iPhone 3
- Caldav: remove empty ics properties
- Caldav: Remove calendar-proxy, only used for delegate calendars
- Caldav: try to improve responses for iCal
- Caldav: fix bug 2833044 Event not found error on dismissing reminders with events created in Outlook with a plus sign in subject
- Caldav: Experimental, add calendar-proxy DAV option and version in server header
- Caldav: Add missing allow OPTIONS header
- Caldav: improve public (shared) calendar support, accept calendars at any depth
- Caldav: set caldav logging to davmail logging level
- Caldav: updated fix, remove organizer line if event has no attendees for iPhone
- Caldav: remove organizer line if current user is organizer for iPhone, will not remove line for events with attendees
- Caldav: Improve principal -> actualPrincipal detection: use session alias instead of login
- Caldav: fix bug 2819028, case insensitive email in sendPrincipal test
- Caldav: iPhone compatibility, remove <C:comp name="VTODO"/>
- Caldav: iPhone workaround: send calendar subfolder
- Caldav: revert @ encoding, breaks iCal
- Caldav: iPhone fix, encode @ in Caldav response hrefs
- Caldav: untested, extended PROPFIND / response for iPhone 3.0
- Caldav: fix infinite loop with Sunbird 1.0pre with invalid credentials

### SMTP:
- SMTP: fix bug 2791607, do not patch message body (breaks electronic signature), no longer needed with latest Thunderbird

## DavMail 3.3.0 2009-07-22
This is a bug fix release after two beta releases,
including PKCS11 (smartcard) client certificate support,
gateway encryption (SSL) support, the new jackrabbit and httpclient libraries
and I18N support (french and english available).

- Caldav: updated caldav response headers according to gmail, added Expires and Cache-control HTTP headers
- POP3: implement NOOP command
- Doc: Update documentation header
- Doc: Add GPLv2 header to all source files
- Doc: Remove Apache license from checkstyle config file header
- IMAP: fix DELETED/UNDELETED SEARCH parameters
- IMAP: Fix bug 2822625: support index range in IMAP SEARCH
- Enhancements: Merged network down (with firewall) code from Dan Foody
- Caldav: Additional properties and ignore cases for Sunbird
- Caldav: Fix empty organizer field in ICS (active sync support) and another getParticipants bug
- GUI: Fix OSX menu default ActionListener
- GUI: Try to set Nimbus Look And Feel on Linux with Gtk

## DavMail 3.3.0b2 2009-06-29
This is a bug fix release, with nonetheless one main new feature:
PKCS11 (smartcard) client certificate support !
Tested with ActivIdentity ActivClient and Mozilla soft token, should
work with any PKCS11 module.

### Security:
- (RFE 2800206) PKCS11 (smartcard) client certificate support

### Server (daemon) mode:
- Fix server mode: now all listener threads are daemon, avoid main thread exit and add a shutdown hook
- Name shutdown thread

### Caldav:
- Move wire debug log with headers
- Fix NoSuchMethodError with Java 5
- revert supported-calendar-component-set on root and improve logging
- fix regression on iCal calendar color change
- only include attendees with RSVP=TRUE or PARTSTAT=NEEDS-ACTION for notifications (avoid iCal additional notifications)
- Improve error handling on FreeBusy failure
- add supported-calendar-component-set property requested by iPhone 3.0
 Sunbird compatibility, exclude events with empty names
- Fix for iCal: send etag on GET and HEAD requests
- Send empty response instead of error on freebusy with unknown attendee

### IMAP:
- revert refreshFolder calls that break Outlook

### OSX:
- Remove default trayIcon listener on OSX
- Fixed logFilePath logic on OSX

### Enhancements:
- Update ExchangeSession test
- Exclude optional log4j maven dependencies
- Added a logFilePath setting to set log4j file appender path, this appender is now added dynamically to avoid davmail.log file create failure
- Upgrade Log4J to 1.2.15
- Make sure we do exit: catch exceptions before System.exit

### Doc: 
- Document build process in FAQ

### Known issues:
- Does not - yet - work with iPhone 3.0 Caldav
- Still issues with Exchange activeSync mode

## Davmail 3.3.0b1 2009-06-10
This release is mainly a deep refactoring: replaced deprecated 
jakarta slide library with jackrabbit and upgrade httpclient.
Also added client to gateway encryption (SSL) support, started I18N
(french and english available) and many bug fixes and enhancements.

### I18N:
- I18N: FrameGatewayTray
- I18N: Format port numbers and add missing message
- I18N: improve startup log message
- I18N: remove Locale.ROOT not available under Java 1.5
- I18N: externalize and translate exception messages
- I18N: Do not apply i18n on log file
- I18N: french localization
- I18N: externalize all DavGatewayTray log statements for i18n
- I18N: ldap package
- I18N: davmail package
- I18N: start internationalization conversion

### POP:
- POP: Defer message list after login phase and load only uid and size attributes
- POP: make sure the url is encoded correctly on delete

### IMAP:
- IMAP: Detect fetch of a missing (probably deleted) message to avoid infinite loop with Thunderbird
- IMAP: reset icon after each command

### SMTP:
- SMTP: fixed two bugs, header ignored because of Exchange 2007 from patch and bccbuffer 
 double xml encoding (=> Delivery status notification)

### LDAP:
- LDAP: improve ldap search logging

### Doc:
- Add Mitchell V. Oliver as Java Contributor
- SSL certificate settings documentation in getting started
- Update OSX doc: credentials are mandatory in Directory Utility settings
- Add Eivind Tagseth as Java Contributor

### Enhancements:
- Check java version in ant build.xml
- Fix ExchangeSession test
- Refactor DavProperty handling with new jackrabbit library
- Close idle connections to exchange after one minute
- Avoid 401 roundtrips
- Remove deprecated HttpException api
- Replace deprecated setRequestBody with setRequestEntity
- Refactor DavProperty handling with new jackrabbit library
- Update packaging and Maven POM after library update
- Major refactoring: replace deprecated jakarta slide with jackrabbit and upgrade httpclient
- Upgrade svnkit library
- Sort properties and display version in startup message
- Use interactive console certificate accept in headless and/or server mode
- Append svn build number to release name
- Additional login failover : get email from options page
- Replace greyscale inactive icon with a color icon
- Avoid nullpointerexception in Settings.setProperty
- Reinsert System.exit after clean shutdown to make sure we do exit
- Make all threads daemon and remove System.exit calls
- Patch 2790299 by Mitchell V. Oliver: Add support for SSL to client connections
- Remember previous checkConfig status to detect network down
- Fixes from audit
- Refactor email retrieval : do not throw IOException in failovers
- Implement BundleMessage.toString() for direct usage in Log4J logger
- Revert to simple class names in thread names
- Catch unknown host on session login
- Workaround for post logon script redirect
- Workaround for Exchange server misconfiguration: move galfind requests to mailPath or /exchange instead of /public
- Enhancements from audit
- Fix exchangeSession test class
- Improve BindException error message
- Cleanup from audit
- Improve exception handling
- Implement a last resort failover to build email from alias and domain name
- Limit redirects to 10 instead of 100
- Replace hardcoded strings
- Refactor SimpleDateFormat usage
- Reorganize packages

### OSX:
- OSX: replace JavaApplicationStub link with actual file
- OSX: Move davmail.log to Library/Logs/DavMail on OSX
- OSX: Improve Mac OSX Java6 support
- OSX: fix regression on OSX Quit handler

### Bugs fixed:
- Fix bug 2797272: Add disable update check
- Do not localize port numbers
- Replaced localhost check with the isLoopbackAddress() method, should be IPV6 compatible
- Fix regression : /exchange/ does not work for galfind under Exchange 2007
- Fixed 2717547: Unsupported filter attribute: apple-group-memberguid
- URI encode alias in getEmail()
- Fix SSLProtocolSocketFactory with HttpClient 3.1
- Reenable limited timeout on getReleasedVersion
- Always exclude NTLM authentication, not only for proxy authorization
- Fix 2717446 from Eivind Tagseth

### Caldav:
- Caldav: fix unknown recipient message
- Caldav: do not send freebusy info if attendee is unknown
- Caldav: Improve calendar-color patch answer
- Caldav: implement HEAD request
- Improve network down detection for Caldav
- Caldav: No need to check connectivity on HTTPS
- Caldav: Fix Bug 2783595, allow empty lines in ICS content
- Caldav: Exclude RSVP=FALSE from notifications recipients list for Outlook 2003 compatibility
- Caldav: exclude invalid attendees address from recipient list
- Caldav: avoid duplicate / in event path
- Caldav: implement public shared calendar
- Caldav: In progress multi calendar support
- Caldav: fix regression in FreeBusy date handling
- Caldav: switch icon during event report
- Caldav: refactor CaldavConnection, prepare /public context
- Caldav: another special characters handling improvement
- Caldav: iCal decodes hrefs, not lightning => detect client in CaldavRequest
- Caldav: replace etag by resourcetag in getCalendarEtag
- Caldav: Send events back to the client after each get on REPORT request (avoid iCal timeout)
- Caldav: no inbox/outbox for delegated calendars
