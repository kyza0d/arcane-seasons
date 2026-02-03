export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

HYTALE_DIR := /home/kyza/.var/app/com.hypixel.HytaleLauncher/data/Hytale
MODS_DIR := $(HYTALE_DIR)/UserData/Saves/Modding/mods

.PHONY: build clean setup deploy

setup:
	chmod +x gradlew

build: setup
	./gradlew build

clean: setup
	./gradlew clean

deploy: build
	@mkdir -p $(MODS_DIR)
	@cp build/libs/*.jar $(MODS_DIR)/
	@echo "Deployment complete!"

dev: clean build
	./scripts/deploy.sh