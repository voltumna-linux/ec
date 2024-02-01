SUMMARY = "Tha kernel module driver for VME ADIOS 5750"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "b4b5b3bf53ecc9a6ae0895cc9f035356bfdb68b9"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adios.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

FILES_${PN} += "${sysconfdir}/udev/rules.d"

do_install_append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES_${PN} += "kernel-module-adios"
