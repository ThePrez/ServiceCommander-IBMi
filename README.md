# Service Commander for IBM i

<details>
  <summary>Index - click to show/hide...</summary>

- [Introduction](#introduction)
- [Current features](#current-features)
- [Important differences from other service management tools](#important-differences-from-other-service-management-tools)
- [Installation](#installation)
  - [System Requirements](#system-requirements)
  - [Option 1: Binary distribution](#option-1-binary-distribution)
  - [Option 2: Build from source (for development or fix evaluation)](#option-2-build-from-source-for-development-or-fix-evaluation)
  - [Option 3: Install from release on GitHub](#option-3-Install-from-release-on-GitHub)
- [Hands-on Exercise](#hands-on-exercise)
- [Basic usage](#basic-usage)
  - [Usage examples](#usage-examples)
  - [Command for checking which ports are currently open](#command-for-checking-which-ports-are-currently-open)
  - [Special `system` group (hidden by default)](#special-system-group-hidden-by-default)
  - [Specifying options in environment variables](#specifying-options-in-environment-variables)
- [Configuring Services](#configuring-services)
  - [Initializing your configuration with defaults](#initializing-your-configuration-with-defaults)
  - [Using the 'scinit' tool](#using-the-scinit-tool)
  - [Using the 'scedit' tool](#using-the-scedit-tool)
  - [Ad hoc service definition](#ad-hoc-service-definition)
  - [Directly creating/editing YAML configuration files](#directly-creatingediting-yaml-configuration-files)
    - [YAML File Format](#yaml-file-format)
      - [Required fields](#required-fields)
      - [Optional fields that are often needed/wanted](#optional-fields-that-are-often-neededwanted)
      - [Other optional fields](#other-optional-fields)
      - [Deprecated fields](#deprecated-fields)
    - [YAML file example](#yaml-file-example)
- [Advanced usage](#advanced-usage)
  - [Cluster Mode](#cluster-mode)
    - [Prerequisites for Cluster Mode](#prerequisites-for-cluster-mode)
    - [Cluster mode methodologies](#cluster-mode-methodologies)
    - [Cluster mode advanced configuration](#cluster-mode-advanced-configuration)
      - [Defining `cluster_opts` in the service configuration](#defining-cluster_opts-in-the-service-configuration)
      - [cluster.conf](#clusterconf)
  - [Automatically restarting a service if it fails](#automatically-restarting-a-service-if-it-fails)
  - [STRTCPSVR Integration](#strtcpsvr-integration)
    - [Special groups used by STRTCPSVR/ENDTCPSVR](#special-groups-used-by-strtcpsvrendtcpsvr)
    - [Running two or more STRTCPSVR commands simultaneously](#running-two-or-more-strtcpsvr-commands-simultaneously)
    - [Using with ADDJOBSCDE](#using-with-addjobscde)
  - [Customize Terminal Colors](#customize-terminal-colors)
- [Demo (video)](#demo-video)
- [Have feedback or want to contribute?](#have-feedback-or-want-to-contribute)
- [Testimonials](#testimonials)

</details>

## Introduction

A utility for unifying the daunting task of managing various services and applications running on IBM i. Its objective is to provide an intuitive, easy-to-use command line interface for managing services or jobs. It also provides integration with `STRTCPSVR`.

Service Commander can be used to manage a number of services, for instance:

- IBM i host server jobs
- IBM i standard TCP servers (\*FTP, \*SSHD, etc.)
- Programs you wrote using open source technology (Node.js, Python, PHP, etc.)
- Apache Tomcat instances
- Apache Camel routes
- Kafka, Zookeeper, ActiveMQ servers, etc
- Jenkins
- The Cron daemon
- OSS Database servers (PostgreSQL, MariaDB)

![logo](sc_logo.jpg)

## Current features

Some of the features of the tool include:

- The ability to specify dependencies (for instance, if one application or service depends on another), and it will start any dependencies as needed
- The ability to submit jobs to batch easily, even with custom batch settings (use your own job description or submit as another user, for instance)
- The ability to check the "liveliness" of your service by either port status or job name
- Customize the runtime environment variables of your job
- Define custom groups for your services, and perform operations on those groups (by default, a group of "all" is defined)
- Query basic performance attributes of the services
- Assistance in providing/managing log files. This is a best-guess only and naively assumes the service uses stdout/stderr as its logging mechanism. Service Commander has its own primitive logging system that works well only for certain types of services
- Ability to define manage ad hoc services specified on the command line
- Ability to see what ports are currently open (have a job listening)

## Important differences from other service management tools

Service Commander's design is fundamentally different from other tools that accomplish similar tasks, like init.d, supervisord, and so on. Namely, the functions within Service Commander are intended to work regardless of:

- Who else may start or stop the service
- What other tools may be used to start or stop the service. For instance, Service Commander may start/stop an IBM i host server, but so could the `STRHOSTSVR`/`ENDHOSTSVR` CL commands.
- Whether the service runs in the initially spawned job or a secondary job

Also, this tool doesn't have the privilege of being the unified, integrated solution with the operating system that other tools may have. Therefore, Service Commander cannot take the liberty of assuming that it can keep track of the resources tied to the services that it manages. So, for example, Service Commander does not keep track of process IDs of launched processes. Similarly, it doesn't have special access to kernel data structures, etc.

Instead, Service Commander makes strong assumptions based on checks for a particular job name or port usage (see `check_alive` in the file format documentation). A known limitation, therefore, is that Service Commander may mistake another job for a configured service based on one of these attributes. For example, if you configure a service that is supposed to be listening on port 80, Service Commander will assume that any job listening on port 80 is indeed that service.

Service Commander's unique design is intended to offer a great deal of flexibility and ease of management through the use of simple `.yaml` files.

## Installation

### System Requirements

For most of the features of Service Commander, the following is required to be installed (the installation steps should handle these for you):

- db2util (`yum install db2util`)
- OpenJDK (`yum install openjdk-11`)
- bash (`yum install bash`)
- GNU coreutils (`yum install coreutils-gnu`)

(click [here](docs/appendix1.md) to read more about the requirements for `db2util` and `openjdk-11`.)

The performance information support (`perfinfo`) has additional requirements that are not automatically installed, including:

- Python 3 with the ibm_db database connector (`yum install python3-ibm_db`)
- Required operating system support, which depends on your IBM i operating system level, as follows:

  - IBM i 7.4: included with base OS
  - IBM i 7.3: Group PTF SF99703 Level 11
  - IBM i 7.2: Group PTF SF99702 Level 23
  - IBM i 7.1 (and earlier): not supported

### Option 1: Binary distribution

You can install the binary distribution by installing the `service-commander` package:

```bash
yum install service-commander
```

If you are not familiar with IBM i RPMs, see [this documentation](http://ibm.biz/ibmi-rpms) to get started.

### Option 2: Build from source (for development or fix evaluation)

Feel free to build from the `main` branch to start making code contributions or to evaluate a fix/feature not yet published. This process assumes your `PATH` environment variable is set up properly, otherwise:

```bash
PATH=/QOpenSys/pkgs/bin:$PATH
export PATH
```

The build itself can be done with the following steps:

```bash
yum install git ca-certificates-mozilla make-gnu
git clone https://github.com/ThePrez/ServiceCommander-IBMi/
cd ServiceCommander-IBMi
make install_with_runtime_dependencies
```

### Option 3: Install from release on GitHub

If you need any binary distribution of Service Commander not yet in the IBM repository but available as a [release](https://github.com/ThePrez/ServiceCommander-IBMi/releases) on GitHub, you can install the release using `yum` and the URL of the RPM release file:

```bash
yum install https://github.com/ThePrez/ServiceCommander-IBMi/releases/download/v1.4.3/service-commander-1.4.3-0.ibmi7.2.ppc64.rpm
```

This will install release 1.4.3 from the RPM file on GitHub.

## Hands-on Exercise

Want to walk through a quick exercise to get some basic "hands-on" experience with this tool? If so, please see [our very simple hands-on exercise](quickstart/HANDS_ON.md)

## Basic usage

Usage of the command is summarized as:

```text
Usage: sc  [options] <operation> <service>

    Valid options include:
        -v: verbose mode
        -q: quiet mode (suppress warnings). Ignored when '-v' is specified
        --disable-colors: disable colored output
        --splf: send output to *SPLF when submitting jobs to batch (instead of log)
        --sampletime=x.x: sampling time(s) when gathering performance info (default is 1)
        --ignore-globals: ignore globally-configured services
        --ignore-groups=x,y,z: ignore services in the specified groups (default is 'system')
        --all/-a: don't ignore any services. Overrides --ignore-globals and --ignore-groups

    Valid operations include:
        start: start the service (and any dependencies)
        stop: stop the service (and dependent services)
        kill: stop the service (and dependent services) forcefully
        restart: restart the service
        check: check status of the service
        info: print configuration info about the service
        jobinfo: print basic performance info about the service
        perfinfo: print basic performance info about the service
        loginfo: get log file info for the service (if running)
        list: print service short name and friendly name
        groups: print an overview of all groups

    Valid formats of the <service(s)> specifier include:
        - the short name of a configured service
        - A special value of "all" to represent all configured services (same as "group:all")
        - A group identifier (e.g. "group:groupname")
        - the path to a YAML file with a service configuration
        - An ad hoc service specification by port (for instance, "port:8080")
        - An ad hoc service specification by job name (for instance, "job:ZOOKEEPER")
        - An ad hoc service specification by subsystem and job name (for instance, "job:QHTTPSVR/ADMIN2")

```

The above usage assumes the program is installed with the above installation steps and is therefore
launched with the `sc` script.

### Usage examples

Start the service named `kafka`:

```bash
sc start kafka
```

Stop the service named `zookeeper`:

```bash
sc stop zookeeper
```

Check status of all configured services (all services belong to a special group named "all")

```bash
sc check group:all
```

Try to start all configured services

```bash
sc start group:all
```

Print information about all configured services

```bash
sc info group:all
```

Try to start all services in "host_servers" group

```bash
sc start group:host_servers
```

List all services

```bash
sc list group:all
```

List jobs running on port 8080

```bash
sc jobinfo port:8080
```

Stop jobs running on port 8080

```bash
sc stop port:8080
```

Check if anything is running on port 8080

```bash
sc check port:8080
```

Start the service defined in a local file, `myservice.yml`

```bash
sc start myservice.yml
```

List all groups

```bash
sc groups
```

Only list groups that are defined within the users private YAML configuration files

```bash
sc groups --ignore-globals
```

### Command for checking which ports are currently open

Service Commander (>=0.7.x) also comes with command `scopenports` that allow you to see which ports are open.
Usage is as follows:

```bash
Usage: scopenports  [options]

    Valid options include:
        -v: verbose mode
        --mine: only show ports that you have listening
```

See what ports are currently listening

```bash
scopenports
```

Example output when invoked with the `--mine` option:

![image](https://user-images.githubusercontent.com/17914061/146984207-826a1f5e-5021-494e-820d-a3b44d2be20a.png)

The value in the service name column can be used with the `sc` command. For instance, with
the above example, if I wanted to see which job was running on port 62006, I could run

```bash
sc jobinfo port:62006
```

**Important Note:** Currently, `scopenports` can only show human-readable descriptions for services that have
been configured for `sc`'s use. To populate some common defaults, run `sc_install_defaults`.

### Special `system` group (hidden by default)

Service Commander ships a handful of pre-made configurations for common system services. These include things like:

- IBM i host servers
- common system services (like ftp, ssh, etc)
- Administration interfaces (like Navigator for i)

By default, the `sc` command ignores these system services. So, for instance, if you run `sc check all`, it will omit
these preconfigured system services. In order to include them, use the `-a` option, for instance `sc -a check all`.

List all services in the special "system" group

```bash
sc list group:system
```

List all services including those in the special "system" group

```bash
sc -a list group:all
```

### Specifying options in environment variables

If you would like to set some of the tool's options via environment variable, you may do so with one of the following:

- `SC_TCPSVR_OPTIONS`, which will be processed when invoked via the `STRTCPSVR`/`ENDTCPSVR` commands
- `SC_OPTIONS`, which will be processed on all invocations
For example, to gather verbose output when using `STRTCPSVR`, run the following before your `STRTCPSVR` command:

```cl
ADDENVVAR ENVVAR(SC_OPTIONS) VALUE('-v') REPLACE(*YES)
```

## Configuring Services

### Initializing your configuration with defaults

If you'd like to start with pre-made configurations for common services, simply run the
`sc_install_defaults` command. Its usage is as follows:

```bash
usage: sc_install_defaults [options]

    valid options include:
        -h            : display help
        --apache      : autocreate from apache instances (default)
        --cleanupv0   : clean up files created by v0 (default)
        --cleanup     : clean up previously-generated configs (default)
        --noapache    : don't autocreate from apache instances
        --nocleanupv0 : don't clean up files created by v0
        --nocleanup   : don't clean up previously-generated configs
        --global      : install for all users
        --user        : install for current user (default)
```

This will install service definitions for:

- The Cron daemon (if you have the cronie package installed)
- MariaDB (if you have the mariadb package installed)
- IBM i HTTP Server (DG1) instances (unless you specify `--noapache`)

**Important Note 1:**
Services installed with this utility will be in a special group named `autogenerated`.
This group is used by the `--cleanup` option when re-running the script.

**Important Note 2:**
If you ran this tool with v0.x, you will want to clean up the old configurations by running:

```bash
sc_install_defaults --cleanupv0
```

### Using the 'scinit' tool

You can use the `scinit` tool to create the YAML configuration files for you. Basic usage of the tool is simply:

```bash
scinit <program start command>
```

The idea is that you would simply:

1. `cd` to the directory where you'd normally start the service
2. Run the command you'd normally use to start the service, prefixed by `scinit`
3. Answer a series of questions about how you would like the service deployed

In doing so, the `scinit` will create the YAML configuration file for you and also show you information about the newly-configured service.

For instance, if you would normally launch a Node.js application from `/home/MYUSR/mydir` by running `node app.js`, you would run:

```bash
cd /home/MYUSR/mydir
scinit node app.js
```

The `scinit` tool will ask you for a "short name" among other things. When done, a service configuration will be saved under that short
name. So, for instance, if your short name is "my_node_app", you can run `sc start my_node_app`.

### Using the 'scedit' tool

You can use the `scedit` tool to edit the already created YAML configuration files. Basic usage of the tool is simply:

```bash
scedit <service>
```

When activated, `scedit` will locate the YAML file for the service and open the file in the first of the following editors:

1. The editor specified in the $EDITOR environment variable. This is the recommended way of specifying the editor you want out of all editors installed
2. The `nano` editor
3. The `joe` editor
4. The `vim` editor
5. The `vi` editor

### Ad hoc service definition

Ad hoc services can be specified on the `sc` command line in the format `job:jobname` or `port:portname`.
In these instances, the operations will be performed on the specified jobs. This is determined by looking for
jobs matching the given job name or listening on the given port. The job name can be specified either in
`jobname` or `subsystem/jobname` format. It can also be specified in `PGM-____` format to check for jobs running
a certain program from the main thread.

If an existing service definition is found (configured via YAML, as in the preceding section) that matches the
job name or port criteria, that service will be used. For instance, if you have a service configured to run on
port 80, then specifying `sc info port:80` will show information about the service configured to run on port 80.

Ad hoc service definition is useful for quick checks without the need to create a YAML definition. It's also
useful if you do not recall the service name, but remember the job name or port.

It is also useful for cases where you just want to find out who (if anyone) is using a certain port. For instance,
`sc jobinfo port:8080` will show you which job is listening on port 8080. Similarly, `sc stop port:8080` will kill
whatever job is running on port 8080.

### Directly creating/editing YAML configuration files

Service Commander allows you to define any services of interest in `.yaml` files. These files can be stored in any of the following locations:

- A global directory (/QOpenSys/etc/sc/services). This, of course, requires you to have admin access (`*ALLOBJ` special authority).
- A user-specific directory($HOME/.sc/services)
- If defined, whatever the value of the `services.dir` system property is.

The file name must be in the format of `service_name.yaml` (or `service_name.yml`), where "service_name" is the "simple name" of the service as to be used with this tool's CLI. The service name must consist of only lowercase letters, numbers, hyphens, and underscores.

The file can also be located in any arbitrary directory, but it must be explicitly passed along to the `sc` command, for instance:

```bash
sc start /tmp/my_service.yml
```

#### YAML File Format

See the [samples](https://github.com/ThePrez/ServiceCommander-IBMi/tree/main/samples) directory for some sample service definitions.
The following attributes may be specified in the service definition (`.yaml`) file:

##### Required fields

- `start_cmd`: The command used to start the service
- `check_alive`: How to check whether the service is alove or not. This can be a port number, or a job name in either the the format "jobname" or "subsystem/jobname". It can also be specified in `PGM-____` format to check for jobs running a certain program from the main thread. To specify
multiple criteria, just use a comma-separated list or a YAML String array.

##### Optional fields that are often needed/wanted

- `name`: A "friendly" name of the service
- `dir`: The working directory in which to run the startup/shutdown commands

##### Other optional fields

- `stop_cmd`: The service shutdown command. If unspecified, the service will be located by port number or job name.
- `startup_wait_time`: The wait time, in seconds, to wait for the service to start up (the default is 60 seconds if unspecified)
- `stop_wait_time`: The wait time, in seconds, to wait for the service to stop (the default is 45 seconds if unspecified)
- `log_dir`: The directory for storing log files created by Service Commander
- `cluster`: Enable cluster mode by providing a comma-separated list of ports (see [Cluster Mode](#cluster-mode) below)
- `batch_mode`: Whether or not to submit the service to batch
- `sbmjob_jobname`: If submitting to batch, the custom job name to be used for the batch job
- `sbmjob_opts`: If submitting to batch, custom options for the SBMJOB command (for instance, a custom JOBD)
- `environment_is_inheriting_vars`: Whether the service inherits environment variables from the current environment (default is true)
- `only_if_executable`: This configuration is ignored unless the specified file exists and is executable
- `environment_vars`: Custom environment variables to be set when launching the service. Specify as an array of strings in `"KEY=VALUE"` format
- `service_dependencies`: An array of services that this service depends on. This is the simple name of the service (for instance, if the dependency is defined as "myservice", then it is expected to be defined in a file named `myservice.yaml`), not the "friendly" name of the service.
- `groups`: Custom groups that this service belongs to. Groups can be used to start and stop sets of services in a single operation. Specify as an array of strings.

##### Deprecated fields

- `check_alive_criteria`: The criteria used when checking whether the service is alive or not. If `check_alive` is set to "port", this is expected to be a port number. If `check_alive` is set to "jobname", this is expect to be be a job name, either in the format "jobname" or "subsystem/jobname". It can also be specified in `PGM-____` format to check for jobs running a certain program from the main thread. This field is deprecated. As of v1.0.0, the `check_alive` field handles both port numbers and job names (or a list containing both).

#### YAML file example

The following is an example of a simple configuration for a Node.js application that runs on port 80:

```yaml
name: My Node.js application
dir: /home/MYUSER/myapp
start_cmd: node index.js
check_alive: '80'
batch_mode: 'false'
environment_vars:
- PATH=/QOpenSys/pkgs/bin:/QOpenSys/usr/bin:/usr/ccs/bin:/QOpenSys/usr/bin/X11:/usr/sbin:.:/usr/bin
```

## Advanced usage

### Cluster Mode

Service Commander allows for the automatic "clustering" of your applications. When utilizing "cluster mode":

- Service Commander will start _n_ worker jobs, each running on a different port
- Service Commander will manage the worker jobs when performing operations on the service
- Work is load-balanced across the worker jobs as needed

For example, imagine a service configured like this:

```yaml
name: Active Jobs Dashboard
dir: /home/JGORZINS/ibmi-oss-examples/python/active-jobs-dashboard
start_cmd: python3.9 ./server.py
check_alive: 9333
```

In standard operation, this example would start up a Python web server that listens on port 9333.
Cluster mode can be easily enabled with the `cluster` value. The `cluster` value provides a set of ports
for the worker jobs to listen on. The number of backend workers is simply based on the quantity of ports specified
in this property.

In this example, we run the same Python web server with cluster mode, using 4 backend jobs:

```yaml
name: Active Jobs Dashboard
dir: /home/JGORZINS/ibmi-oss-examples/python/active-jobs-dashboard
start_cmd: python3.9 ./server.py
cluster: 9334,9335,9336,9337
check_alive: 9333
```

The application is still expected to run on 9333, so in the case of a web server, it would still run at
`http://<system_name>:9333`. Service Commander will run four backend worker jobs, running on ports 9334, 9335,
9336 and 9337.

#### Prerequisites for Cluster Mode

In order for cluster mode to work correctly, your application must honor the `PORT` environment variable. If the
technology has the ability to run on dynamically-defined ports but cannot recognize `PORT`, then the program startup
can be wrapped in a script that transposes the environment variable to a command line option. For instance:

```bash
#!/QOpenSys/pkgs/bin/bash
exec ./startup.sh --port=$PORT
```

In case the application requires more than one port, Service Commander also provides these environment variables to the
backend worker jobs, which can then be used to run the different components of the backend worker with different ports:

- `PORT_PLUS_1`
- `PORT_PLUS_2`
- `PORT_PLUS_3`
- `PORT_PLUS_4`
- `PORT_PLUS_5`

To avoid collusions with other backend worker jobs, leave the necessary gaps between ports. For instance, if your application
uses three ports, specify the backend worker jobs 3 ports apart. For instance, `cluster: 8000, 8003, 8006, 8009`.

#### Cluster mode methodologies

There are two methodologies that can be used for the load-balancing activity:

1. **http**: This methodology has more customization options (for instance, microcaching, handling http headers, "sticky" sessions, etc) but only works with the http protocol. To enable, you must manually edit the "cluster.conf" file that is created when your service is first started.
2. **stream** _(default)_: This methodology has less overhead than 'http', but also has fewer configuration options. However, it works with most protocols.

#### Cluster mode advanced configuration

More advanced configuration can be achieved in one of two ways:

##### Defining `cluster_opts` in the service configuration

This is _NOT YET SUPPORTED_

##### cluster.conf

When a service is first started in cluster mode, a `cluster.conf` file is created in the service's working directory. Cluster mode is built on top of `nginx`,
and this file is the nginx configuration file. Once `cluster.conf` is created, you can feel free to edit it in any way that is supported by `nginx`.
For instance, this example:

- uses the **http** methodology for load balancing
- Enables 10-second request caching
- Enables a `/tablesorter` directory for serving static content

```nginx
pid nginx.pid;
events {}
http {
  error_log logs/error.log warn;
  proxy_cache_path /tmp/cache keys_zone=cache:10m levels=1:2 inactive=600s max_size=100m;
  upstream sc_servers {
    server 127.0.0.1:3341;
    server 127.0.0.1:3342;
  }
  server {
    proxy_cache cache;
    proxy_cache_lock on;
    proxy_cache_valid 200 10s;
    proxy_cache_methods GET HEAD POST;
    proxy_cache_use_stale updating error timeout http_500 http_502 http_503 http_504;
    proxy_buffering on;
    listen 9333 backlog=8096;
    location / {
      proxy_pass http://sc_servers;
    }
    location /tablesorter {
      autoindex on;
      alias tablesorter/;
    }
  }
}
```

### Automatically restarting a service if it fails

Currently, Service Commander does not have built-in monitoring and restart capabilities. This may be a future enhancement. In the meantime, one can use simple scripting to accomplish a similar task. For instance, to check every 40 seconds and ensure that the `navigator` service is running, you could submit a job like this (replace the sleep time, service name, and submitted job name to match your use case):

```cl
SBMJOB CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/usr/bin/sh' '-c' 'while :; do sleep 40 && /QOpenSys/pkgs/bin/sc start navigator >/dev/null 2>&1 ; done')) JOB(NAVMON) JOBD(*USRPRF) JOBQ(QUSRNOMAX)
```

This will result in several jobs that continuously check on the service and attempt to start it if the service is dead. If you wish to stop this behavior, simply kill the jobs. In the above example, the job name is `NAVMON`, so the WRKACTJOB command to do this interactively looks like:

```cl
 WRKACTJOB JOB(NAVMON)
```

### STRTCPSVR Integration

Service Commander now has integration with system STRTCPSVR and ENDTCPSVR commands.

To integrate with the STRTCPSVR and ENDTCPSVR commands, you can run the following command as an admin user:

```bash
/QOpenSys/pkgs/lib/sc/tcpsvr/install_sc_tcpsvr
```

This will create the `SCOMMANDER` library and compile/install the TCP program into that library. To use a different
library, just set the `SCTARGET` variable. For instance:

```bash
SCTARGET=mylib /QOpenSys/pkgs/lib/sc/tcpsvr/install_sc_tcpsvr
```

If you need to compile to a previous release of IBM i, set the `SCTGTRLS` variable to the required value of CRTCMOD parameter TGTRLS. Example for IBM i 7.1:

```bash
SCTGTRLS=V7R1M0 /QOpenSys/pkgs/lib/sc/tcpsvr/install_sc_tcpsvr
```

After install, you can run the `*SC` TCP server commands, specifying the simple name of the sc-managed service as the instance name. For example:

```cl
STRTCPSVR SERVER(*SC) INSTANCE('kafka')
```

**Important Note: As of Service Commander v1.2.x, the TCP server is set to `AUTOSTART(*YES)` by default. If this
is not the desired behavior, change it with the CHGTCPSVR command. For instance:**
```bash
CHGTCPSVR SVRSPCVAL(*SC) AUTOSTART(*NO)
```

#### Special groups used by STRTCPSVR/ENDTCPSVR

There are a couple special groups used by the TCP server support. You can define your services to be members of one or more of these groups:

- `default`, which is what's started or ended if no instance is specified (i.e. `STRTCPSVR SERVER(*SC)`)
- `autostart`, which is what's started when invoked on the `*AUTOSTART` instance (i.e. `STRTCPSVR SERVER(*SC) INSTANCE(*AUTOSTART)`)
- `system`, which contains the system services and is used when invoked on anything but the `*ALL` instance. This makes it possible to start or stop a system service using ServiceCommander. For instance, to end the NetServer service, run `ENDTCPSVR SERVER(*SC) INSTANCE('system_netserver')`.

#### Running two or more STRTCPSVR commands simultaneously

Be aware that running two or more STRTCPSVR commands at the same time in different jobs can cause the command to fail with TCP1A11. This is because the system will only run one STRTCPSVR command at a time and uses an internal locking mechanism to control this. The wait time is 30 seconds, and if STRTCPSVR in job A is taking longer to start the service, the STRTCPSVR in job B and C etc. will time out when aquiring the lock.

If you need to run more than one STRTCPSVR *SC command at a time (e.g. after IPL where the system is busy and the service can take longer to start), you can reduce the lock time significantly by setting an environment variable before running the STRTCPSVR command:

```cl
ADDENVVAR ENVVAR(SC_TCPSVR_SUBMIT) VALUE('Y') LEVEL(*SYS) REPLACE(*YES)
```

When STRTCPSVR detects the environment variable having the value 'Y', it will submit a job to start the service instead of starting the service in the job running the STRTCPSVR command, thus shortening the lock time significantly and allow the same command in other jobs to run and not time out.

#### Using with ADDJOBSCDE

It may be desired to start, stop, or ensure the liveliness of services on a particular schedule. This is most easily accomplished once the `STRTCPSVR`
integration is leveraged. This makes it easier to create job scheduler entries. For instance, to ensure that the `myapp` service is
running, every day at 01:00:

```cl
ADDJOBSCDE JOB(SC) CMD(STRTCPSVR SERVER(*SC) INSTANCE('myapp')) FRQ(*WEEKLY) SCDDATE(*NONE) SCDDAY(*ALL) SCDTIME(010000)
```

### Customize terminal Colors

ServiceCommander offers color support for the terminal output in different contexts, such as displaying the list of running and non-running services with `sc check`. Service Commander supports two configuration files where command line options can be configured:

- `/QOpenSys/etc/sc/scrc (global)`
- `$HOME/.scrc (individual)`

In order to configure terminal colors, add the following command with a comma separated list of `CONTEXT:COLOR`:

```cl
--color-scheme=[CONTEXT:COLOR], [CONTEXT:COLOR]
```

Example:

```cl
--color-scheme=NOT_RUNNING:BRIGHT_RED, INFO:YELLOW
```

Here is a list of the different contexts and default colors:

```cl
Terminal Color defaults and contexts used in sc:
      RUNNING: GREEN
      NOT_RUNNING: PURPLE
      INFO: CYAN
      WARNING: YELLOW
      ERROR: BRIGHT_RED
      PLAIN: WHITE
      STATUS: BLUE
```

Here is a list of the avalaible colors for customization:
```cl
- BLUE
- BRIGHT_RED
- CYAN
- GREEN
- PURPLE
- RED
- WHITE
- YELLOW
```

## Demo (video)

[![asciicast](https://asciinema.org/a/459898.svg)](https://asciinema.org/a/459898)

## Have feedback or want to contribute?

Feel free to [open an issue](https://github.com/ThePrez/ServiceCommander-IBMi/issues/new/choose) with any questions, problems, or other comments. If you'd like to contribute to the project, see [CONTRIBUTING.md](https://github.com/ThePrez/ServiceCommander-IBMi/blob/main/CONTRIBUTING.md) for more information on how to get started.

In any event, we're glad to have you aboard in any capacity, whether as a user, spectator, or contributor!

Thanks to everybody who has contributed so far!


<a href="https://github.com/ThePrez/ServiceCommander-IBMi/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=ThePrez/ServiceCommander-IBMi" />
</a>

Made with [contrib.rocks](https://contrib.rocks).

## Testimonials

> "I use this a lot for my own personal use. Might be useless for the rest of the world. I don't know, though."
>
> &nbsp; --[@ThePrez](https://github.com/ThePrez/), creator of Service Commander
>
> "Service Commander is a great tool that has helped us controlling the rising number of services, that we run on IBM i. Previously we had different commands for different services, but now we just define the services in Service Commander and let it control the (auto)start and stop of the services. Not at all useless!"
>
> &nbsp; --[@chrjorgensen](https://github.com/chrjorgensen/), IBM i System Administrator and contributor to Service Commander
>
> "A fantastic tool to manage our many TCPSVR and Node.js instances. It's got a great command line interface allowing us to run `sc` through our shell scripts. "
>
> &nbsp; --[@KerimG](https://github.com/KerimG), IBM i software developer and PASE for i enthusiast
>
> "Service Commander is a phenomenal set of tools for IBM i. We use it and promote it in all our training courses, and it truly deserves to be a part of the IBM i OS build. It's that great!"
>
> &nbsp; --[@AndyYouens](https://github.com/AndyYouens), Managing Director [FormaServe Systems Ltd](https://www.formaserve.co.uk), &amp; Editor of [PowerWire](https://powerwire.uk)  &amp; contributor to Service Commander
