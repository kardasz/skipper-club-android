GRADLEW = ./gradlew
MODULE = :app
APP_ID = app.skipperclub
LAUNCHER = $(APP_ID)/$(APP_ID).MainActivity
VERSION_FILE = app/version.properties

.PHONY: help assemble-debug assemble-release bundle-release bump-version-code print-version install-debug uninstall run test connected-check coverage coverage-connected lint clean dependencies

help:
	@echo "SkipperClub — Android Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  assemble-debug     Build debug APK"
	@echo "  assemble-release   Build release APK (unsigned)"
	@echo "  bundle-release     Build release AAB for Play Store (auto-bumps versionCode unless VERSION_CODE is set)"
	@echo "  bump-version-code  Increment versionCode in $(VERSION_FILE)"
	@echo "  print-version      Print current versionCode and versionName"
	@echo "  install-debug      Install debug APK on the connected device"
	@echo "  uninstall          Uninstall app from the connected device"
	@echo "  run                Install debug APK and launch MainActivity"
	@echo "  test               Run unit tests (testDebugUnitTest)"
	@echo "  connected-check    Run instrumented tests on the connected device"
	@echo "  coverage           Run unit tests and generate JaCoCo coverage report"
	@echo "  coverage-connected Run unit + connected tests and generate combined coverage report"
	@echo "  lint               Run Android lint on the debug variant"
	@echo "  clean              Gradle clean"
	@echo "  dependencies       Print resolved dependencies"
	@echo ""
	@echo "Examples:"
	@echo "  make bundle-release                          # auto-bump versionCode, build AAB"
	@echo "  make bundle-release VERSION_NAME=0.3         # auto-bump code, override name"
	@echo "  make bundle-release VERSION_CODE=42 VERSION_NAME=1.0  # override both, no auto-bump"

assemble-debug:
	$(GRADLEW) $(MODULE):assembleDebug

assemble-release:
	$(GRADLEW) $(MODULE):assembleRelease $(if $(VERSION_CODE),-PVERSION_CODE=$(VERSION_CODE),) $(if $(VERSION_NAME),-PVERSION_NAME=$(VERSION_NAME),)

bundle-release:
	@if [ -z "$(VERSION_CODE)" ]; then $(MAKE) --no-print-directory bump-version-code; fi
	$(GRADLEW) $(MODULE):bundleRelease $(if $(VERSION_CODE),-PVERSION_CODE=$(VERSION_CODE),) $(if $(VERSION_NAME),-PVERSION_NAME=$(VERSION_NAME),)

bump-version-code:
	@current=$$(awk -F= '/^versionCode=/ {print $$2}' $(VERSION_FILE)); \
	if [ -z "$$current" ]; then echo "versionCode not found in $(VERSION_FILE)" >&2; exit 1; fi; \
	next=$$((current + 1)); \
	awk -v n=$$next 'BEGIN{FS=OFS="="} /^versionCode=/ {$$2=n} {print}' $(VERSION_FILE) > $(VERSION_FILE).tmp && mv $(VERSION_FILE).tmp $(VERSION_FILE); \
	echo "Bumped versionCode: $$current -> $$next"

print-version:
	@awk -F= '/^versionCode=|^versionName=/ {print $$1"="$$2}' $(VERSION_FILE)

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

coverage:
	$(GRADLEW) $(MODULE):debugUnitTestCoverage

coverage-connected:
	$(GRADLEW) $(MODULE):debugCombinedTestCoverage

lint:
	$(GRADLEW) $(MODULE):lintDebug

clean:
	$(GRADLEW) clean

dependencies:
	$(GRADLEW) $(MODULE):dependencies
