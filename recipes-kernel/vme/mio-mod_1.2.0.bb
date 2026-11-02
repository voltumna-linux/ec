SUMMARY = "Tha kernel module driver for VME Multi I/O"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "a70e735611057e42e2ea680b8d956daa3c972308"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/mio.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

FILES_${PN} += "${sysconfdir}/udev/rules.d"

do_install_append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES_${PN} += "kernel-module-mio"
