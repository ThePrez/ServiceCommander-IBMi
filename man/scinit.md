---
nav_exclude: true
---
scinit 1 "January 2022" IBMi "Initialize configs for Service Commander"
=======================================
# NAME
**scinit** - a tool for creating service definitions for Service Commander.

# SYNOPSIS
`usage: scinit <startup_command>`

# DESCRIPTION

The `scinit` command will create a service definition (for use with the
`sc` utility) from the given startup command. 

For instance, to create a service definition for a Node.js application,
one could run:

```
scinit node app.js
```

The `scinit` command will then interactively ask a series of questions.
When complete, the command will display information about the service
definition that has been created.

# OPTIONS

Usage of the command is summarized as:

```
usage: scinit <startup_command>
```
