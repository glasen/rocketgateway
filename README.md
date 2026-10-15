# RocketGateway

I had originally developed this program for internal use in my company. Since we set up our internal network with no
internet access, I needed an easy way to receive status emails from servers e.g. TrueNAS. Since I didn't want to maintain an
email server only for status messages, it was obvious to use our internal RocketChat server to receive these emails.

Unfortunately there was no software available that allowed to send an email directly to RocketChat. All programs on the
market use a normal email server as an intermediate step.

For this reason I developed this small program which provides a SMTP-to-RocketChat-gateway. You can simply send an e-mail
to a specific user (The e-mail address must be provided for each user account) and he will get a message directly in 
RocketChat. Attachments in the email are converted to file uploads in RocketChat. Since RocketChat, for security 
reasons, does not support HTML-rendering, HTML-emails are converted into Markdown-format.

# How to use the program

The program needs an installation of RocketChat-server and at least Java 17 to run and compile it.

In RocketChat you __must__ create a user which is in the "_bot_"-group e.g. "_email-bot_" which is then used 
by RocketGateway to send messages. You also __must__ add the permission "_View Full Other User Info_" to the 
bot-group, otherwise RocketGateway cannot map the e-mail-address to the internal user id of RocketChat.

For security reasons you should generate a new permissions-group and clone every permission from the original
"_bot_"-group and then add "_View Full Other User Info_" to this group. The credentials of the newly generated 
user _must_ then be added to the "_rocketgateway.json_"-file.

You can also activate SMTP-authentication (username and password) and TLS-encryption (1.3 and 1.2) for the
SMTP-connection. I've tested the TLS-encryption with the "_Let's Encrypt_"-certificates of my own root-server.
If you want to enable TLS, ensure that all paths to the certificate files are correct.

All settings are made in the json-config file (see [Configuration](#configuration)). Copy the file from the
dist-directory and adjust it to your installation.

# Configuration

The configuration is a JSON file which you pass with `-c`. The file in the dist-directory
([rocketgateway.json](src/main/dist/rocketgateway.json)) is a complete example. It contains passwords, so restrict
its permissions (`chmod 600 rocketgateway.json`). RocketGateway prints a warning if other users can read it.

RocketGateway validates the file at startup and prints the problem if something is wrong. The sections `smtp`,
`rocketchat`, `spam` and `tls` are required, `email_channels` is optional.

## smtp

| Key            | Description                                                                                         |
|----------------|-----------------------------------------------------------------------------------------------------|
| `smtp_port`    | TCP port of the SMTP server (1-65535). Ports below 1024 need extra privileges.                      |
| `require_auth` | `true`: clients must log in with the credentials below. `false`: anybody who can reach the port can post messages. |
| `smtp_username`| Username for SMTP login. Required if `require_auth` is `true`.                                      |
| `smtp_password`| Password for SMTP login. Required if `require_auth` is `true`.                                      |

Leave `require_auth` on unless the port is only reachable from a trusted network. If TLS is enabled as well,
clients must use STARTTLS before they can log in, so the password is never sent in cleartext.

## rocketchat

| Key              | Description                                                                                       |
|------------------|---------------------------------------------------------------------------------------------------|
| `rocketchat_url` | Base URL of your RocketChat server, e.g. `https://chat.example.org`. Must start with `http://` or `https://`. Plain HTTP to a non-local host works, but sends the bot password unencrypted, so a warning is printed. |
| `bot_username`   | Username (or e-mail address) of the bot user described above.                                     |
| `bot_password`   | Password of the bot user.                                                                         |

The bot must be allowed to post in the target channels (add it as a member of private groups). Uploading
attachments uses the `rooms.media` API, so you need a current RocketChat version (6.10 or newer).

## spam

| Key            | Description                                                                                         |
|----------------|-----------------------------------------------------------------------------------------------------|
| `get_spam`     | `true`: e-mails to addresses which cannot be mapped to a channel or user are posted to `spam_channel`. `false`: they are dropped. |
| `spam_channel` | Name of the channel which receives these e-mails (without `#`).                                     |

Everything which is sent to an unknown address ends up there, so keep this channel private.

## tls

| Key                       | Description                                                                              |
|---------------------------|------------------------------------------------------------------------------------------|
| `enable_tls`              | `true` enables STARTTLS. If `false`, all other keys in this section are ignored.         |
| `tls_versions`            | Allowed protocols. Default: `["TLSv1.3", "TLSv1.2"]`.                                    |
| `certificatechain_file`   | PEM file with the server certificate and the intermediate certificates.                 |
| `privatekey_file`         | PEM file with the private key.                                                           |
| `privatekey_password`     | Password of the private key. Use `""` if the key is not encrypted.                      |
| `trustedcertificate_file` | PEM file with the (root) certificate. All three files are required if TLS is enabled.   |

With Let's Encrypt the files are usually `fullchain.pem`, `privkey.pem` and `cert.pem` in
`/etc/letsencrypt/live/<domain>/`. RocketGateway reads the certificates only once at startup, so restart it after
a renewal.

## email_channels

Maps recipient addresses to a destination in RocketChat. The mapping is a list of objects:

```json
"email_channels": [
  { "address": "status@test.local", "channel": "status" },
  { "address": "admin@test.local",  "channel": "@alice" },
  { "address": "ops@test.local",    "channel": "#ops" }
]
```

The `channel` value is interpreted as follows:

* `#name` is the channel (or private group) `name`.
* `@name` is a direct message to the user `name`.
* A bare `name` is a channel, unless it is the username of a RocketChat user. Use the prefixes if you want to
  be explicit.

The addresses `To`, `Cc` and `Bcc` of an e-mail are all checked, and each recipient gets its own message. Address
matching ignores upper and lower case.

You don't have to list users: RocketGateway also reads the e-mail addresses of all RocketChat users and sends mails
for these addresses as direct messages to the user (this is what the "_View Full Other User Info_" permission is for).
Entries in `email_channels` take precedence over this automatic mapping.

The mappings are reloaded automatically when the file changes, no restart is needed. If the changed file is invalid,
an error is printed and the old mappings stay active. All other settings need a restart.

## Limits and behavior

* E-mails larger than 25 MB are rejected.
* At most 10 attachments per e-mail are uploaded, each at most 10 MB. A short note is posted if some were skipped.
* `@all`, `@here` and `@user` mentions in the subject, body and sender are defused, so an e-mail cannot notify
  people.
* HTML-only e-mails are converted to Markdown.

# Installation

Just download the tar-file, unpack it and start the bash-script:

```bash
./rocketgateway.sh -c rocketgateway.json
```

# Compilation

```bash
./gradlew build
```
