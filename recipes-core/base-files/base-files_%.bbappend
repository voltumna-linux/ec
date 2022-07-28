FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

SRC_URI_append_mvme2500 = " \
	file://vme_tsi148.conf \
	file://vme_ca91cx42.conf \
	"

do_install_append_mvme2500() {
	install -d ${D}/etc/modprobe.d
	install -m 0644 vme_tsi148.conf vme_ca91cx42.conf  \
		${D}/etc/modprobe.d/
}

