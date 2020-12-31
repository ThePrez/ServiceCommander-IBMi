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

# Installation
Assumes your `PATH` environment variable is set up properly, otherwise:
```
PATH=/QOpenSys/pkgs/bin:$PATH
export PATH
```
```
yum install git ca-certificates-mozilla make-gnu
git clone https://github.com/ThePrez/ServiceCommander-IBMi/
cd ServiceCommander-IBMi
make install
```


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
The above usage assumes the program is installed with the above installation steps and is therefore
launched with the `sc` script. Otherwise, if you've hand-built with maven (`mvn compile`), 
you can specify arguments in `exec.args` (for instance, `mvn exec:java -Dexec.args='start kafka'`).

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

### Optional
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
