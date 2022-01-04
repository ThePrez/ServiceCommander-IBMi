---
nav_exclude: true
---
sc_install_defaults 1 "January 2022" IBMi "Install Default configs for Service Commander"
=======================================
# NAME
**sc_install_defaults** - a tool for showing which TCP/IP ports are open on an IBM i server.

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
        --noapache    : don't autocreate from apache instances
        --nocleanupv0 : don't clean up files created by v0
        --global      : install for all users
        --user        : install for current user (default)
```

** Important Note**
If you ran this tool with v0.x, you will want to clean up the old configurations by running:

```
sc_install_defaults --cleanupv0
```
