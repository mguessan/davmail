# do not hard-code ids https://sourceforge.net/mailarchive/message.php?msg_id=27249602
%{?!davmail_uid:   %define davmail_uid   213}
%{?!davmail_gid:   %define davmail_gid   213}

%{?!davrel:   %define davrel   4.3.2}
%{?!davsvn:   %define davsvn   2137}
%define davver %{davrel}-%{davsvn}
%ifarch i386 i586 i686
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
BuildRequires: ant >= 1.7.1, ant-nodeps >= 1.7.1, ant-antlr, desktop-file-utils
BuildRequires: java-devel >= 1.6.0
Requires: coreutils
Requires: filesystem
Requires(pre): /usr/sbin/useradd, /usr/sbin/groupadd
Requires(post): coreutils, filesystem, /sbin/chkconfig
Requires(preun): /sbin/service, coreutils, /sbin/chkconfig, /usr/sbin/userdel, /usr/sbin/groupdel
Requires(postun): /sbin/service
Requires: /etc/init.d, logrotate, jre >= 1.6.0

%define davmaildotproperties davmail.properties

Source0: %{name}-src-%{davver}.tgz
Source1: davmail.sh
Source2: davmail-logrotate
Source3: davmail-init
Source4: %{davmaildotproperties}
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
# JAVA_HOME points to the JDK root directory: ${JAVA_HOME}/{bin,lib}
jre=`rpm -q --whatprovides jre`
jexec=`rpm -ql ${jre} | grep "jexec"`
lib=`dirname ${jexec}` # level up
jre=`dirname ${lib}` # level up
java_home=`dirname ${jre}` # level up
export JAVA_HOME=${java_home}
export ANT_OPTS="-Dfile.encoding=UTF-8"
# we have java 1.6
ant -Dant.java.version=1.6

%install
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT/%{_bindir}
mkdir -p $RPM_BUILD_ROOT/%{_sysconfdir}/logrotate.d
mkdir -p $RPM_BUILD_ROOT/%{_sysconfdir}/init.d
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/applications
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/pixmaps
mkdir -p $RPM_BUILD_ROOT/%{_datadir}/davmail/lib
mkdir -p $RPM_BUILD_ROOT/%{_localstatedir}/lib/davmail
mkdir -p $RPM_BUILD_ROOT/%{_localstatedir}/log

# Init scripts, icons, configurations
install -m 0775 %{SOURCE1} $RPM_BUILD_ROOT/%{_bindir}/davmail
install -m 0644 %{SOURCE2} $RPM_BUILD_ROOT/%{_sysconfdir}/logrotate.d/davmail
install -m 0775 %{SOURCE3} $RPM_BUILD_ROOT/%{_sysconfdir}/init.d/davmail
install -m 0644 %{SOURCE4} $RPM_BUILD_ROOT/%{_sysconfdir}
# https://fedoraproject.org/wiki/TomCallaway/DesktopFileVendor
desktop-file-install --dir $RPM_BUILD_ROOT/%{_datadir}/applications/ %{SOURCE5} --vendor=""
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
# do not hard-code ids https://sourceforge.net/mailarchive/message.php?msg_id=27249602
#/usr/sbin/groupadd -g %{davmail_gid} -f -r davmail > /dev/null 2>&1 || :
#/usr/sbin/useradd -u %{davmail_uid} -r -s /sbin/nologin -d /var/lib/davmail -M \
#                  -g davmail davmail > /dev/null 2>&1 || :
/usr/sbin/groupadd -f -r davmail > /dev/null 2>&1 || :
/usr/sbin/useradd -r -s /sbin/nologin -d /var/lib/davmail -M \
                  -g davmail davmail > /dev/null 2>&1 || :

%post
file=/var/log/davmail.log
if [ ! -f ${file} ]
    then
    /bin/touch ${file}
fi
/bin/chown davmail:davmail ${file}
/bin/chmod 0640 ${file}

# proper service handling http://en.opensuse.org/openSUSE:Cron_rename
%{?fillup_and_insserv:
%{fillup_and_insserv -y davmail}
}
%{!?fillup_and_insserv:
# undefined
/sbin/chkconfig --add davmail
#/sbin/chkconfig davmail on
}

%preun
if [ "$1" = "0" ]; then
    /sbin/service davmail stop > /dev/null 2>&1 || :
    /bin/rm -f /var/lib/davmail/pid > /dev/null 2>&1 || :
    %{?stop_on_removal:
    %{stop_on_removal davmail}
    }
    %{!?stop_on_removal:
    # undefined
    /sbin/chkconfig davmail off
    /sbin/chkconfig --del davmail
    }
    /usr/sbin/userdel davmail
    if [ ! `grep davmail /etc/group` = "" ]; then
        /usr/sbin/groupdel davmail
    fi
fi

%postun
if [ $1 -ge 1 ]; then
    %{?restart_on_update:
    %{restart_on_update davmail}
    %insserv_cleanup
    }
    %{!?restart_on_update:
    # undefined
    /sbin/service davmail condrestart > /dev/null 2>&1 || :
    }
fi

%files
%defattr (-,root,root,-)
%{_bindir}/*
%{_sysconfdir}/init.d/davmail
%{_sysconfdir}/logrotate.d/davmail
%config(noreplace) %{_sysconfdir}/%{davmaildotproperties}
%{_datadir}/applications/*
%{_datadir}/pixmaps/*
%{_datadir}/davmail/
%attr(0775,davmail,davmail) %{_localstatedir}/lib/davmail

%changelog
* Fri Dec 09 2011 Marcin Dulak <Marcin.Dulak@gmail.com>
- use /var/run/davmail.lock instead of /var/lock/subsys/davmail
  http://en.opensuse.org/openSUSE:Packaging_checks#subsys-unsupported

* Fri Dec 09 2011 Marcin Dulak <Marcin.Dulak@gmail.com>
- fixed https://bugzilla.novell.com/show_bug.cgi?id=734592

* Wed Apr 20 2011 Marcin Dulak <Marcin.Dulak@gmail.com>
- proper service handling on openSUSE http://en.opensuse.org/openSUSE:Cron_rename

* Thu Mar 24 2011 Marcin Dulak <Marcin.Dulak@gmail.com>
- do not hard-code gid/uid: https://sourceforge.net/mailarchive/message.php?msg_id=27249602

* Fri Mar 18 2011 Marcin Dulak <Marcin.Dulak@gmail.com>
- fixed incorrect JAVA_HOME
- added i386 i586 arch
- uses {davmail_gid} and {davmail_uid} of default 213
- uses /etc/init.d for compatibility with other dists
- BuildRequires and Requires compatible with openSUSE 11.4
- removed runlevels 2 4 from davmail-init: https://bugzilla.novell.com/show_bug.cgi?id=675870

* Mon Oct 18 2010 Marko Myllynen <myllynen@redhat.com>
- Initial version
