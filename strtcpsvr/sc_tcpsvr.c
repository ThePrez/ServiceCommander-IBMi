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
#include <ctype.h>
#include <stddef.h>
#include <string.h>
#include <sys/types.h>
#include <iconv.h>
#include <qtqiconv.h>
#include <qp0ztrc.h>
#include <qusrjobi.h>
#include <spawn.h>
#include <errno.h>
#include <fcntl.h>
#pragma convert(37)
#define TRUE 1
#define FALSE 0
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
    for (int i = 0; i < length; ++i)
    {
        if (padded[i] == '\0' || padded[i] == ' ')
        {
            return i;
        }
    }
    return length;
}

void to_job_ccsid(char *out, size_t out_len, char *in)
{
    QtqCode_T compile_ccsid = {37, 0, 0, 0, 0, 0};
    QtqCode_T job_ccsid = {0, 0, 0, 0, 0, 0};
    iconv_t cd = QtqIconvOpen(&job_ccsid, &compile_ccsid);
    if (cd.return_value == -1)
    {
        fprintf(stderr, "Error in opening conversion descriptors\n");
        exit(8);
    }

    size_t inleft = strlen(in);
    size_t outleft = out_len;
    char *input = in;
    char *output = out;

    int rc = iconv(cd, &input, &inleft, &output, &outleft);
    if (rc == -1)
    {
        fprintf(stderr, "Error in converting characters\n");
        exit(8);
    }
    iconv_close(cd);
}

char *opt_from_config(char *opt)
{
    int max_line_len = 1024;
    char line[max_line_len];
    char *buf = malloc(1 + max_line_len);
    strcpy(buf, "");
    int fd = open("/QOpenSys/etc/sc/conf/strtcpsvr.conf", O_RDONLY | O_TEXTDATA);
    if (fd == -1)
    {
        Qp0zLprintf("Error opening /QOpenSys/etc/sc/conf/strtcpsvr.conf: %s\n", strerror(errno));
        return buf;
    }
    memset(line, 0, sizeof(line));
    int opt_len = strlen(opt);
    int bytesRead = -1;
    char *linePtr = line;
    int rc = -1;
    while ((rc = read(fd, linePtr, 1)) > 0)
    {
        int pos = linePtr - line;
        if (*linePtr == '\n' || pos >= (sizeof(line) - 1))
        {
            *linePtr = '\0';
            if (strncmp(line, opt, opt_len) == 0)
            {
                strcpy(buf, line + opt_len);
            }
            linePtr = line;
            memset(line, 0, sizeof(line));
        }
        else
        {
            linePtr++;
        }
    }
    close(fd);
    if (strncmp(line, opt, opt_len) == 0)
    {
        strcpy(buf, line + opt_len);
    }
    return buf;
}

int is_qtcp()
{
    struct passwd *pd;
    if (NULL != (pd = getpwuid(getuid())))
    {
        return (0 == strcmp("QTCP", pd->pw_name));
    }
    return FALSE;
}

int is_batch()
{
    char buffer[128];
    memset(buffer, 0x00, sizeof(buffer));
    // Run in batch mode if we're in a non-interactive job
    QUSRJOBI(buffer, sizeof(buffer), "JOBI0100", "*                         ",
             "                ");
    int is_batch = ('I' != buffer[60]);

    // Run in batch mode if user profile starts with 'Q'
    struct passwd *pd;
    if (0 != is_qtcp())
    {
        is_batch = TRUE;
    }

    // .. or override with SC_TCPSVR_SUBMIT environment variable
    char *sc_submit = getenv("SC_TCPSVR_SUBMIT");
    if (NULL == sc_submit)
    {
        sc_submit = "";
    }
    if (0 == memcmp(sc_submit, "Y", 1))
    {
        is_batch = TRUE;
    }
    else if (0 == memcmp(sc_submit, "N", 1))
    {
        is_batch = FALSE;
    }
    return is_batch;
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
    int is_ipl = FALSE;
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
        is_ipl = is_qtcp();
        strcpy(instance, "group:autostart");
    }
    else if (parm->instance[0] == '*' || parm->instance[0] == '.')
    {
        Qp0zLprintf("Invalid instance value: %.*s\n", instance_len, parm->instance);
        parm->rc = RC_FAILED;
        return 1;
    }

#define CMD_MAX 333
    char command[CMD_MAX];
    char command_printf_fmt[CMD_MAX];
    char sc_operation[32];
    char *sc_options = getenv("SC_TCPSVR_OPTIONS");
    if (NULL == sc_options)
    {
        sc_options = "";
    }

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
    if (0 != is_batch())
    {
        memset(command_printf_fmt, 0x00, sizeof(command));
        char *sbmjob_opts = is_ipl ? opt_from_config("ipl_sbmjob_opts:") : opt_from_config("sbmjob_opts:");
        to_job_ccsid(command_printf_fmt, sizeof(command_printf_fmt) - 1,
                     "SBMJOB JOBQ(QSYS/QUSRNOMAX) ALWMLTTHD(*YES) CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/pkgs/bin/bash' '-l' '-c' '/QOpenSys/pkgs/bin/sc -a %s %s %s 2>&1')) %s");
        snprintf(command, sizeof(command), command_printf_fmt, sc_options, sc_operation, instance, sbmjob_opts);
        Qp0zLprintf("Running command: > %s <'\n", command);
        Qp0zLprintf("Check spooled file output for progress\n");
        free(sbmjob_opts);

        rc = system(command);
        rc = rc == 0 ? RC_OK : RC_FAILED;
        parm->rc = rc;
        return rc;
    }
    // To run interactively and still reliably get output displayed on the
    // 5250 screen, we need to explicitly set up piped descriptors, spawn
    // a shell, read the 'sc' output, and print it in this job. This is a
    // bit involved, but the alternatives weren't great:
    //  - QP2SHELL2 didn't set up file descriptors reliably
    //  - QSH fails if we're not in a multithread-capable environment!
    // So, here we go.....

    // First things first, we need an arguments array set up...
    char *child_argv[6];
    child_argv[0] = "/QSYS.LIB/QP2SHELL2.PGM"; // Note that "/QOpenSys/pkgs/bin/bash" won't work because PASE executables are not allowed
    child_argv[1] = "/QOpenSys/pkgs/bin/bash";
    child_argv[2] = "-l";
    child_argv[3] = "-c";
    char sc_cmd[1024];
    snprintf(sc_cmd, sizeof(sc_cmd), "exec /QOpenSys/pkgs/bin/sc -a %s %s %s 2>&1", sc_options, sc_operation, instance);
    child_argv[4] = sc_cmd;
    child_argv[5] = NULL;

    // ...and an environment for the child process...
    char *envp[10];
    envp[0] = "QIBM_MULTI_THREADED=Y";
    envp[1] = "PATH=/QOpenSys/pkgs/bin:/QOpenSys/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin";
    envp[2] = "QIBM_USE_DESCRIPTOR_STDIO=Y";
    envp[3] = "PASE_STDIO_ISATTY=N";
    struct passwd *pd;
    char logname[20];
    if (NULL != (pd = getpwuid(getuid())))
    {
        sprintf(logname, "LOGNAME=%s", pd->pw_name);
    }
    else
    {
        sprintf(logname, "LOGNAME=%s", getenv("LOGNAME"));
    }
    envp[4] = logname;
    envp[5] = (char *)NULL;

    // ...and we need to set up the pipes...
    int stdoutFds[2];
    if (pipe(stdoutFds) != 0)
    {
        fprintf(stderr, "failure on pipe\n");
        return 1;
    }
    int fd_map[3];
    fd_map[0] = open("/dev/null", O_RDONLY);
    fd_map[1] = stdoutFds[1];
    fd_map[2] = stdoutFds[1];

    // ...and we want to spawn a multithread-capable job...
    struct inheritance inherit;
    memset(&inherit, 0, sizeof(inherit));
    inherit.flags = SPAWN_SETTHREAD_NP;
    inherit.pgroup = SPAWN_NEWPGROUP;

    // ...and we can FINALLY run our command!
    // Qp0zLprintf("Running command: '%s'\n", sc_cmd);
    pid_t child_pid = spawnp(child_argv[0], //executable
                             3,             // fd_count
                             fd_map,        //fd_map[]
                             &inherit,      //inherit
                             child_argv,    //argv
                             envp);         //envp
    if (child_pid == -1)
    {
        Qp0zLprintf("Error spawning child process: %s\n", strerror(errno));
        fprintf(stderr, "Error spawning child process: %s\n", strerror(errno));
        parm->rc = RC_FAILED;
        return -1;
    }
    // We don't need to talk to the child's stdin, so let's close it.
    close(stdoutFds[1]);

    // Now, let's read the output from the child process and print it here.
    char line[1024 * 4];
    memset(line, 0, sizeof(line));
    int bytesRead = -1;
    char *linePtr = line;
    while ((rc = read(stdoutFds[0], linePtr, 1)) > 0)
    {
        int pos = linePtr - line;
        if (*linePtr == '\n' || pos >= (sizeof(line) - 1))
        {
            *linePtr = '\0';
            Qp0zLprintf("%s\n", line);
            printf("%s\n", line);
            linePtr = line;
            memset(line, 0, sizeof(line));
        }
        else
        {
            linePtr++;
        }
    }
    Qp0zLprintf("%s\n", line);
    printf("%s\n", line);

    // Close out the descriptor now that data from the pipe is fully consumed
    close(stdoutFds[0]);

    // Wait for the child process to finish (should be already done since the pipe is closed)
    waitpid(child_pid, &rc, 0);

    // FINALLY done! Goodness...
    rc = rc == 0 ? RC_OK : RC_FAILED;
    parm->rc = rc;
    return rc;
}
