require recipes-core/images/voltumna-sde.bb
require include/ec.inc

IMAGE_INSTALL_append += "libmodbus-dev libusb1-dev libgpiod-dev \
	libximc-dev cpptango-dev net-snmp-dev"

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
