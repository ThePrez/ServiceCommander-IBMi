// Copyright (c) 2021 Jesse Gorzinski
// Derived work of "generic server" of OSSILE project, Copyright (c) 2018 Kevin Adler
// https://github.com/OSSILE/OSSILE/blob/master/main/c_generic_server/generic_tcp.c
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

#include <pwd.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <string.h>
#include <sys/types.h>

#include <qp0ztrc.h>

#define START "*START    "
#define END "*END      "

enum
{
    RC_OK,
    RC_FAILED,
    RC_ALREADY_RUNNING
} rc_map;
typedef _Packed struct
{
    char action[10];
    char reserved[20];
    short rc;
    char instance[32];
    unsigned int startup_offset;
    unsigned int startup_len;
} parm_t;

size_t unpad_length(const char *padded, size_t length)
{
    for (; padded[length - 1] == ' '; --length)
        ; // empty body

    return length;
}

int main(int argc, char *argv[])
{
    int rc;
    char instance[33];
    parm_t *parm = (parm_t *)argv[1];

    size_t instance_len = unpad_length(parm->instance, sizeof(parm->instance));
    memcpy(instance, parm->instance, instance_len);
    instance[instance_len] = 0;
    for (int i = 0; i < instance_len; ++i)
    {
        instance[i] = tolower(instance[i]);
    }

#define ISINSTANCE(name) (instance_len == sizeof(name) - 1 && memcmp(parm->instance, name, instance_len) == 0)
    if (ISINSTANCE("*DFT"))
    {
        strcpy(instance, "group:default");
    }
    else if (ISINSTANCE("*ALL"))
    {
        strcpy(instance, "group:all");
    }
    else if (ISINSTANCE("*AUTOSTART"))
    {
        strcpy(instance, "group:autostart");
    }
    else if (parm->instance[0] == '*' || parm->instance[0] == '.')
    {
        Qp0zLprintf("Invalid instance value: %.*s\n", instance_len, parm->instance);
        parm->rc = RC_FAILED;
        return 1;
    }
    int is_batch = 1;

    struct passwd *pd;
    if (NULL != (pd = getpwuid(getuid())))
    {
        is_batch = (pd->pw_name[0] == 'Q');
    }
    char command[200];
    char sc_operation[32];

    if (0 == memcmp(parm->action, START, 10))
    {
        strcpy(sc_operation, "start");
    }
    else if (0 == memcmp(parm->action, END, 10))
    {
        strcpy(sc_operation, "stop");
    }
    else
    {
        Qp0zLprintf("Unknown operation: %.10s\n", parm->action);
        parm->rc = RC_FAILED;
        return -1;
    }

    if (0 == is_batch)
    {
        snprintf(command, sizeof(command), "CALL PGM(QP2SHELL2) PARM('/QOpenSys/pkgs/bin/bash' '-l' '-c' '/QOpenSys/pkgs/bin/sc %s %s 2>&1 | cat')", sc_operation, instance);
    }
    else
    {
        snprintf(command, sizeof(command), "SBMJOB JOBQ(QSYS/QUSRNOMAX) ALWMLTTHD(*YES) CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/pkgs/bin/bash' '-l' '-c' 'exec /QOpenSys/pkgs/bin/sc %s %s 2>&1 | cat'))", sc_operation, instance);
    }
    Qp0zLprintf("Check spooled file output for progress\n");
    rc = system(command);
    rc = rc == 0 ? RC_OK : RC_FAILED;
    parm->rc = rc;
    return rc;
}
