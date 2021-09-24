%undefine _disable_source_fetch
Name: service-commander
Version: 0.5.0
Release: 0
License: Apache-2.0
Summary: Utility for managing services and applications on IBM i
Url: https://github.com/ThePrez/ServiceCommander-IBMi

Obsoletes: sc

BuildRequires: coreutils-gnu
BuildRequires: db2util
BuildRequires: make-gnu
BuildRequires: maven
BuildRequires: openjdk-11
Requires: bash
Requires: coreutils-gnu
Requires: db2util
Requires: openjdk-11
Requires: python3-ibm_db

Source0: https://github.com/ThePrez/ServiceCommander-IBMi/archive/v%{version}.tar.gz


%description
A utility for unifying the daunting task of managing various services and
applications running on IBM i. Some of the features of the tool include
management of dependent services, creating custom groups, easily submitting
to batch, and more
%prep
%setup -n ServiceCommander-IBMi-%{version}

%build
%make_build all


%install
%make_install INSTALL_ROOT=%{buildroot}

%post
# This will explicitly make sure that permissions are set correctly on the global
# configuration directory. In most cases, this is unnecessary, but it is needed
# to fix up scenarios where an older version of sc was used and a user created
# the directories manually with incorrect permissions/ownership.
if [ -e %{_sysconfdir}/sc ]; then
    chown qsys %{_sysconfdir}/sc
    chmod 755 %{_sysconfdir}/sc
fi
if [ -e %{_sysconfdir}/sc/services ]; then
    chown qsys %{_sysconfdir}/sc/services
    chmod 755 %{_sysconfdir}/sc/services
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/services/ -type f -exec chmod 644 {} \;
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/services/ -type l -exec chmod 644 {} \;
fi

%files
%defattr(-, qsys, *none)

%{_bindir}/sc*
%{_libdir}/sc
%{_sysconfdir}/sc
%{_mandir}/man1/sc.*

%changelog
* Thu Sep 02 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.5.0
- Rename package to 'service-commander'
* Thu Sep 02 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.4.1
- Allow 'sc_install_defaults' to autogen definitions for apache
* Mon Aug 30 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.4.0
- Add examples for cron and mariadb
- Install example files to /QOpenSys/pkgs/lib/sc/samples
- Deliver new 'sc_install_defaults' command
* Tue Aug 24 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.3.4
- bugfix: allow port numbers greater than 9999
* Wed May 19 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.3.3
- bugfix: minor bugfixes to loginfo operation
* Sat May 15 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.3.2
- enhancement: install scriptlet to lock down permissions of existing YAML configurations
* Sat May 15 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.3.1
- bugfix: proper handling of quoted args for 'scinit'
* Fri May 14 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.3.0
- enhancement: Add 'scinit' tool
* Fri May 14 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.2.3
- enhancement: issue warning when no services are in group
* Thu May 13 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.2.2
- bugfix: Internationalize STRTCPSVR support
* Thu Apr 15 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.2.1
- bugfix: error when running perfinfo/jobinfo on non-existent service
* Thu Apr 15 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.2.0
- STRTCPSVR support (experimental)
- Add support for SC_OPTIONS and SC_TCPSVR_OPTIONS environment variables
- bugfix: setting permissions for globally defined services in /QOpenSys/etc/sc/services
- bugfix: bug related to stopping jobs running with a custom JOBQ
- new '--ignore-globals' option to only operate on user-defined services
* Wed Mar 17 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.1.0
- Performance improvement for actions that don't change state
- New "--ignore-globals" option
- Allowans for ad hoc services definition
- Better handling of services running in LIC tasks
- New "jobinfo" operation
- Allow services to be specified with either .yaml or .yml file extension
- Fix for DST variations in Java runtime configuration
* Mon Mar 15 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.0.2
- Added man pages
* Wed Mar 03 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.0.1
- initial RPM release