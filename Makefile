


target/sc.jar: FORCE /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/mvn
	JAVA_HOME=/QOpenSys/pkgs/lib/jvm/openjdk-11 /QOpenSys/pkgs/bin/mvn -Dproject.sc_version=${SC_VERSION} package
	cp target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

all: target/sc.jar

uninstall: clean
	rm -r ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc ${INSTALL_ROOT}/QOpenSys/pkgs/bin/sc

clean:
	rm -rf target man/*.1

/QOpenSys/pkgs/bin/mvn:
	yum install maven

/QOpenSys/pkgs/bin/db2util:
	yum install db2util

/QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java:
	yum install openjdk-11

cc/QOpenSys/pkgs/bin/nohup:
	yum install coreutils-gnu

/QOpenSys/pkgs/bin/cc:
	yum install /QOpenSys/pkgs/bin/cc libutil-devel

/QOpenSys/pkgs/lib/libutil.so:
	yum install libutil-devel

/QOpenSys/pkgs/bin/ruby:
	wget https://raw.githubusercontent.com/AndreaRibuoli/RIBY/main/andrearibuoli.repo -O ruby.temporary.repo
	cp ruby.temporary.repo /QOpenSys/etc/yum/repos.d
	yum install ruby-devel
	rm /QOpenSys/etc/yum/repos.d/ruby.temporary.repo

/QOpenSys/pkgs/bin/md2man-roff: /QOpenSys/pkgs/bin/ruby /QOpenSys/pkgs/bin/cc /QOpenSys/pkgs/lib/libutil.so 
	CC=gcc gem install md2man

install_runtime_dependencies: /QOpenSys/pkgs/bin/db2util /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/nohup

install_with_runtime_dependencies: install install_runtime_dependencies

man/%.1: man/%.md
	make /QOpenSys/pkgs/bin/md2man-roff
	/QOpenSys/pkgs/bin/md2man-roff $^ > $@

man/%.1.gz: man/%.1
	gzip $^

install: scripts/sc scripts/scinit scripts/sc_install_defaults target/sc.jar man/sc.1 man/scopenports.1 man/scedit.1 man/sc_install_defaults.1 man/scinit.1
	install -m 755 -o qsys -D -d ${INSTALL_ROOT}/QOpenSys/pkgs/bin ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc ${INSTALL_ROOT}/QOpenSys/etc/sc ${INSTALL_ROOT}/QOpenSys/etc/sc/services ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	chmod 755 ${INSTALL_ROOT}/QOpenSys/etc/sc ${INSTALL_ROOT}/QOpenSys/etc/sc/services ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	chown -R qsys ${INSTALL_ROOT}/QOpenSys/etc/sc
	chown -R qsys ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	install -m 555 -o qsys scripts/sc ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scinit ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scedit ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scopenports ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/sc_install_defaults ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 444 -o qsys target/sc.jar ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/sc.jar
	install -m 644 -o qsys samples/system_tcpsvr/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	install -m 644 -o qsys samples/system_common/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	install -m 644 -o qsys samples/host_servers/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/services/ -type f -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/services/ -type l -print -exec chmod 644 {} \;
	install -m 444 -o qsys -D man/*.1 ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	install -m 555 -o qsys -D strtcpsvr/install_sc_tcpsvr ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/install_sc_tcpsvr
	install -m 555 -o qsys -D strtcpsvr/remove_sc_tcpsvr ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/remove_sc_tcpsvr
	install -m 444 -o qsys -D strtcpsvr/sc_tcpsvr.c ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/sc_tcpsvr.c
	cp -R samples/ ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type f -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type l -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type d -print -exec chmod 755 {} \;
	
