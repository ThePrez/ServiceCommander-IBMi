


target/sc.jar: FORCE
	mvn package
	mv target/sc-*-with-dependencies.jar target/sc.jar

FORCE:

all: target/sc.jar

uninstall: clean
	rm -r /QOpenSys/pkgs/lib/sc /QOpenSys/pkgs/bin/sc

clean:
	rm -r target

install: sc.bin target/sc.jar
	install -m 555 -o qsys sc.bin /QOpenSys/pkgs/bin/sc
	mkdir -p /QOpenSys/pkgs/lib/sc
	chmod 755 /QOpenSys/pkgs/lib/sc
	install -m 444 target/sc.jar -o qsys /QOpenSys/pkgs/lib/sc/sc.jar
	chown -R qsys /QOpenSys/pkgs/lib/sc


	
