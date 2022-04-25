%undefine _disable_source_fetch
Name: service-commander
Version: 1.4.2
Release: 0
License: Apache-2.0
Summary: Utility for managing services and applications on IBM i
Url: https://github.com/ThePrez/ServiceCommander-IBMi

Obsoletes: sc

BuildRequires: make-gnu
BuildRequires: maven
BuildRequires: openjdk-11

Requires: bash
Requires: coreutils-gnu
Requires: db2util
Requires: nginx >= 1.16.1-4
Requires: openjdk-11
Requires: python39-ibm_db

Source0: https://github.com/ThePrez/ServiceCommander-IBMi/archive/v%{version}.tar.gz


%description
A utility for unifying the daunting task of managing various services and
applications running on IBM i. Some of the features of the tool include
management of dependent services, creating custom groups, easily submitting
to batch, and more


%prep
%setup -n ServiceCommander-IBMi-%{version} SC_VERSION=%{version}

%build
%make_build all


%install
%make_install INSTALL_ROOT=%{buildroot} SC_VERSION=%{version}


%post
# This will explicitly make sure that permissions are set correctly on the global
# configuration directory. In most cases, this is unnecessary, but it is needed
# to fix up scenarios where an older version of sc was used and a user created
# the directories manually with incorrect permissions/ownership.
if [ -e %{_sysconfdir}/sc ]; then
    chown -R qsys %{_sysconfdir}/sc
    chmod 755 %{_sysconfdir}/sc
fi
if [ -e %{_sysconfdir}/sc/services ]; then
    chmod 755 %{_sysconfdir}/sc/services
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/services/ -type f -exec chmod 644 {} \;
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/services/ -type l -exec chmod 644 {} \;
fi

if [ -e %{_sysconfdir}/sc/conf ]; then
    chmod 755 %{_sysconfdir}/sc/conf
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/conf/ -type f -exec chmod 644 {} \;
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/conf/ -type f -exec setccsid 819 {} \;
    /QOpenSys/usr/bin/find  %{_sysconfdir}/sc/conf/ -type l -exec chmod 644 {} \;
fi

%files
%defattr(-, qsys, *none)

%{_bindir}/sc*
%{_libdir}/sc
%{_libdir}/sc/native/scbash
%dir %{_sysconfdir}/sc/
%dir %{_sysconfdir}/sc/services/
%config(noreplace) %{_sysconfdir}/sc/conf/*
%config(noreplace) %{_sysconfdir}/sc/services/system/*
%config(noreplace) %{_sysconfdir}/sc/services/oss_common/*
%{_mandir}/man1/sc.1*
%{_mandir}/man1/scopenports.1*
%{_mandir}/man1/scinit.*
%{_mandir}/man1/scedit.*
%{_mandir}/man1/sc_install_defaults.*

%changelog
* Tue Apr 25 2022 Jesse Gorzinski <jgorzins@us.ibm.com> 1.4.2
- build: fixup broken chroot build

* Tue Apr 19 2022 Jesse Gorzinski <jgorzins@us.ibm.com> 1.4.0
- feature: 'reload' operation (preview, undocumented)
- feature: 'scrunattrs' operation (preview, undocumented)
- improvement: more 'perfinfo' metrics
- improvement: better log management

* Mon Apr 04 2022 Jesse Gorzinski <jgorzins@us.ibm.com> 1.3.0
- feature: Allow configuration by $HOME/.scrc file
- feature: Allow configuration by /QOpenSys/etc/sc/conf/scrc
- feature: Allow STRTCPSVR to run as different user on IPL
- feature: 'sc_install_defaults' now scans IWS configs

* Mon Mar 28 2022 Jesse Gorzinski <jgorzins@us.ibm.com> 1.2.2
- bugfix: stop issuing warnings about .rpmnew files
- improvement: more lenient wait time for Db2 WebQuery

* Mon Mar 14 2022 Jesse Gorzinski <jgorzins@us.ibm.com> - 1.2.1
- feature(major): cluster mode (experimental)
- feature: new 'sc groups' operation to list groups
- feature: Support '--version' command line option
- feature: sc_install_defaults recognizes Apache virtual hosts
- feature: '-q' option to suppress warnings
- feature: STRTCPSVR/ENDTCPSVR now shows progress
- improvement: STRTCPSVR/ENDTCPSVR run with elevated privileges
- bugfix: sc_install_defaults finding defunct Apache instances
- bugfix: sc_install_defaults --cleanupv0 cleans up Navigator

* Thu Feb 24 2022 Jesse Gorzinski <jgorzins@us.ibm.com> - 1.1.1
- bugfix: error when service dependency does not exist

* Fri Jan 07 2022 Jesse Gorzinski <jgorzins@us.ibm.com> - 1.1.0
- enhancement: 'scinit' utility automatically adds PORT env var
- deps: use Python 3.9 for fetching performance metrics

* Fri Jan 07 2022 Jesse Gorzinski <jgorzins@us.ibm.com> - 1.0.2
- enhancement: allow killing of many jobs
- deps: bump jcmdutils to v0.1.0

* Fri Dec 31 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 1.0.1
- feature(major): allow multiple ports/jobs for a service
- feature: Several new options for 'sc_install_defaults'
- feature: New '--ignore-groups' option for 'sc'
- feature: Allow service path names to be relative
- feature: preinstall crond, MariaDB, and Zend DBi
- enhancement: hide system services by default
- enhancement: sort services alphabetically on 'sc check'
- enhancement: add man pages for all utilities
- bugfix: handling single quotes for batch jobs
- bugfix: startup when originated from symlink
- deps: bump jcmdutils to v0.0.6
- deps: bump snakeyaml to v1.30

* Wed Dec 29 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.7.2
- bugfix: scopenports not handling ipv6 properly

* Wed Dec 29 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.7.1
- bugfix: sc_install_defaults produced invalid configs

* Tue Dec 21 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.7.0
- feature: add new 'scedit' command
- feature: add new 'scopenports' command
- feature: point 'sc' directly at a file
- feature: Have 'scinit' capture current environment variables

* Wed Nov 10 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.6.0
- feature: Automatically infer checkalive job name or SBMJOB job name
- feature: Issue warning when there are conflicting definitions
- bugfix: handling circular dependencies

* Fri Sep 24 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.5.0
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
