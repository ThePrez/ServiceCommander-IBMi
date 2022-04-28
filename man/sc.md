---
nav_exclude: true
---
sc 1 "January 2022" IBMi "Service Commander for IBM i"
=======================================
# NAME
sc - a tool for managing configured services and applications

# SYNOPSIS
`sc  [options] <operation> <service(s)>`

# DESCRIPTION

A utility for unifying the daunting task of managing various services and applications running on IBM i. Its objective is to provide an intuitive, easy-to-use command line interface for managing services or jobs. It also provides integration with `STRTCPSVR`.

This tool can be used to manage a number of services, for instance:

- IBM i host server jobs
- IBM i standard TCP servers (*TCP, *SSHD, etc.)
- Programs you wrote using open source technology (Node.js, Python, PHP, etc.)
- Apache Tomcat instances
- Apache Camel routes
- Kafka, Zookeeper, ActiveMQ servers, etc
- Jenkins
- The Cron daemon
- OSS Database servers (PostgreSQL, MariaDB)

# Current features
Some of the features of the tool include:

- The ability to specify dependencies (for instance, if one application or service dependds on another), and it will start any dependencies as needed
- The ability to submit jobs to batch easily, even with custom batch settings (use your own job description or submit as another user, for instance)
- The ability to check the "liveliness" of your service by either port status or job name
- Customize the runtime environment variables of your job
- Define custom groups for your services, and perform operations on those groups (by default, a group of "all" is defined)
- Query basic performance attributes of the services
- Assistance in providing/managing log files. This is a best-guess only and naively assumes the service uses stdout/stderr as its logging mechanism. Service Commander has its own primitive logging system that works well only for certain types of services
- Ability to define manage ad hoc services specified on the command line
- Ability to see what ports are currently opening (have a job listening)

# Important differences from other service management tools
Service Commander's design is fundamentally different from other tools that accomplish similar tasks, like init.d, supervisord, and so on. Namely, the functions within Service Commander are intended to work regardless of:

- Who else may start or stop the service
- What other tools may be used to start or stop the service. For instance, Service Commander may start/stop an IBM i host server, but so could the `STRHOSTSVR`/`ENDHOSTSVR` CL commands.
- Whether the service runs in the initially spawned job or a secondary job

Also, this tool doesn't have the privilege of being the unified, integrated solution with the operating system that other tools may have. Therefore, Service Commander cannot take the liberty of assuming that it can keep track of the resources tied to the services that it manages. So, for example, this tool does not keep track of process IDs of launched processes. Similarly, it doesn't have special access to kernel data structures, etc. 

Instead, this tool makes strong assumptions based on checks for a particular job name or port usage (see `check_alive_criteria` in the file format documentation). A known limitation, therefore, is that Service Commander may mistake another job for a configured service based on one of these attributes. For example, if you configure a service that is supposed to be listening on port 80, Service Commander will assume that any job listening on port 80 is indeed that service.

Service Commander's unique design is intended to offer a great deal of flexibility and ease of management through the use of simple `.yaml` files.

# Configuring Services

## Initializing your configuration with defaults

If you'd like to start with pre-made configurations for common services, simply run the
`sc_install_defaults` command. Its usage is as follows:

```
usage: sc_install_defaults [options]

    valid options include:
        -h            : display help
        --apache      : autocreate from apache instances (default)
        --cleanupv0   : clean up files created by v0 (default)
        --noapache    : don't autocreate from apache instances
        --nocleanupv0 : don't clean up files created by v0
        --global      : install for all users
        --user        : install for current user (default)
```

This utility can be used to install This will install service definitions for:
- The Cron daemon (if you have cron installed)
- MariaDB (if you have mariadb installed)
- IBM i HTTP Server (DG1) instances (unless you specify `--noapache`)

** Important Note**
If you ran this tool with v0.x, you will want to clean up the old configurations by running:

```
sc_install_defaults --cleanupv0
```


## Using the 'scinit' tool
You can use the `scinit` tool can be used to create the YAML configuration files for you. Basic usage of the tool is simply:

```
scinit <program start command>
```

The idea is that you would simply:

1. `cd` to the directory where you'd normally start the service
2. Run the command you'd normally use to start the service, prefixed by `scinit`
3. Answer a series of questions about how you would like the service deployed
In doing so, the `scinit` will create the YAML configuration file for you and also show you information about the newly-configured service.

For instance, if you would normally launch a Node.js application from `/home/MYUSR/mydir` by running `node app.js`, you would run:

```
cd /home/MYUSR/mydir
scinit <program start command>
```

The `scinit` tool will ask you for a "short name" among other things. When done, a service configuration will be saved under that short
name. So, for instance, if your short name is "my_node_app", you can run `sc start my_node_app`.

## Ad hoc service definition
Ad hoc services can be specified on the sc command line in the format `job:jobname` or `port:portname`. 
In these instances, the operations will be performed on the specified jobs. This is determined by looking for
jobs matching the given job name or listening on the given port. The job name can be specified either in
`jobname` or `subsystem/jobname` format.

If an existing service definition is found (configured via YAML, as in the preceding section) that matches the
job name or port criteria, that service will be used. For instance, if you have a service configured to run on
port 80, then specifying `sc info port:80` will show information about the service configured to run on port 80.

Ad hoc service definition is useful for quick checks without the need to create a YAML definition. It's also
useful if you do not recall the service name, but remember the job name or port. 

It is also useful for cases where you just want to find out who (if anyone) is using a certain port. For instance,
`sc jobinfo port:8080` will show you which job is listening on port 8080. Similarly, `sc stop port:8080` will kill
whatever job is running on port 8080.

## Directly creating/editing YAML configuration files
This tool allows you to define any services of interest in `.yaml` files. These files can be stored in any of the following locations:

- A global directory (/QOpenSys/etc/sc/services). This, of coures, requires you to have admin access (`*ALLOBJ` special authority).
- A user-specific directory($HOME/.sc/services)
- If defined, whatever the value of the `services.dir` system property is. 
The file name must be in the format of `service_name.yaml` (or `service_name.yml`), where "service_name" is the "simple name" of the service as to be used with this tool's CLI. The service name must consist of only lowercase letters, numbers, hyphens, and underscores.

The file can also be located in any arbitrary directory, but it must be explicitly passed along to the `sc` command, for instance

```
sc start myservice.yml
```

### YAML File Format
See the [samples](https://github.com/ThePrez/ServiceCommander-IBMi/tree/main/samples) directory for some sample service definitions. 
The following attributes may be specified in the service definition (`.yaml`) file:

**Required fields**

- `start_cmd`: the command used to start the service
- `check_alive`: How to check whether the service is alove or not. This can be a port number, or a job name in either the the format "jobname" or "subsystem/jobname". To specify multiple criteria, just use a comma-separated list or a YAML String array. 

**Optional fields that are often needed/wanted**

- `name`: A "friendly" name of the service
- `dir`: The working directory in which to run the startup/shutdown commands

**Other optional fields**

- `stop_cmd`: The service shutdown command. If unspecified, the service will be located by port number or job name.
- `startup_wait_time`: The wait time, in seconds, to wait for the service to start up (the default is 60 seconds if unspecified)
- `stop_wait_time`: The wait time, in seconds, to wait for the service to stop (the default is 45 seconds if unspecified)
- `log_dir`: The directory for storing log files created by Service Commander
- `cluster`: Enable cluster mode by providing a comma-separated list of ports (see "Cluster Mode," below)
- `batch_mode`: Whether or not to submit the service to batch
- `sbmjob_jobname`: If submitting to batch, the custom job name to be used for the batch job
- `sbmjob_opts`: If submitting to batch, custom options for the SBMJOB command (for instance, a custom JOBD) 
- `environment_is_inheriting_vars`: Whether the service inherits environment variables from the current environment (default is true)
- `only_if_executable`: This configuration is ignored unless the specified file exists and is executable
- `environment_vars`: Custom environment variables to be set when launching the service. Specify as an array of strings in `"KEY=VALUE"` format
- `service_dependencies`: An array of services that this service depends on. This is the simple name of the service (for instance, if the dependency is defined as "myservice", then it is expected to be defined in a file named `myservice.yaml`), not the "friendly" name of the service.
- `groups`: Custom groups that this service belongs to. Groups can be used to start and stop sets of services in a single operation. Specify as an array of strings.

**Deprecated fields**
- `check_alive_criteria`: (Deprecated)The criteria used when checking whether the service is alive or not. If `check_alive` is set to "port", this is expected to be a port number. If `check_alive` is set to "jobname", this is expect to be be a job name, either in the format "jobname" or "subsystem/jobname". This field is deprecated. As of v1.0.0, the `check_alive` field handles both port numbers and job names (or a list containing both).

### YAML file example
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

## Cluster Mode
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
9336, and 9337.

### Prerequisites for Cluster Mode

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

### Cluster mode methodologies

There are two methodologies that can be used for the load-balancing activity:
1. **http**: This methodology has more customization options (for instance, microcaching, handling http headers, "sticky" sessions, etc) but only works with the http protocol. To enable, you must manually edit the "cluster.conf" file that is created when your service is first started.
2. **stream** _(default)_: This methodology has less overhead than 'http', but also has fewer configuration options. However, it works with most protocols. 

### Cluster mode advanced configuration

More advanced configuration can be achieved in one of two ways:

**Defining `cluster_opts` in the service configuration** 
_NOT YET SUPPORTED_

**cluster.conf**
When a service is first started in cluster mode, a `cluster.conf` file is created in the service's working directory. Cluster mode is built on top of nginx,
and this file is the nginx configuration file. Once `cluster.conf` is created, you can feel free to edit it in any way that is supported by nginx.
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
  upstream python_servers {
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
      proxy_pass http://python_servers;
    }
    location /tablesorter {
      autoindex on;
      alias tablesorter/;
    }
  }
}
```

## Testimonials
*"I use this a lot for my own personal use. Might be useless for the rest of the world. I don't know, though."*

 &nbsp; --[@ThePrez](https://github.com/ThePrez/), creator of Service Commander
 
# OPTIONS

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
        loginfo: get log file info for the service (best guess only)
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

# Automatically restarting a service if it fails
Currently, this tool doees not have built-in monitoring and restart capabilities. This may be a future enhancement. In the meantime, one can use simple scripting to accomplish a similar task. For instance, to check every 40 seconds and ensure that the `navigator` service is running, you could submit a job like this (replace the sleep time, service name, and submitted job name to match your use case):

```
SBMJOB CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/usr/bin/sh' '-c' 'while :; do sleep 40 && /QOpenSys/pkgs/bin/sc start navigator >/dev/null 2>&1 ; done')) JOB(NAVMON) JOBD(*USRPRF) JOBQ(QUSRNOMAX)                         
```

This will result in several jobs that continuously check on the service and attempt to start it if the service is dead. If you wish to stop this behavior, simply kill the jobs. In the above example, the job name is `NAVMON`, so the WRKACTJOB command to do this interactively looks like:

```
 WRKACTJOB JOB(NAVMON) 
```

# EXAMPLES
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

List all services in the special "system" group

```
sc list group:system
```

List all services including those in the special "system" group

```
sc -a list group:all
```

List jobs running on port 8080

```
sc jobinfo port:8080
```

Stop jobs running on port 8080

```
sc stop port:8080
```

Check if anything is running on port 8080

```
sc check port:8080
```

Start the service defined in a local file, `myservice.yml`

```
sc start myservice.yml
```

See what ports are currently listening

```
scopenports
```
