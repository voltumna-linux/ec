SUMMARY = "Tha kernel module driver for VME TIP501"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "48c4d07515f16641a618e38d96c20ef03b2a423d"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/tip501.git;protocol=https;branch=devel"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-tip501"

