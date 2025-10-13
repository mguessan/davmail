# Davmail Docker

## Get from GitHub Container Registry

Davmail docker images are available on GHCR at https://github.com/mguessan/davmail/pkgs/container/davmail

Pull the latest image with:

```
docker pull ghcr.io/mguessan/davmail:latest
```

Pull development image with:

```
docker pull ghcr.io/mguessan/davmail:unstable
```

## Run image in interactive mode

Make sure the window manager has X11 available and run the following command:

```
mkdir -p ~/.davmail
docker run --network=host --ipc=host --rm --name davmail --hostname davmail \
-e DISPLAY=$DISPLAY \
-v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" \
-v ${HOME}/.davmail:/config:z \
-u $UID \
ghcr.io/mguessan/davmail:latest
```
On first run the container will store configuration in `~/.davmail`

## Obtain an Oauth2 refresh token to run DavMail on a remote instance

Simple username/password authentication is no longer available for most tenants.
This means that to authenticate with DavMail, you need to obtain an OAuth2 refresh token
on a workstation compliant with company policies.

To obtain an OAuth2 refresh token, run the following command on a workstation with a GUI:

```
mkdir -p ~/.davmail
docker run --network=host --ipc=host --rm --name davmail --hostname davmail \
-e DISPLAY=$DISPLAY \
-v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" \
-v ${HOME}/.davmail:/config:z \
-u $UID \
ghcr.io/mguessan/davmail:latest -token
```

Alternative on windows: get the latest standalone package from:
https://github.com/mguessan/davmail#trunk-builds (development build)
or https://davmail.sourceforge.net/download.html (release packages)

And run the following command:

```
davmail64.exe -token
```
or
```
jre\bin\java -jar davmail.jar -token
```

This will give you a token that you can store in `~/.davmail/.env.oauth` on the remote instance.

Content should look like:
```
davmail.oauth.<email@company.com>.refreshToken=<refresh_token>
```
Note that Davmail will encrypt the refresh token using an AES key derived from the password provided by your email client.
This password can be different from your actual account password.


## Run with Docker Compose

To run davmail in Docker, download the `compose.yml` file and run `docker compose up -d`.

Make sure to change the ports in the `compose.yml` file to make sure you are not exposing them to the public (unless you want that, of course).

See below for more information on how to enable SSL.


## Enable SSL

Below are two methods described to setup SSL. Either using a service like `letsencrypt` or providing your own certificate, or using `traefik` (a reverse proxy).

### Letsencrypt/own certificate

If you are running davmail on your server, make sure to setup SSL. In order to do this, use a service like letsencrypt or create a self signed certificate. 
In the case of letsencrypt, go to `certs/live/<DOMAIN>` and run the following command

```
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -certfile cert.pem -out davmail.p12
```

Make sure to set a password `<password2>` and make sure it is different than `<password>` (it can be the same, but for your own sake use a different one, please).

Move the new `davmail.p12` file over to the configuration directory on your server, edit `davmail.properties` (make sure to put the correct `<password2>`!).

```
davmail.ssl.keystoreType=PKCS12
davmail.ssl.keyPass=<password2>
davmail.ssl.keystoreFile=/davmail.p12
davmail.ssl.keystorePass=<password2>
```

Restart your container and go to your email client. Go to account settings and enable SSL (keep the same ports) and connect.

### Traefik

Note that communication between your email client and traefik will be encrypted, and then traefik will forward the unencrypted traffic 
(through the docker's internal network) to your DavMail instance. This way, you don't have to add another certificate manager if you are 
already using traefik to handle your certificates.

First download the new `compose-traefik.yml` from this directory and rename it to `compose.yml`. Then edit/create a `.env` file and put
```
DOMAIN=domain.com
```

This means that you can access your davmail instance over `davmail.domain.com`, make sure to change domain.com such that traefik can generate a certificate for it.

To use traefik to manage your certificate, change your `traefik.yml` config (or `.toml`, depending on you traefik configuration) and add the following entrypoints:

```
entryPoints:
    imap-tls:
        address: :1143
    smtp-tls:
        address: :1025
```

Then change the `compose.yml` where you have you traefik instance, expose port 1143 and 1025 (and others if needed) and make sure that both davmail 
and traefik share a docker network. At last, run `docker compose up -d` for both traefik and davmail and now you can point your email clients 
to `davmail.domain.com` and enable SSL (note that the port stays the same).

## Build from source
To experiment with Davmail docker configuration, you can build the image from source:

```
git clone https://github.com/mguessan/davmail.git
docker build . -t davmail -f src/docker/Dockerfile
```
