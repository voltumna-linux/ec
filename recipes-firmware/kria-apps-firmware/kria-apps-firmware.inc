LICENSE = "Proprietary & GPL-2.0-only"
LIC_FILES_CHKSUM = " \
	file://${UNPACKDIR}/${PN}-${PV}/LICENSE-BINARIES;md5=fa9b03ada9ffccf51dd60899bf28c702 \
	file://${UNPACKDIR}/${PN}-${PV}/LICENSE-GPLv2;md5=9afdcd1be3f71bd3791fa5961075d776 \
	"

BRANCH = "main"
SRC_URI = "git://github.com/Xilinx/kria-apps-firmware.git;protocol=https;branch=${BRANCH}"
SRCREV = "c111436a82d60b13f4092768d213e7a09c6b0972"

inherit dfx_user_dts

COMPATIBLE_MACHINE = "^$"
COMPATIBLE_MACHINE:kria = "${MACHINE}"
COMPATIBLE_MACHINE:amd-cortexa53-mali-common = "${MACHINE}"
