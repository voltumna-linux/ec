SUMMARY = "Tha kernel module driver for VME ADIO 5718"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "6545b205125e7f41e78efba938bf181f331f2d7e"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adio.git;protocol=https;branch=master"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-adio"
