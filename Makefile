


target/sc.jar: FORCE /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/mvn
	JAVA_HOME=/QOpenSys/pkgs/lib/jvm/openjdk-11 /QOpenSys/pkgs/bin/mvn package
	cp target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

all: target/sc.jar

uninstall: clean
	rm -r ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc ${INSTALL_ROOT}/QOpenSys/pkgs/bin/sc

clean:
	rm -r target man/man.1

/QOpenSys/pkgs/bin/mvn:
	yum install maven

/QOpenSys/pkgs/bin/db2util:
	yum install db2util

/QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java:
	yum install openjdk-11

/QOpenSys/pkgs/bin/nohup:
	yum install coreutils-gnu

install_runtime_dependencies: /QOpenSys/pkgs/bin/db2util /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/nohup

man/man.1: man/man.header man/man.mansrc
	rm -f man/man.1
	cat man/man.header man/man.mansrc > man/man.1

install: sc.bin target/sc.jar install_runtime_dependencies man/man.1
	install -m 755 -o qsys -D -d ${INSTALL_ROOT}/QOpenSys/pkgs/bin ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc ${INSTALL_ROOT}/QOpenSys/etc/sc ${INSTALL_ROOT}/QOpenSys/etc/sc/services
	chmod 755 ${INSTALL_ROOT}/QOpenSys/etc/sc ${INSTALL_ROOT}/QOpenSys/etc/sc/services
	chmod o+r ${INSTALL_ROOT}/QOpenSys/etc/sc/services/*
	install -m 555 -o qsys sc.bin ${INSTALL_ROOT}/QOpenSys/pkgs/bin/sc
	install -m 444 -o qsys target/sc.jar ${INSTALL_ROOT}/QOpenSys/pkgs/lib/sc/sc.jar
	install -m 444 -o qsys -D man/man.1 ${INSTALL_ROOT}/QOpenSys/pkgs/share/man/man1/sc.1

