#Introductory (hands-on) Exercise

## 1. Create a simple program (shell script)
To create a sample "service", open an SSH terminal, 'cd' to a directory of your choice, and run the following commands.
```bash
echo '#!/QOpenSys/pkgs/bin/bash' > myscript.sh
echo 'echo starting' > myscript.sh
echo 'while :; do sleep 5 && echo running ;done' >> myscript.sh
chmod +x ./myscript.sh
```
As you can see, this "service" does nothing more than periodically produce output. If you'd like to, you may run `./myscript.sh` to see what it does (<ctrl>+C to exit).

## 2. Use the `scinit` utility to make a service definition
From your SSH session, run:
```bash
scinit ./myscript.sh
```
Answer the questions like so:
```
Would you like this service to be available to all users? [y] n
Short name: myscript
Friendly name: My Script that I used to learn Service Commander
Must the application be started in the current directory (/home/JGORZINS)? [y] y
How can the application be checked for liveliness? (one of: JOBNAME/PORT) JOBNAME
What job does your application run in? myscript
Will your application need to be submitted to batch? [n] y
What job name should be used? (leave blank for default) myscript
What custom SBMJOB options should be used? (leave blank for none) JOBQ(QUSRNOMAX)
What group(s) would this application be a part of?
        (press <enter> after each entry, leave blank to entering values)
1>
What service(s) does this application rely on?
        (press <enter> after each entry, leave blank to entering values)
1>
```

![image](https://user-images.githubusercontent.com/17914061/118847976-ef559a00-b893-11eb-802b-1d2fedddb446.png)

The tool should also show you the information about the service you just created
![image](https://user-images.githubusercontent.com/17914061/118848557-79056780-b894-11eb-9c36-38014eb7190d.png)


## 3. Start the service
Since the short name of the service is `myscript`, you can start your newly-created service by running:
```bash
sc start myscript
```
## 4. Observe the service running in WRKACTJOB
From a 5250 session, run
```
WRKACTJOB JOB(MYSCRIPT)
```
Observe the jobs that are now running under the 'myscript' job name you provided earlier.
![image](https://user-images.githubusercontent.com/17914061/118849087-03e66200-b895-11eb-87bf-678fc493b2d8.png)

## 5. Display the service log file
From an SSH terminal, run:
```
sc loginfo myscript
```
This will give you a command that can be run to monitor the log file. 
```
myscript: tail -f /home/JGORZINS/.sc/logs/2021-05-19-12.05.38.myscript.log
```
In this case, it will be a `tail -f` command. Copy/paste that command to watch the log file. 
![image](https://user-images.githubusercontent.com/17914061/118849720-9edf3c00-b895-11eb-9050-77b15dbaae07.png)


## 6. Stop the service
## 7. Start the service again (this time with --splf-output
## 8. Display the log file
## 8. View performance info
## 9. Stop the service
## 10. Delete the service definition
