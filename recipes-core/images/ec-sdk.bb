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

install_ldlinux_sh() {
	mkdir -p ${SDK_OUTPUT}/${SDKTARGETSYSROOT}/environment-setup.d/
	cat <<-__EOF__ >> ${SDK_OUTPUT}${SDKTARGETSYSROOT}/environment-setup.d/ldlinux.sh
	export SDKTARGETLOADER="/usr/lib/ld-linux-x86-64.so.2"
	__EOF__
}

POPULATE_SDK_POST_TARGET_COMMAND_append_x86-64 = " install_ldlinux_sh;"
