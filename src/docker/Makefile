IMAGE_NAME = mguessan/davmail
IMAGE_VERSION ?= ""

ifeq ($(IMAGE_VERSION),"")
	IMAGE_LABEL ?= $(IMAGE_NAME)
else
	IMAGE_LABEL ?= $(IMAGE_NAME):${IMAGE_VERSION}
endif

image:
	@echo "Building Docker image ${IMAGE_LABEL}..."
	docker build . -t $(IMAGE_LABEL)

tag-latest-without-build:
	@echo "Tagging Docker image ${IMAGE_LABEL} with latest..."
	docker tag `docker image ls --format '{{.ID}}' $(IMAGE_LABEL)` $(IMAGE_NAME):latest

run:
	@echo "Starting davmail"
	docker compose up -d

run-gui:
	@echo "Running davmail with GUI"
	mkdir ${HOME}/.davmail
	docker run --network=host --ipc=host --rm --name davmail --hostname davmail \
		-e DISPLAY=$DISPLAY \
		-v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" \
		-v ${HOME}/.davmail:/config:z \
		-u $UID \
		davmail

run-token:
	@echo "Running davmail with GUI to get oauth token"
	mkdir ${HOME}/.davmail
	docker run --network=host --ipc=host --rm --name davmail --hostname davmail \
		-e DISPLAY=$DISPLAY \
		-v "${XAUTHORITY:-$HOME/.Xauthority}:/.Xauthority:ro" \
		-v ${HOME}/.davmail:/config:z \
		-u $UID \
		davmail -token


latest: image tag-latest-without-build