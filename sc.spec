Name: sc
Version: 0.1.0
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

%{_bindir}/sc
%{_libdir}/sc
%{_sysconfdir}/sc
%{_mandir}/man1/%{name}.1

%changelog
* Wed Mar 17 2021 Jesse Gorzinski <jgorzins@us.ibm.com> - 0.1.0
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
