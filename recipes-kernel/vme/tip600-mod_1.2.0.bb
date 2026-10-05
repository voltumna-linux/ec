SUMMARY = "Tha kernel module driver for VME TIP600"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "c4d1d2cd9e75a6e66dc4dbe7ba7f5f994f6cfc5d"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/tip600.git;protocol=https;branch=master"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-tip600"
