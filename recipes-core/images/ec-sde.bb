require recipes-core/images/voltumna-sde.bb
require include/ec.inc

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
