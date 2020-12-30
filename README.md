# Service Commander for IBM i
A utility for unifying the daunting task of managing various services and applications running on IBM i. 

This tool can be used to manage a number of services, for instance:
- IBM i host server jobs
- IBM i standard TCP servers (*TCP, *SSHD, etc.)
- Open Source programs you wrote (Node.js, Python, PHP applications)
- Apache Tomcat instances
- Kafka, Zookeeper, ActiveMQ servers, etc
- Jenkins


# Current features
Some of the features of the tool include:
- The ability to specify dependencies (for instance, if one application or service dependds on another), and it will start any dependencies as needed
- The ability to submit jobs to batch easily, even with custom batch settings (use your own job descriptions, for instance)
- The ability to check the "liveliness" of your service by either port status or job name
- Customize the runtime environment variables of your job
- Define custom groups for your services, and perform operations on those groups (by default, a group of "all" is defined)

# Basic usage
This tool currently requires you to define any services of interest in `.yaml` files. These files can be stored in any of the following locations:
- A global directory (/QOpenSys/etc/services) 
- A user-specific directory($HOME/.sc/services) 
- If defined, whatever the value of the `services.dir` system property is. 
The file name must be in the format of `service_name.yaml`, where "service_name" is the name of the service as to be used with this tool's CLI. The service name must consist of only lowercase letters and underscores.

Usage of the command is summarized as:
```
Usage: sc  [options] <operation> <service or group:group_name>

    Valid options include:
        -v: verbose mode

    Valid operations include:
        start: start the service (and any dependencies)
        stop: stop the service (and dependent services)
        restart: restart the service
        check: check status of the service
```
The above usage assumes this program is written in a script, `sc`. This is not yet implemented. 
For now, the project can be hand-built with maven (`mvn compile`) and run with maven, specifying
arguments in `exec.args` (for instance, `mvn exec:java -Dexec.args='start kafka'`).

# Sample .yaml configuration files
See the [samples](samples) directory for some sample service definitions. 
