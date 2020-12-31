


target/sc.jar: FORCE /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/mvn
	JAVA_HOME=/QOpenSys/pkgs/lib/jvm/openjdk-11 /QOpenSys/pkgs/bin/mvn package
	mv target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

all: target/sc.jar

uninstall: clean
	rm -r /QOpenSys/pkgs/lib/sc /QOpenSys/pkgs/bin/sc

clean:
	rm -r target

/QOpenSys/pkgs/bin/mvn:
	yum install -y maven

/QOpenSys/pkgs/bin/db2util:
	yum install -y db2util

/QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java:
	yum install -y openjdk-11

/QOpenSys/pkgs/bin/nohup:
	yum install -y coreutils-gnu

install_runtime_dependencies: /QOpenSys/pkgs/bin/db2util /QOpenSys/pkgs/lib/jvm/openjdk-11/bin/java /QOpenSys/pkgs/bin/nohup

install: sc.bin target/sc.jar install_runtime_dependencies
	install -m 555 -o qsys sc.bin /QOpenSys/pkgs/bin/sc
	mkdir -p /QOpenSys/pkgs/lib/sc
	chmod 755 /QOpenSys/pkgs/lib/sc
	install -m 444 target/sc.jar -o qsys /QOpenSys/pkgs/lib/sc/sc.jar
	chown -R qsys /QOpenSys/pkgs/lib/sc


	
