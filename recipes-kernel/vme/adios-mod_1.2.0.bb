SUMMARY = "Tha kernel module driver for VME ADIOS 5750"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "3f0a519eaf9fd9f4a933bf7f1d6df73fc8b381fc"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adios.git;protocol=https;branch=master"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-adios"
