SUMMARY = "Tha kernel module driver for VME ADIOS 5750"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "291e7961f703c9bdcca7f8bdb6f5a4fb10bdf7dc"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adios.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-adios"

FILES_${PN} += "${sysconfdir}"
FILES_${PN}-dev = "${includedir}"

do_install_append() {
	oe_runmake DESTDIR=${D} install
}
