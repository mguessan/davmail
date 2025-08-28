# Running headless

To run davmail (headless) in Docker, download the `Dockerfile` and `compose.yml` file to your server.

After that, run the following commands:
```
docker build . -t davmail
docker compose up -d
```

Note that the first command will download and compile davmail, which might take a while depending on your server's internet speed and processor. Once build, you don't have to build it again and running the second command only is enough.

Also make sure to change the ports in the `compose.yml` file to make sure you are not exposing them to the public (unless you want that, of course).

# Setting up OAuth2 on "headless"

Normally running davmail headless means that you can not use OAuth2, because you need a GUI for that. However, what you can do is download the `Dockerfile` to your **PC** and download the config from [here](https://raw.githubusercontent.com/mguessan/davmail/refs/heads/master/src/etc/davmail.properties), make sure to save it as `config/davmail.properties`.

Edit the `config/davmail.properties` file and set `davmail.server=false` and set `davmail.mode=O365Manual` (or whatever method you want to use, see [Exchange protocol](https://davmail.sourceforge.net/gettingstarted.html)), and run the commands below (make sure to do this on your **PC**).

```
docker build . -t davmail # again, this might take a bit
docker run --network=host --rm --name davmail --hostname davmail -v /tmp/.X11-unix:/tmp/.X11-unix  -e "DISPLAY=${DISPLAY}" -v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" -v ./config:/config -u "$UID" davmail
```


Next, open your email client, e.g. thunderbird. Add a new account and make sure to click "Configure manually" or something like that. Set receiving/incoming (IMAP) to `localhost:1143` and sending/outgoing (SMTP) to `localhost:1025`. Both using username `<email>` and password `<password>`. Note that `<email>` has to match the email you want log in to (using oauth2), and `<password>` can be ANY password, even different than your account password.

After pressing connect on the email client (sometimes you may need to ignore ssl warnings), the GUI from davmail should show instructions on how to authenticate (depending on the davmail.mode you set before). Please follow these instructions and confirm that your account is now connected. To move the configuration to your server/headless instance, stop the docker container (Ctrl+C) and type `cat config/davmail.properties`.

At the bottom, there is an entry called `davmail.oauth.<email>.refreshToken={AES}...`. Edit `config/.env.oauth` on your server to include `<email>={AES}...` and restart the container.

You can now configure your email client again using the same steps before, but instead of localhost you should use your server ip (make sure you put in the same `<password>`).
