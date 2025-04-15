DESCRIPTION = "This recipe adds users to an image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

DEPENDS:append = "bash systemd"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM_${PN} = " \
	--uid 10000 --user-group --groups dialout --no-create-home \
            --home-dir /var/run/tango --shell /bin/nologin controls; \
	--uid 11000 --groups controls,dialout --no-create-home \
            --home-dir /home/lorenzo.pivetta --shell /bin/bash lorenzo.pivetta; \
	--uid 11001 --groups controls,dialout --no-create-home \
            --home-dir /home/alessio.bogani --shell /bin/bash alessio.bogani; \
	--uid 11002 --groups controls,dialout --no-create-home \
            --home-dir /home/giulio.gaio --shell /bin/bash giulio.gaio; \
	--uid 11003 --groups controls,dialout --no-create-home \
            --home-dir /home/graziano.scalamera --shell /bin/bash graziano.scalamera; \
	--uid 11004 --groups controls,dialout --no-create-home \
            --home-dir /home/claudio.scafuri --shell /bin/bash claudio.scafuri; \
	--uid 11005 --groups controls,dialout --no-create-home \
            --home-dir /home/alessandro.abrami --shell /bin/bash alessandro.abrami; \
	--uid 11006 --groups controls,dialout --no-create-home \
            --home-dir /home/martin.scarcia --shell /bin/bash martin.scarcia; \
	--uid 11008 --groups controls,dialout --no-create-home \
            --home-dir /home/adriano.contillo --shell /bin/bash adriano.contillo; \
	"

# Prevents do_package failures with:
# debugsources.list: No such file or directory:
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

ALLOW_EMPTY_${PN} = "1"

inherit useradd
