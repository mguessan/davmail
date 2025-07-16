There are two methods to setup SSL. Either using a service like `letsencrypt` or providing your own certificate, or using `traefik`.

# Letsencrypt/own certificate

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

# Traefik

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
