---
nav_exclude: true
---
# NAME
**scopenports** - a tool for showing which TCP/IP ports are open on an IBM i server.

# SYNOPSIS
`scopenports  [options]`

# DESCRIPTION
The **scopenports** tool is used for showing which TCP/IP ports are open on an IBM i server.
This tool is part of the **Service Commander** for IBM i package.

**scopenports** will display the ports that are open on the server, in a table, as in the following example:

| IP Address | Port Number | Port Description |
| ---------- | ----------- | ---------------- |
| 10.0.0.2   | 22          | sshd (System Secure Shell server)             |


If you find the list is too extensive, try using the `--mine` option.  This will limit the output to only show the ports that relate to your jobs (The job user profile name, used to run the scopenports utility).

If you wish the list to show a friendly name for the port, use service commander (sc) to define the names you wish to use.

Run the `sc_install_defaults` command to initialise common defaults.

# OPTIONS

Usage of the command is summarized as:
```
Usage: scopenports  [options]

    Valid options include:
        -v: verbose mode
        --mine: only show ports that are running under your user profile