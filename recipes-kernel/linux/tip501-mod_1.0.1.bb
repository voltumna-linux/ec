SUMMARY = "Tha kernel module driver for VME TIP501"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "c2456f9e60bfbb61fba13a22bb093481385bb3d6"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/tip501.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-tip501"
