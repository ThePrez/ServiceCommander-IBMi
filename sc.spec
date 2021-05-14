Name: sc
Version: 0.3.0
Release: 0
License: Apache-2.0
Summary: Service Commander, a utility for managing services and applications on IBM i.
Url: https://github.com/ThePrez/ServiceCommander-IBMi

BuildRequires: maven
BuildRequires: openjdk-11
BuildRequires: coreutils-gnu
BuildRequires: make-gnu
Requires: openjdk-11
Requires: bash
Requires: coreutils-gnu
Requires: db2util
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
gmake all


%install
INSTALL_ROOT=%{buildroot} gmake -e install

%files
%defattr(-, qsys, *none)

%{_bindir}/sc*
%{_libdir}/sc
%{_sysconfdir}/sc
%{_mandir}/man1/%{name}.*

%changelog
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
