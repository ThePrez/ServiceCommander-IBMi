
#include <unistd.h>
#include <as400_protos.h>
#include <stdio.h>
#include <string.h>

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
  return execv(args[0], args);
}