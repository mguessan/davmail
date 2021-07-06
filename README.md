# DavMail POP/IMAP/SMTP/Caldav/Carddav/LDAP Exchange and Office 365 Gateway

[![Build Status: Linux](https://travis-ci.org/mguessan/davmail.svg?branch=master)](https://travis-ci.org/mguessan/davmail)
[![Build status: Windows](https://ci.appveyor.com/api/projects/status/d7tx645gwqvprd4g?svg=true)](https://ci.appveyor.com/project/mguessan/davmail)
[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://img.shields.io/sourceforge/dm/davmail.svg)](https://sourceforge.net/projects/davmail/files/latest/download)
[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://img.shields.io/sourceforge/dt/davmail.svg)](https://sourceforge.net/projects/davmail/files/latest/download)

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=alert_status)](https://sonarcloud.io/dashboard/index/mguessan_davmail)
[![SonarCloud Bugs](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=bugs)](https://sonarcloud.io/dashboard/index/mguessan_davmail)
[![SonarCloud Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=vulnerabilities)](https://sonarcloud.io/dashboard/index/mguessan_davmail)

:warning: **HttpClient 4 migration in progress**: Trunk will not be as stable as usual

Ever wanted to get rid of Outlook ? DavMail is a POP/IMAP/SMTP/Caldav/Carddav/LDAP gateway allowing users to use any mail client with Exchange, even from the internet through Outlook Web Access on any platform, tested on MacOSX, Linux and Windows

![DavMail Architecture](src/site/resources/images/davmailArchitecture.png)

Main project site is still on Sourceforge at http://davmail.sourceforge.net/.

This git repository is synchronized with subversion repository in order to make contributions easier for Github users.

## Download
Download latest DavMail release on Sourceforge

[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://a.fsdn.com/con/app/sf-download-button)](https://sourceforge.net/projects/davmail/files/davmail/6.0.0/)

## Trunk builds
Latest working builds are now available on Appveyor:

* Windows setup [davmail-6.0.0-trunk-setup.exe](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.0.0-trunk-setup.exe?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)
* Windows 64 bits setup [davmail-6.0.0-trunk-setup64.exe](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.0.0-trunk-setup64.exe?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)
* Windows noinstall package [davmail-6.0.0-trunk-windows-noinstall.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.0.0-trunk-windows-noinstall.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)
* Windows standalone (with embedded Azul JRE-FX) package [davmail-6.0.0-trunk-windows-standalone.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.0.0-trunk-windows-standalone.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* Platform independent package [davmail-6.0.0-trunk.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.0.0-trunk.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* Debian package [davmail_6.0.0-trunk-1_all.deb](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail_6.0.0-trunk-1_all.deb?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* OSX application [DavMail-MacOSX-6.0.0-trunk.app.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2FDavMail-MacOSX-6.0.0-trunk.app.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

## Contribute
Contributions are welcome, you can either [submit a patch](https://sourceforge.net/p/davmail/patches/) or create a [Github pull request](https://github.com/mguessan/davmail/pulls).

In case you are looking for tasks to work on, please check current
[Backlog](https://sourceforge.net/p/davmail/feature-requests/milestone/Backlog/).


