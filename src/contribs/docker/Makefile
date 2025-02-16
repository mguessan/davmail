SUBTAG=:next
DTARGETS=davmail$(SUBTAG) davbuild$(SUBTAG)
LOCATION_OF_DAVMAIL_ROOT=$$PWD/../../..

all: build davmail

davbuild davmail davupstream davbase:
	docker build --target $@ -t $@$(SUBTAG) .

clean:
	rm -f davmail-compile.tar *~
	-docker rmi $(DTARGETS)

build: davbuild indirect

indirect: indirect_compile indirect_archive

indirect_compile:
	docker run -it --memory=1.5g --cpus .3 --rm --name davmail-build -u "$$UID" --entrypoint "" -v "${LOCATION_OF_DAVMAIL_ROOT}:/src" -w /src "davbuild$(SUBTAG)" ant -lib /usr/share/java jar

indirect_archive:
	tar -cf davmail-compile.tar -C ${LOCATION_OF_DAVMAIL_ROOT}/dist .

test:
	docker run -it --memory=1.5g --cpus .3 --network=host --rm --name davmail -v /tmp/.X11-unix:/tmp/.X11-unix -e JAVA_OPT_USER=-Dsun.java2d.uiScale=2.0 -e "DISPLAY=$${DISPLAY}" -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -v "$${XAUTHORITY:-$$HOME/.Xauthority}:/.Xauthority:ro" -v $$HOME/.davmail.properties.core:/davmail.properties:ro -v $$HOME/.davmail.properties.toks:/davmail.properties.tokens:rw -u "$$UID" davmail:next

.PHONY: all build davmail davbuild davupstream clean indirect indirect_compile indirect_archive
