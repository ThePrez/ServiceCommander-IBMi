# Service Commander for IBM i
A utility for unifying the daunting task of managing various services and applications running on IBM i. 

This tool can be used to manage a number of services, for instance:
- IBM i host server jobs
- IBM i standard TCP servers (*TCP, *SSHD, etc.)
- Open Source programs you wrote (Node.js, Python, PHP applications)
- Apache Tomcat instances
- Kafka, Zookeeper, ActiveMQ servers, etc
- Jenkins


# Current features
Some of the features of the tool include:
- The ability to specify dependencies (for instance, if one application or service dependds on another), and it will start any dependencies as needed
- The ability to submit jobs to batch easily, even with custom batch settings (use your own job descriptions, for instance)
- The ability to check the "liveliness" of your service by either port status or job name
- Customize the runtime environment variables of your job

# Basic usage
