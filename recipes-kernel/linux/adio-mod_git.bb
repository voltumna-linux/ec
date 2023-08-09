SUMMARY = "Tha kernel module driver for VME ADIO 5718"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "02e896661ef4a09ae5cfe45d04290dacc6f0cfdc"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adio.git;protocol=https;branch=devel"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-adio"
