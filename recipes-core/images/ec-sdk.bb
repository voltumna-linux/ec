require recipes-core/images/voltumna-sdk.inc
require include/ec.inc

TOOLCHAIN_HOST_TASK_append += "nativesdk-pogo"

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Cross Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
