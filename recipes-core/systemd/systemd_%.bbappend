FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

do_install:append() {
	# Make log persistent	
	mkdir -p ${D}/var/log/journal
}

