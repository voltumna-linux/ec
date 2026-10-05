SUMMARY = "Tha kernel module driver for PSI PS CIP"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "3d5ecc8b71c1bb1b5ba805f121fd4ec240703b4c"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/pscip.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

FILES_${PN} += "${sysconfdir}/udev/rules.d"

do_install_append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES_${PN} += "kernel-module-digio"
