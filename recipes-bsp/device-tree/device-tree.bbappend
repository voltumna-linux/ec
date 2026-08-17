# Keep the standalone device trees alongside the kernel image.

do_install:append() {
    if [ -n "${KERNEL_IMAGEDEST}" ] && [ -d "${D}/boot/devicetree" ]; then
        install -d "${D}/${KERNEL_IMAGEDEST}"
        mv "${D}/boot/devicetree"/* "${D}/${KERNEL_IMAGEDEST}/"
        rmdir "${D}/boot/devicetree"
    fi
}

SYSROOT_DIRS:remove = "${@'/boot/devicetree' if d.getVar('KERNEL_IMAGEDEST') else ''}"
SYSROOT_DIRS:append = "${@' /%s' % d.getVar('KERNEL_IMAGEDEST') if d.getVar('KERNEL_IMAGEDEST') else ''}"

FILES:${PN}:remove = "${@'/boot/devicetree/*.dtb /boot/devicetree/*.dtbo' if d.getVar('KERNEL_IMAGEDEST') else ''}"
FILES:${PN}:append = "${@' /%s' % d.getVar('KERNEL_IMAGEDEST') if d.getVar('KERNEL_IMAGEDEST') else ''}"
