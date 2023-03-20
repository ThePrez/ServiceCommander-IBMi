# Service Commander for IBM i

## Appendix 1: Requirements for db2util and OpenJDK

### Q: Why does this program require db2util? Why can't it just work with JT400?

A: Well, the answer lies within authentication. At the time of authorship of this tool, the [JTOpen project](https://github.com/IBM/JTOpen)
(aka "JT400," among other names) does not support connecting using the special value
'*CURRENT' as the userid/password with the open source Java implementation. Rather than
requiring the user to log in somehow to make database queries (used to check for job liveliness),
it's a lot easier to use db2util.

### Q: Why does this program require OSS Java distributions? Why can't it just work with 5770JV1?

A: Well, the answer lies in how JV1 implements java.lang.Runtime.exec(). JV1's implementation, for
legacy compatibility reasons, unconditionally spawns an ILE job that will then (if needed) call
back into PASE. In this design, handling of environment variables between the parent and child
jobs is unpredictable and things can get "lost" in the transition from Java to ILE, then back to PASE
(remember that ILE and PASE maintain their own environment variable table).

The only way to cleanly implement and control the environment variable set for child processes
is to rely on the much more "normal" implementation in OpenJDK, which follows MUCH closer to
UNIX-style expectations.