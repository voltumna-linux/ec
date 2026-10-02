SUMMARY = "Tha kernel module driver for VME TIP675"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=12f884d2ae1ff87c09e5b7ccc2c4ca7e"

inherit module

SRCREV = "2486253fd4d5740ecaf66e078354ac540495102b"
SRC_URI = "git://gitlab.elettra.eu/cs/drv/mods/tip675.git;protocol=https;branch=master"

FILES:${PN} += "${sysconfdir}/udev/rules.d"

do_install:append() {
 	install -d ${D}${sysconfdir}/udev/rules.d/
	install -m 0644 ${S}/udev/*.rules ${D}${sysconfdir}/udev/rules.d/
}

RPROVIDES:${PN} += "kernel-module-tip675"
