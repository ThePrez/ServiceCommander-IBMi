# Service Commander for IBM i
A utility for unifying the daunting task of managing various services and applications running on IBM i. 

This tool can be used to manage a number of services, for instance:
- IBM i host server jobs
- IBM i standard TCP servers (*TCP, *SSHD, etc.)
- Open Source programs you wrote (Node.js, Python, PHP applications)
- Apache Tomcat instances
- Apache Camel routes
- Kafka, Zookeeper, ActiveMQ servers, etc
- Jenkins


# Current features
Some of the features of the tool include:
- The ability to specify dependencies (for instance, if one application or service dependds on another), and it will start any dependencies as needed
- The ability to submit jobs to batch easily, even with custom batch settings (use your own job descriptions, for instance)
- The ability to check the "liveliness" of your service by either port status or job name
- Customize the runtime environment variables of your job
- Define custom groups for your services, and perform operations on those groups (by default, a group of "all" is defined)
- Query basic performance attributes of the services
- Assistance in providing/managing log files. This is a best-guess only and naively assumes the service uses stdout/stderr as its logging mechanism. Service Commander has its own primitive logging system that works well only for certain types of services

# Important differences from other service management tools
Service Commander's design is fundamentally different from other tools that accomplish similar tasks, like init.d, supervisord, and so on. Namely, the functions within Service Commander are intended to work regardless of:
- Who else may start or stop the service
- What other tools may be used to start or stop the service. For instance, Service Commander may start/stop an IBM i host server, but so could the `STRHOSTSVR`/`ENDHOSTSVR` CL commands.
- Whether the service runs in the initially spawned job or a secondary job
Therefore, Service Commander cannot take the liberty of assuming that it can keep track of the resources tied to the services that it manages. So, for example, this tool does not keep track of process IDs of launched processes

Instead, this tool makes strong assumptions based on checks for a particular job name or port usage (see `check_alive_criteria` in the file format documentation). A known limitation, therefore, is that Service Commander may mistake another job for a configured service based on one of these attributes. For example, if you configure a service that is supposed to be listening on port 80, Service Commander will assume that any job listening on port 80 is indeed that service.

# Installation
## From Source
To ensure you're getting the latest and greatest code, feel free to build from source. This process assumes your `PATH` environment variable is set up properly, otherwise:
```
PATH=/QOpenSys/pkgs/bin:$PATH
export PATH
```
The build itself can be done with the following steps:
```
yum install git ca-certificates-mozilla make-gnu
git clone https://github.com/ThePrez/ServiceCommander-IBMi/
cd ServiceCommander-IBMi
make install
```
## Binary distribution (untested)
You can install the binary distribution by copying the link to the `.rpm` file from the releases page of this project and using `yum` to install it. For instance, to install the very first release:
```
yum install https://github.com/ThePrez/ServiceCommander-IBMi/releases/download/v0.0.1/sc-0.0.1-0.ibmi7.2.ppc64.rpm
```
Of note, this RPM has not yet been tested. Feel free to evaluate and submit an issue if you encounter any problems.

# System Requirements
For most of the features of this tool, the following is required to be installed (the `make install` of the installation steps should handle these for you):
- db2util (`yum install db2util`)
- OpenJDK (`yum install openjdk-11`)
- bash (`yum install bash`)
- GNU coreutils (`yum install coreutils-gnu`)

The performance information support (`perfinfo`) has additional requirements, including:
- Python 3 with the ibm_db database connector (`yum install python3-ibm_db`)
- Required operating system support, which depends on your IBM i operating system level, as follows:
    - IBM i 7.4: included with base OS
    - IBM i 7.3: Group PTF SF99703 Level 11
    - IBM i 7.2: Group PTF SF99702 Level 23
    - IBM i 7.1 (and earlier): not supported

# Basic usage
This tool currently requires you to define any services of interest in `.yaml` files. These files can be stored in any of the following locations:
- A global directory (/QOpenSys/etc/sc/services)
- A user-specific directory($HOME/.sc/services)
- If defined, whatever the value of the `services.dir` system property is. 
The file name must be in the format of `service_name.yaml`, where "service_name" is the "simple name" of the service as to be used with this tool's CLI. The service name must consist of only lowercase letters, hyphens, and underscores.

Usage of the command is summarized as:
```
Usage: sc  [options] <operation> <service or group:group_name>

    Valid options include:
        -v: verbose mode
        --disable-colors: disable colored output
        --splf: send output to *SPLF when submitting jobs to batch (instead of log)
        --sampletime=x.x: sampling time(s) when gathering performance info (default is 1)

    Valid operations include:
        start: start the service (and any dependencies)
        stop: stop the service (and dependent services)
        restart: restart the service
        check: check status of the service
        info: print configuration info about the service
        perfinfo: print basic performance info about the service
        loginfo: get log file info for the service (best guess only)
        list: print service short name and friendly name

```
The above usage assumes the program is installed with the above installation steps and is therefore
launched with the `sc` script. Otherwise, if you've hand-built with maven (`mvn compile`), 
you can specify arguments in `exec.args` (for instance, `mvn exec:java -Dexec.args='start kafka'`).

# Usage examples
Start the service named `kafka`:
```
sc start kafka
```
Stop the service named `zookeeper`:
```
sc stop zookeeper
```
Check status of all configured services (all services belong to a special group named "all")
```
sc check group:all
```
Try to start all configured services
```
sc start group:all
```
Print information about all configured services
```
sc info group:all
```
Try to start all services in "host_servers" group
```
sc start group:host_servers
```
List all services
```
sc list group:all
```

# Automatically restarting a service if it fails
Currently, this tool doees not have built-in monitoring and restart capabilities. This may be a future enhancement. In the meantime, one can use simple scripting to accomplish a similar task. For instance, to check every 40 seconds and ensure that the `navigator` service is running, you could submit a job like this (replace the sleep time, service name, and submitted job name to match your use case):
```
SBMJOB CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/usr/bin/sh' '-c' 'while :; do sleep 40 && /QOpenSys/pkgs/bin/sc start navigator >/dev/null 2>&1 ; done')) JOB(NAVMON) JOBD(*USRPRF) JOBQ(QUSRNOMAX)                         
```
This will result in several jobs that continuously check on the service and attempt to start it if the service is dead. If you wish to stop this behavior, simply kill the jobs. In the above example, the job name is `NAVMON`, so the WRKACTJOB command to do this interactively looks like:
```
 WRKACTJOB JOB(NAVMON) 
```

# Sample .yaml configuration files
See the [samples](samples) directory for some sample service definitions. 

# YAML File Format

The following attributes may be specified in the service definition (`.yaml`) file:
### Required:
- `start_cmd`: the command used to start the service
- `check_alive`: the technique used to check whether the service is alive or not. This is either "jobname" or "port".
- `check_alive_criteria`: The criteria used when checking whether the service is alive or not. If `check_alive` is set to "port", this is expected to be a port number. If `check_alive` is set to "jobname", this is expect to be be a job name, either in the format "jobname" or "subsystem/jobname".

### Optional but often needed/wanted:
- `name`: A "friendly" name of the service
- `dir`: The working directory in which to run the startup/shutdown commands

### Optional:
- `stop_cmd`: The service shutdown command. If unspecified, the service will be located by port number or job name.
- `startup_wait_time`: The wait time, in seconds, to wait for the service to start up (the default is 60 seconds if unspecified)
- `stop_wait_time`: The wait time, in seconds, to wait for the service to stop (the default is 45 seconds if unspecified)
- `batch_mode`: Whether or not to submit the service to batch
- `sbmjob_jobname`: If submitting to batch, the custom job name to be used for the batch job
- `sbmjob_opts`: If submitting to batch, custom options for the SBMJOB command (for instance, a custom JOBD) 
- `environment_is_inheriting_vars`: Whether the service inherits environment variables from the current environment (default is true)
- `environment_vars`: Custom environment variables to be set when launching the service. Specify as an array of strings in `"KEY=VALUE"` format
- `service_dependencies`: An array of services that this service depends on. This is the simple name of the service (for instance, if the dependency is defined as "myservice", then it is expected to be defined in a file named `myservice.yaml`), not the "friendly" name of the service.
- `groups`: Custom groups that this service belongs to. Groups can be used to start and stop sets of services in a single operation. Specify as an array of strings.
