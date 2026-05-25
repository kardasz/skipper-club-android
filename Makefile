GRADLEW = ./gradlew
MODULE = :app
APP_ID = app.skipperclub
LAUNCHER = $(APP_ID)/$(APP_ID).MainActivity

.PHONY: help assemble-debug assemble-release bundle-release install-debug uninstall run test connected-check lint clean dependencies

help:
	@echo "SkipperClub — Android Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  assemble-debug     Build debug APK"
	@echo "  assemble-release   Build release APK (unsigned)"
	@echo "  bundle-release     Build release AAB for Play Store"
	@echo "                     Override with VERSION_CODE=3 VERSION_NAME=0.2.1"
	@echo "  install-debug      Install debug APK on the connected device"
	@echo "  uninstall          Uninstall app from the connected device"
	@echo "  run                Install debug APK and launch MainActivity"
	@echo "  test               Run unit tests (testDebugUnitTest)"
	@echo "  connected-check    Run instrumented tests on the connected device"
	@echo "  lint               Run Android lint on the debug variant"
	@echo "  clean              Gradle clean"
	@echo "  dependencies       Print resolved dependencies"

assemble-debug:
	$(GRADLEW) $(MODULE):assembleDebug

assemble-release:
	$(GRADLEW) $(MODULE):assembleRelease $(if $(VERSION_CODE),-PVERSION_CODE=$(VERSION_CODE),) $(if $(VERSION_NAME),-PVERSION_NAME=$(VERSION_NAME),)

bundle-release:
	$(GRADLEW) $(MODULE):bundleRelease $(if $(VERSION_CODE),-PVERSION_CODE=$(VERSION_CODE),) $(if $(VERSION_NAME),-PVERSION_NAME=$(VERSION_NAME),)

install-debug:
	$(GRADLEW) $(MODULE):installDebug

uninstall:
	$(GRADLEW) $(MODULE):uninstallAll

run: install-debug
	adb shell am start -n $(LAUNCHER)

test:
	$(GRADLEW) $(MODULE):testDebugUnitTest

connected-check:
	$(GRADLEW) $(MODULE):connectedDebugAndroidTest

lint:
	$(GRADLEW) $(MODULE):lintDebug

clean:
	$(GRADLEW) clean

dependencies:
	$(GRADLEW) $(MODULE):dependencies
