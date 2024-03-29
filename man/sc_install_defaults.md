---
nav_exclude: true
---
sc_install_defaults 1 "January 2022" IBMi "Install Default configs for Service Commander"
=======================================
# NAME
**sc_install_defaults** - a tool for installing common configurations for use
with the `sc` tool.

# SYNOPSIS
`sc_install_default  [options]`

# DESCRIPTION

The `sc_install_defaults` command can install pre-made configurations for 
common services. Once installed, these services can be managed with Service
Commander's main `sc` command. 

This utility can be used to install service definitions for:
- The Cron daemon (if you have cron installed)
- MariaDB (if you have mariadb installed)
- IBM i HTTP Server (DG1) instances (unless you specify `--noapache`)
- 
# OPTIONS

Usage of the command is summarized as:

```
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

** Important Note # 1**
Services installed with this utility will be in a special group named `autogenerated`.
This group is used by the `--cleanup` option when re-running the script.

** Important Note # 2**
If you ran this tool with v0.x, you will want to clean up the old configurations by running:

```
sc_install_defaults --cleanupv0
```
