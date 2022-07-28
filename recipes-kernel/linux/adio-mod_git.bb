SUMMARY = "Tha kernel module driver for VME ADIO 5718"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "6a3ed9c94e2ac310bba36012735ed6fa21bc7b68"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adio.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-adio"

FILES_${PN} += "${sysconfdir}"
FILES_${PN}-dev = "${includedir}"

do_install_append() {
	oe_runmake DESTDIR=${D} install
}
