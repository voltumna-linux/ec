require recipes-core/images/voltumna-sre.bb
require include/ec.inc
require include/ec-sxe.inc

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Runtime)"
	MACHINE="${MACHINE}"
	__EOF__
}
