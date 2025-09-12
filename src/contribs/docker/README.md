# Davmail Docker

To run davmail in Docker, download the `compose.yml` file and run `docker compose up -d`.

Make sure to change the ports in the `compose.yml` file to make sure you are not exposing them to the public (unless you want that, of course).

If you want to set up OAuth2, but your host is headless/does not have access to a GUI, please see below

If you want to set up SSL/certificates, scroll down to SSL

# OAuth2 on headless

Download the `Dockerfile` on your **PC** (needs GUI access) and run the commands below

```
docker build . -t davmail # this might take a bit
docker run --network=host --rm --name davmail --hostname davmail -v /tmp/.X11-unix:/tmp/.X11-unix  -e "DISPLAY=${DISPLAY}" -v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" -v ./config:/config -u "$UID" davmail davmail --token
```

A new window should pop up with a few settings. Please change "Exchange Protocol" from `EWS` to for example `O365Manual` (or whatever method you want to use, see [Exchange protocol](https://davmail.sourceforge.net/gettingstarted.html)) and press "Save"

Next, open your email client, e.g. thunderbird. Add a new account and make sure to click "Configure manually" or something like that.

Set receiving/incoming (IMAP) as following:
- hostname: `localhost`
- port: `1143`
- Connection security: `none`
- Authentication method: `Normal password`

Set sending/outgoing (SMTP) as following:
- hostname: `localhost`
- port: `1025`
- Connection security: `none`
- Authentication method: `Normal password`

Both using username `<email>` and password `<password>`. Note that `<email>` has to match the email you want log in to (using oauth2), and `<password>` can be ANY password, even different than your account password.

After pressing connect on the email client (sometimes you may need to ignore ssl warnings), the GUI from davmail should show instructions on how to authenticate (depending on the Exchange Protocol you set before). Please follow these instructions and confirm that your account is now connected. To move the configuration to your server/headless instance, stop the docker container (Ctrl+C) and type `cat config/davmail.properties`.

At the bottom, there is an entry called `davmail.oauth.<email>.refreshToken={AES}...`. Edit `config/.env.oauth` on your server to include `<email>={AES}...` and restart the container.

You can now configure your email client again using the same steps before, but instead of localhost you should use your server ip (make sure you put in the same `<password>`).

# SSL

Below are two methods described to setup SSL. Either using a service like `letsencrypt` or providing your own certificate, or using `traefik` (a reverse proxy).

## Letsencrypt/own certificate

If you are running davmail on your server, make sure to setup SSL. In order to do this, use a service like letsencrypt or create a self signed certificate. In the case of letsencrypt, go to `certs/live/<DOMAIN>` and run the following command

```
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -certfile cert.pem -out davmail.p12
```

Make sure to set a password `<password2>` and make sure it is different than `<password>` (it can be the same, but for your own sake use a different one, please).

Move the new `davmail.p12` file over to the `config` directory on your server, edit `config/davmail.properties` (make sure to put the correct `<password2>`!).
```
davmail.ssl.keystoreType=PKCS12
davmail.ssl.keyPass=<password2>
davmail.ssl.keystoreFile=/davmail.p12
davmail.ssl.keystorePass=<password2>
```

Restart your container and go to your email client. Go to account settings and enable SSL (keep the same ports) and connect.

## Traefik

Note that communication between your email client and traefik will be encrypted, and then traefik will forward the unencrypted traffic (through the docker's internal network) to your DavMail instance. This way, you don't have to add another certificate manager if you are already using traefik to handle your certificates.

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

Then change the `compose.yml` where you have you traefik instance, expose port 1143 and 1025 (and others if needed) and make sure that both davmail and traefik share a docker network. At last, run `docker compose up -d` for both traefik and davmail and now you can point your email clients to `davmail.domain.com` and enable SSL (note that the port stays the same).
