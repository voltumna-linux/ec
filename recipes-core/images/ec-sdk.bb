require recipes-core/images/voltumna-sdk.bb
require recipes-devtools/pogo/include/pogo_9.4.2.inc
require include/ec.inc

POPULATE_SDK_POST_TARGET_COMMAND += " install_pogo_into_sdk;"

append_to_osrelease() {
	cat <<-__EOF__ >> ${IMAGE_ROOTFS}/etc/os-release
	VARIANT_ID="${BPN}"
	VARIANT="Equipment Controller (Cross Development)"
	MACHINE="${MACHINE}"
	__EOF__
}
