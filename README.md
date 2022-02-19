# RocketGateway

I had originally developed this program for internal use in my company. Since we set up our internal network with no
internet access, I needed an easy way to receive status emails from servers e.g. TrueNAS. Since I didn't want to maintain an
email server only for status messages, it was obvious to use our internal RocketChat server to receive these emails.

Unfortunately there was no software available that allowed to send an email directly to RocketChat. All programs on the
market use a normal email server as an intermediate step.

For this reason I developed this small program which provides a SMTP-to-RocketChat-gateway. You can simply send an e-mail
to a specific user (The e-mail address must be provided for each user account) and he will get a message directly in 
RocketChat. Attachments in the email are converted to file uploads in RocketChat. Since RocketChat, for security 
reasons, does not support HTML-rendering, HTML-emails are converted to plain-text messages. The original messages
are additionally sent as file uploads.

# How to use the program

The program needs an installation of RocketChat-server and at least Java 11 and Gradle 7.x to compile it.

In RocketChat you __must__ create a user which is in the "_bot_"-group e.g. "_email-bot_" which is then used 
by RocketGateway to send messages. You also __must__ add the permission "_View Full Other User Info_" to the 
bot-group, otherwise RocketGateway cannot map the e-mail-address to the internal user id of RocketChat.

For security reasons you should generate a new permissions-group and clone every permission from the original
"_bot_"-group and then add "_View Full Other User Info_" to this group. The credentials of the newly generated 
user _must_ then be added to the "_rocketgateway.json_"-file.

You can also activate SMTP-authentication (username and password) and TLS-encryption (1.3 and 1.2) for the
SMTP-connection. I've tested the TLS-encryption with the "_Let's Encrypt_"-certificates of my own root-server.
If you want to enable TLS, ensure that all paths to the certificate files are correct.

The json-config file in the dist-directory should be self-explaining and the default configuration should work
with a normal RocketChat-installation on the same server.

# Installation

Just download the tar-file, unpack it and start the bash-script:

```bash
./rocketgateway.sh -c rocketgateway.json
```

# Compilation

```bash
gradle clean jar assembleDist
```
