SUMMARY = "Tha kernel module driver for PSI PS CIP"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "3d5ecc8b71c1bb1b5ba805f121fd4ec240703b4c"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/pscip.git;protocol=https;branch=master"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-pscip"
