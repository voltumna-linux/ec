FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append:mvme5100 = " \
	file://cmdline.cfg \
	"

SRC_URI:append:mvme6100 = " \
	file://cmdline.cfg \
	"

SRC_URI:append:mvme7100 = " \
	file://cmdline.cfg \
	"

KERNEL_FEATURES:remove = " features/security/security.scc"
