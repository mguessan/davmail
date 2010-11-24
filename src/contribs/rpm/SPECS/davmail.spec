%define davrel 3.8.5
%define davsvn 1480
%define davver %{davrel}-%{davsvn}
%ifarch i686
%define davarch x86
%endif
%ifarch x86_64
%define davarch x86_64
%endif

Summary: DavMail is a POP/IMAP/SMTP/Caldav/Carddav/LDAP gateway for Microsoft Exchange
Name: davmail
Version: %{davrel}
Release: 1%{?dist}
License: GPL
Group: Applications/Internet
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildRequires: ant >= 1.7.1, ant-antlr, desktop-file-utils
Requires(pre): chkconfig, coreutils, initscripts, shadow-utils
Requires: logrotate, jre

Source0: %{name}-src-%{davver}.tgz
Source1: davmail.sh
Source2: davmail-logrotate
Source3: davmail-init
Source4: davmail.properties
Source5: davmail.desktop
Source6: davmail-wrapper

%description
DavMail is a POP/IMAP/SMTP/Caldav/Carddav/LDAP Exchange gateway allowing 
users to use any mail/calendar client with an Exchange server, even from 
the internet or behind a firewall through Outlook Web Access. DavMail 
now includes an LDAP gateway to Exchange global address book and user 
personal contacts to allow recipient address completion in mail compose 
window and full calendar support with attendees free/busy display.

%prep
%setup -q -n %{name}-src-%{davver}

%build
export JAVA_HOME=/etc/alternatives/java_sdk
ant

%install
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT/%{_bindir}
mkdir -p $RPM_BUILD_ROOT/%{_sysconfdir}/logrotate.d
mkdir -p $RPM_BUILD_ROOT/%{_sysconfdir}/rc.d/init.d
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/applications
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/pixmaps
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/davmail/lib
mkdir -p $RPM_BUILD_ROOT/%{_localstatedir}/lib/davmail
mkdir -p $RPM_BUILD_ROOT/%{_localstatedir}/log

# Init scripts, icons, configurations
install -m 0775 %{SOURCE1} $RPM_BUILD_ROOT/%{_bindir}/davmail
install -m 0644 %{SOURCE2} $RPM_BUILD_ROOT/%{_sysconfdir}/logrotate.d/davmail
install -m 0775 %{SOURCE3} $RPM_BUILD_ROOT/%{_sysconfdir}/rc.d/init.d/davmail
install -m 0644 %{SOURCE4} $RPM_BUILD_ROOT/%{_sysconfdir}
desktop-file-install --dir $RPM_BUILD_ROOT/%{_datadir}/applications/ %{SOURCE5}
install -m 0775 %{SOURCE6} $RPM_BUILD_ROOT/%{_localstatedir}/lib/davmail/davmail

# Actual DavMail files
install -m 0644 src/java/tray32.png $RPM_BUILD_ROOT/%{_datadir}/pixmaps/davmail.png
rm -f dist/lib/*win32*.jar
install -m 0664 dist/lib/*-%{davarch}.jar $RPM_BUILD_ROOT/%{_datadir}/davmail/lib/
rm -f dist/lib/*x86*.jar
install -m 0664 dist/lib/* $RPM_BUILD_ROOT/%{_datadir}/davmail/lib/
install -m 0664 dist/*.jar $RPM_BUILD_ROOT/%{_datadir}/davmail/

%clean
rm -rf $RPM_BUILD_ROOT

%pre
/usr/sbin/groupadd -r davmail > /dev/null 2>&1 || :
/usr/sbin/useradd  -r -s /sbin/nologin -d /var/lib/davmail -M \
	-g davmail davmail > /dev/null 2>&1 || :

%post
if [ ! -f /var/log/davmail.log ]
then
	/bin/touch /var/log/davmail.log
fi
/bin/chown davmail:davmail /var/log/davmail.log
/bin/chmod 0640 /var/log/davmail.log
/sbin/chkconfig --add davmail
#/sbin/chkconfig davmail on

%preun
if [ "$1" = "0" ]; then
	/sbin/service davmail stop > /dev/null 2>&1 || :
	/bin/rm -f /var/lib/davmail/pid > /dev/null 2>&1 || :
	/sbin/chkconfig davmail off
	/sbin/chkconfig --del davmail
fi

%postun
if [ $1 -ge 1 ]; then
	/sbin/service davmail condrestart > /dev/null 2>&1 || :
fi

%files
%defattr (-,root,root,-)
%{_bindir}/*
%{_sysconfdir}/rc.d/init.d/davmail
%{_sysconfdir}/logrotate.d/davmail
%{_sysconfdir}/davmail.properties
%{_datadir}/applications/*
%{_datadir}/pixmaps/*
%{_datadir}/davmail/
%attr(0775,davmail,davmail) %{_localstatedir}/lib/davmail

%changelog
* Mon Oct 18 2010 Marko Myllynen <myllynen@redhat.com>
- Initial version
