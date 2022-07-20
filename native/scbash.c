
#include <unistd.h>
#include <as400_protos.h>
#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <stdarg.h>
#include <stdlib.h>

static int write_to_file(char* file_name, char* fmt, ...) {
    va_list varargs;
    va_start(varargs, fmt);
    fprintf(stderr, fmt, varargs);
    if(NULL == file_name) {
        return -1;
    }
    FILE* fp = fopen(file_name, "a");
    if (fp == NULL) {
        return -1;
    }
    fprintf(fp, fmt, varargs);
    fclose(fp);
    return 0;
}

extern char **environ;
int main(int _argc, char **_argv)
{

  char **iterator = environ;
  for (; *iterator; iterator++)
  {
    char *env = *iterator;
    if (env == strstr(env, "SCOMMANDER_"))
    {
      const char *ile[2];
      ile[0] = env;
      ile[1] = NULL;
      int rc = Qp2setenv_ile(ile, NULL);
    }
  }
  char **args = _argv;
  args[0] = "/QOpenSys/pkgs/bin/bash";
#ifndef AUTO_RESTART
   return execv(args[0], args);
#else
  char* log_file = getenv("SCOMMANDER_LOGFILE");
  int last_rc = -1;
  int num_forks = 0;
  struct timespec start;
  clock_gettime(CLOCK_MONOTONIC, &start); //TODO: error check

  while(true)
  {
    
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now); //TODO: error check
    int secs = (int) (now.tv_sec - start.tv_sec);
    if(2+secs < num_forks) {
      write_to_file(log_file, "===> FATAL ERROR: Process has died %d times in %d seconds. Something's wrong.\n", num_forks, secs);
      return 0 == last_rc ? -1 : last_rc;
    }
    int rc = fork();
    if(rc == 0)
    {
      int rc = execv(args[0], args);
      if(rc == -1)
      {
       write_to_file(log_file, "===> FATAL ERROR: execv failed\n");
      return 0 == last_rc ? -1 : last_rc;
      }
    }
    else if(rc == -1)
    {
       write_to_file(log_file,"===> FATAL ERROR: fork failed\n");
      return 0 == last_rc ? -1 : last_rc;
    }
    else
    {
      num_forks++;
      if(num_forks > 1)
      {
         write_to_file(log_file,"===> forked %d times. Latest PID=%d\n", num_forks, rc);
      }
      pid_t status=-1;
      waitpid(-1, &status, 0);
      last_rc = (int) status;
       write_to_file(log_file,"===> Exic code was %d\n", (int)status);
    }
  }
#endif
}