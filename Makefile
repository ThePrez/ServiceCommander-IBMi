


target/sc.jar: FORCE
	mvn package
	mv target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

all: target/sc.jar

uninstall: clean
	rm -r /QOpenSys/pkgs/lib/sc /QOpenSys/pkgs/bin/sc

clean:
	rm -r target

/QOpenSys/pkgs/bin/db2util:
	yum install db2util

/QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java:
	yum install openjdk-11


install_runtime_dependencies: /QOpenSys/pkgs/bin/db2util /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java

install: sc.bin target/sc.jar install_runtime_dependencies
	install -m 555 -o qsys sc.bin /QOpenSys/pkgs/bin/sc
	mkdir -p /QOpenSys/pkgs/lib/sc
	chmod 755 /QOpenSys/pkgs/lib/sc
	install -m 444 target/sc.jar -o qsys /QOpenSys/pkgs/lib/sc/sc.jar
	chown -R qsys /QOpenSys/pkgs/lib/sc


	
