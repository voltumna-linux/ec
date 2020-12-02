require recipes-core/images/voltumna-sde.inc
require include/ec.inc
require include/ec-sxe.inc

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
