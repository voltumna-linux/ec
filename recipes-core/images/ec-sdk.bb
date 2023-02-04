require recipes-core/images/voltumna-sdk.inc
require recipes-core/images/elettra-sdk.inc
require include/ec.inc

IMAGE_INSTALL_append += "libmodbus-dev libusb1-dev libgpiod-dev \
	libximc-dev cpptango-dev"

TOOLCHAIN_HOST_TASK_append += "nativesdk-pogo nativesdk-jive \
	nativesdk-python3-pytango nativesdk-libmodbus-dev \
	nativesdk-libusb1-dev nativesdk-libgpiod-dev \
	nativesdk-libximc-dev nativesdk-cpptango-dev"

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Cross Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
