SUMMARY = "Tha kernel module driver for VME ADIOS 5750"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "eee4224a67fa2244d0f1ab85a3a43d94ee4a42e6"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/adios.git;protocol=https;branch=devel"

S = "${WORKDIR}/git"

RPROVIDES_${PN} += "kernel-module-adios"
