SUMMARY = "Tha kernel module driver for VME DIGIO 5715"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "7a4d4d0db6a7df59308105fc0525dbe6db2a7825"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/digio.git;protocol=https;branch=devel"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-digio"
