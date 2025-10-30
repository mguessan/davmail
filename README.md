# DavMail POP/IMAP/SMTP/Caldav/Carddav/LDAP Exchange and Office 365 Gateway

[![Build status: Windows](https://ci.appveyor.com/api/projects/status/d7tx645gwqvprd4g?svg=true)](https://ci.appveyor.com/project/mguessan/davmail)
[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://img.shields.io/sourceforge/dm/davmail.svg)](https://sourceforge.net/projects/davmail/files/latest/download)
[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://img.shields.io/sourceforge/dt/davmail.svg)](https://sourceforge.net/projects/davmail/files/latest/download)

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=alert_status)](https://sonarcloud.io/project/overview?id=mguessan_davmail)
[![SonarCloud Bugs](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=bugs)](https://sonarcloud.io/project/overview?id=mguessan_davmail)
[![SonarCloud Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=mguessan_davmail&metric=vulnerabilities)](https://sonarcloud.io/project/overview?id=mguessan_davmail)

**_Security note:_** DavMail does not depend on Log4J2 and is thus not vulnerable to [CVE-2021-44228](https://github.com/advisories/GHSA-jfh8-c2jp-5v3q)

Ever wanted to get rid of Outlook? DavMail is a POP/IMAP/SMTP/Caldav/Carddav/LDAP gateway allowing users to use any mail client with Exchange, even from the internet through Outlook Web Access on any platform, tested on macOS, Linux and Windows

![DavMail Architecture](src/site/resources/images/davmailArchitecture.png)

The main project site is still on Sourceforge at http://davmail.sourceforge.net/.

This git repository is synchronized with the subversion repository in order to make contributions easier for GitHub users.

## Donate

Donations are welcome to help support DavMail development, in dollars 
[![Donate dollars](https://img.shields.io/badge/paypal-donate-green.svg)](https://www.paypal.com/cgi-bin/webscr?item_name=Donation+to+DavMail+POP%2FIMAP%2FSMTP%2FCaldav+to+Exchange&amp;cmd=_donations&amp;business=mguessan%40free.fr&amp;lc=US)
or Euros
[![Donate euros](https://img.shields.io/badge/paypal-donate-green.svg)](https://www.paypal.com/cgi-bin/webscr?item_name=Donation+to+DavMail+POP%2FIMAP%2FSMTP%2FCaldav+to+Exchange&amp;cmd=_donations&amp;business=mguessan%40free.fr&amp;lc=US&amp;currency_code=EUR)

## Download
Download latest DavMail release on Sourceforge

[![Download DavMail POP/IMAP/SMTP/Caldav to Exchange](https://a.fsdn.com/con/app/sf-download-button)](https://sourceforge.net/projects/davmail/files/davmail/6.5.1/)

RPM packages are available from OpenSuse build service:

[![build result](https://build.opensuse.org/projects/home:mguessan:davmail/packages/davmail/badge.svg?type=default)](https://build.opensuse.org/package/show/home:mguessan:davmail/davmail)

An alternative is COPR on Fedora:

[![Copr build status](https://copr.fedorainfracloud.org/coprs/mguessan/davmail/package/davmail/status_image/last_build.png)](https://copr.fedorainfracloud.org/coprs/mguessan/davmail)

If your distribution supports Flatpak:

[![Flathub Downloads](https://img.shields.io/flathub/downloads/org.davmail.DavMail?label=Flathub)](https://flathub.org/en/apps/org.davmail.DavMail)

Docker images are available on GitHub Container Registry:

[![label=Docker](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fghcr-badge.elias.eu.org%2Fapi%2Fmguessan%2Fdavmail%2Fdavmail&query=downloadCount&logo=docker&label=GitHub%20Docker%20Pulls&color=2496ed)](https://github.com/mguessan/davmail/tree/master/src/docker/README.md)


## Trunk builds
The latest working builds are now available on Appveyor:

* Windows 64 bits setup [davmail-6.5.1-trunk-setup64.exe](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.5.1-trunk-setup64.exe?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)
* Windows standalone (with embedded Azul JRE-FX) package [davmail-6.5.1-trunk-windows-standalone.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.5.1-trunk-windows-standalone.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* Platform independent package [davmail-6.5.1-trunk.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail-6.5.1-trunk.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* Debian package [davmail_6.5.1-trunk-1_all.deb](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2Fdavmail_6.5.1-trunk-1_all.deb?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

* OSX application [DavMail-MacOSX-6.5.1-trunk.app.zip](https://ci.appveyor.com/api/projects/mguessan/davmail/artifacts/dist%2FDavMail-MacOSX-6.5.1-trunk.app.zip?job=Environment%3A%20JAVA_HOME%3DC%3A%5CProgram%20Files%5CJava%5Cjdk1.8.0)

## Contribute
Contributions are welcome, you can either [submit a patch](https://sourceforge.net/p/davmail/patches/) or create a [GitHub pull request](https://github.com/mguessan/davmail/pulls).

In case you are looking for tasks to work on, please check our current
[Backlog](https://sourceforge.net/p/davmail/feature-requests/milestone/Backlog/).
