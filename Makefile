
SC_VERSION := "Development Build (built with Make)"

JAVA_SRCS := $(shell find src -type f)
target/sc.jar: ${JAVA_SRCS}
	JAVA_HOME=/QOpenSys/pkgs/lib/jvm/openjdk-11 /QOpenSys/pkgs/bin/mvn -Dsc.version=${SC_VERSION} package
	cp target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

target/scbash: native/scbash.c
	mkdir -p target
	gcc -o target/scbash native/scbash.c

target/screbash: native/scbash.c
	mkdir -p target
	gcc -DAUTO_RESTART=1 -o target/screbash native/scbash.c

all: target/sc.jar target/scbash target/screbash

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

install_runtime_dependencies: /QOpenSys/pkgs/bin/db2util /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/nohup /QOpenSys/pkgs/bin/mvn

install_with_runtime_dependencies: install install_runtime_dependencies

man/%.1: man/%.md
	make /QOpenSys/pkgs/bin/md2man-roff
	/QOpenSys/pkgs/bin/md2man-roff $^ > $@

man/%.1.gz: man/%.1
	gzip $^

install: scripts/sc scripts/scinit scripts/sc_install_defaults target/sc.jar target/scbash target/screbash man/sc.1 man/scopenports.1 man/scedit.1 man/sc_install_defaults.1 man/scinit.1
	install -m 755 -o qsys -D -d ${INSTALL_ROOT}/QOpenSys/pkgs/bin ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc ${INSTALL_ROOT}/QOpenSys/etc/sc  ${INSTALL_ROOT}/QOpenSys/etc/sc/conf ${INSTALL_ROOT}/QOpenSys/etc/sc/services ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system ${INSTALL_ROOT}/QOpenSys/etc/sc/services/oss_common ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	install -m 755 -o qsys -D -d ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/native
	chmod 755 ${INSTALL_ROOT}/QOpenSys/etc/sc ${INSTALL_ROOT}/QOpenSys/etc/sc/conf ${INSTALL_ROOT}/QOpenSys/etc/sc/services ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system ${INSTALL_ROOT}/QOpenSys/etc/sc/services/oss_common ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	chown -R qsys ${INSTALL_ROOT}/QOpenSys/etc/sc
	chown -R qsys ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	install -m 555 -o qsys scripts/sc ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scinit ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scedit ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/scopenports ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 555 -o qsys scripts/sc_install_defaults ${INSTALL_ROOT}/QOpenSys/pkgs/bin/
	install -m 444 -o qsys target/sc.jar ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/sc.jar
	install -m 555 -o qsys target/scbash ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/native/scbash
	install -m 555 -o qsys target/screbash ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/native/screbash
	dos2unix ${INSTALL_ROOT}/QOpenSys/pkgs/bin/sc
	cp -n samples/system_tcpsvr/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	cp -n samples/system_common/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	cp -n samples/host_servers/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/system
	cp -n samples/oss_common/* ${INSTALL_ROOT}/QOpenSys/etc/sc/services/oss_common
	cp -n conf/* ${INSTALL_ROOT}/QOpenSys/etc/sc/conf
	setccsid 819 ${INSTALL_ROOT}/QOpenSys/etc/sc/conf/* || echo "unable to set CCSID of configuration files"
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/ -type f -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/ -type l -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/ -type f -print -exec chown qsys {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/etc/sc/ -type l -print -exec chown qsys {} \;
	install -m 444 -o qsys -D man/*.1 ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/
	install -m 555 -o qsys -D strtcpsvr/install_sc_tcpsvr ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/install_sc_tcpsvr
	install -m 555 -o qsys -D strtcpsvr/remove_sc_tcpsvr ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/remove_sc_tcpsvr
	install -m 444 -o qsys -D strtcpsvr/sc_tcpsvr.c ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/tcpsvr/sc_tcpsvr.c
	cp -R samples/ ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type f -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type l -print -exec chmod 644 {} \;
	/QOpenSys/usr/bin/find  ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/samples/ -type d -print -exec chmod 755 {} \;
	
