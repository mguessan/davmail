%define systemd_support 0%{?suse_version} || 0%{?el7} || 0%{?el8} || 0%{?el9} || 0%{?fedora}
%define systemd_macros 0%{?suse_version} || 0%{?fedora} > 42

Summary: A POP/IMAP/SMTP/Caldav/Carddav/LDAP gateway for Microsoft Exchange
Name: davmail
URL: http://davmail.sourceforge.net
Version: 6.5.1
Release: 1%{?dist}
License: GPL-2.0+
Group: Productivity/Networking/Email/Utilities
BuildArch: noarch
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildRequires: ant, desktop-file-utils
# required to define _unitdir macro
%if %systemd_support
BuildRequires: systemd
%endif

# same on suse
%if %systemd_macros
BuildRequires: systemd-rpm-macros
%endif

%if 0%{?fedora}
BuildRequires: java-latest-openjdk-devel
%endif

# fedora 42 and later only
%if 0%{?fedora} > 41
BuildRequires: ant-unbound
%endif

%if 0%{?el8} || 0%{?el9}
BuildRequires: java-1.8.0-openjdk-devel
%endif

# compile with JavaFX on Fedora and latest Suse
%if 0%{?fedora} > 38 || 0%{?suse_version} >= 01699
BuildRequires: openjfx
%endif

Requires: coreutils
Requires: filesystem
Requires(post): coreutils, filesystem
Requires(postun): /sbin/service
%if %systemd_macros
Requires(preun): /sbin/service, coreutils
%if 0%{?suse_version}
BuildRequires: sysuser-tools
%sysusers_requires
%endif

%else
Requires(preun): /sbin/service, coreutils, /usr/sbin/userdel, /usr/sbin/groupdel
Requires(pre): /usr/sbin/useradd, /usr/sbin/groupadd
%endif

%if 0%{?el9} || 0%{?fedora}
Requires: java
%endif

%if 0%{?el7} || 0%{?el8}
Requires: java-1.8.0-openjdk
%endif

%if 0%{?is_opensuse} || 0%{?suse_version}
Requires: java-21-openjdk
%endif

Source0: %{name}-src-%{version}.tgz

%description
A POP/IMAP/SMTP/Caldav/Carddav/LDAP Exchange gateway allowing
users to use any mail/calendar client with an Exchange server, even from
the internet or behind a firewall through Outlook Web Access. DavMail
now includes an LDAP gateway to Exchange global address book and user
personal contacts to allow recipient address completion in mail compose
window and full calendar support with attendees free/busy display.

%prep
%setup -q -n %{name}-src-%{version}

%build
# JAVA_HOME points to the JDK root directory: ${JAVA_HOME}/{bin,lib}
jcompiler=`readlink -f $(which javac)`
bin=`dirname ${jcompiler}` # level up
java_home=`dirname ${bin}` # level up
export JAVA_HOME=${java_home}
# /scratch/rpmbuild/davmail-src-4.2.0-2066/build.xml:41: Please force UTF-8 encoding to build debian package with set ANT_OPTS=-Dfile.encoding=UTF-8
export ANT_OPTS="-Dfile.encoding=UTF-8"


# compile with OpenJFX
[ -d /usr/lib/jvm/openjfx ] && cp /usr/lib/jvm/openjfx/*.jar lib
[ -d /usr/share/java/openjfx ] && cp /usr/share/java/openjfx/*.jar lib

ant prepare-dist

%if %systemd_macros && 0%{?suse_version}
%sysusers_generate_pre %{name}-user.conf davmail %{name}-user.conf
%endif

%install
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{_bindir}
mkdir -p $RPM_BUILD_ROOT%{_sbindir}
%if !%systemd_support
mkdir -p $RPM_BUILD_ROOT%{_sysconfdir}/logrotate.d
%endif
mkdir -p $RPM_BUILD_ROOT%{_sysconfdir}/init.d
mkdir -p $RPM_BUILD_ROOT%{_datadir}/applications
mkdir -p $RPM_BUILD_ROOT%{_datadir}/pixmaps
mkdir -p $RPM_BUILD_ROOT%{_datadir}/davmail/lib
mkdir -p $RPM_BUILD_ROOT%{_localstatedir}/lib/davmail
mkdir -p $RPM_BUILD_ROOT%{_localstatedir}/log

# Init scripts, icons, configurations
install -m 0775 src/bin/davmail $RPM_BUILD_ROOT%{_bindir}/davmail
install -m 0644 src/etc/davmail.properties $RPM_BUILD_ROOT%{_sysconfdir}
# https://fedoraproject.org/wiki/TomCallaway/DesktopFileVendor
desktop-file-install --dir $RPM_BUILD_ROOT%{_datadir}/applications/ src/desktop/davmail.desktop --vendor=""

%if %systemd_support
install -D -m 644 src/init/davmail.service %{buildroot}%{_unitdir}/davmail.service
%else
install -m 0644 src/init/davmail-logrotate $RPM_BUILD_ROOT%{_sysconfdir}/logrotate.d/davmail
install -m 0775 src/init/davmail-wrapper $RPM_BUILD_ROOT%{_localstatedir}/lib/davmail/davmail
install -m 0775 src/init/davmail-init $RPM_BUILD_ROOT%{_sysconfdir}/init.d/davmail
ln -sf %{_sysconfdir}/init.d/davmail $RPM_BUILD_ROOT%{_sbindir}/rcdavmail
%endif

# Actual DavMail files
install -m 0644 src/java/tray128.png $RPM_BUILD_ROOT%{_datadir}/pixmaps/davmail.png
rm -f dist/lib/*win32*.jar
rm -f dist/lib/*x86*.jar
rm -f dist/lib/*growl*.jar
rm -f dist/lib/javafx*.jar
install -m 0664 dist/lib/* $RPM_BUILD_ROOT%{_datadir}/davmail/lib/
install -m 0664 dist/*.jar $RPM_BUILD_ROOT%{_datadir}/davmail/

mkdir -p $RPM_BUILD_ROOT%{_datadir}/metainfo
install -m 0644 src/appstream/org.davmail.DavMail.appdata.xml $RPM_BUILD_ROOT%{_datadir}/metainfo

%if %systemd_macros
mkdir -p %{buildroot}%{_sysusersdir}
install -m 0644 %{name}-user.conf %{buildroot}%{_sysusersdir}/
%endif

%if %systemd_macros
%if 0%{?suse_version}
%pre -f davmail.pre
%service_add_pre davmail.service
%endif
%else
%pre
/usr/sbin/groupadd -f -r davmail > /dev/null 2>&1 || :
/usr/sbin/useradd -r -s /sbin/nologin -d /var/lib/davmail -M \
                  -g davmail davmail > /dev/null 2>&1 || :
%endif

%post
file=/var/log/davmail.log
if [ ! -f ${file} ]
    then
    /bin/touch ${file}
fi
/bin/chown davmail:davmail ${file}
/bin/chmod 0640 ${file}

%if %systemd_macros && 0%{?suse_version}
%service_add_post davmail.service
%endif

%if %systemd_support
%else
# proper service handling http://en.opensuse.org/openSUSE:Cron_rename
%{?fillup_and_insserv:
%{fillup_and_insserv -y davmail}
}
%{!?fillup_and_insserv:
/sbin/chkconfig --add davmail
}
%endif

%preun
%if %systemd_macros
%if 0%{?suse_version}
%service_del_preun davmail.service
%else
%systemd_preun davmail.service
%endif
%endif

if [ "$1" = "0" ]; then
%if %systemd_support
%else
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
%endif
    /usr/sbin/userdel davmail
    if [ ! `grep davmail /etc/group` = "" ]; then
        /usr/sbin/groupdel davmail
    fi
fi

%postun
%if %systemd_macros
%if 0%{?suse_version}
%service_del_postun davmail.service
%else
%systemd_postun davmail.service
%endif
%endif

%if %systemd_support
%else
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
%endif

%files
%defattr (-,root,root,-)
%{_bindir}/*
%if %systemd_macros
%{_sysusersdir}/%{name}-user.conf
%endif

%if %systemd_support
%{_unitdir}/davmail.service
%else
%{_sysconfdir}/init.d/davmail
%{_sbindir}/rcdavmail
%config(noreplace) %{_sysconfdir}/logrotate.d/davmail
%endif

%config(noreplace) %{_sysconfdir}/davmail.properties
%{_datadir}/applications/*
%{_datadir}/pixmaps/*
%{_datadir}/davmail/
%{_datadir}/metainfo/org.davmail.DavMail.appdata.xml
%attr(0775,davmail,davmail) %{_localstatedir}/lib/davmail

%changelog
