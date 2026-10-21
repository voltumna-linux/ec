# Keep a copy of the standalone device trees alongside the kernel image.
#
# The dtbs must stay installed in /boot/devicetree so they remain visible
# through the sysroot: xilinx-bootbin resolves the device-tree partition via
# ${RECIPE_SYSROOT}/boot/devicetree, and moving them out of SYSROOT_DIRS
# breaks do_configure ("Partition file does not exist").

do_install:append() {
    if [ -n "${KERNEL_IMAGEDEST}" ] && [ -d "${D}/boot/devicetree" ]; then
        install -d "${D}/${KERNEL_IMAGEDEST}"
        cp -a "${D}/boot/devicetree"/* "${D}/${KERNEL_IMAGEDEST}/"
    fi
}

# Staging only copies directories listed in SYSROOT_DIRS; without this the
# copy made above never reaches consumers' recipe-sysroots (u-boot-xlnx
# resolves EXT_DTB as ${RECIPE_SYSROOT}/${KERNEL_IMAGEDEST}/<dtb>.dtb).
SYSROOT_DIRS:append = "${@' /%s' % d.getVar('KERNEL_IMAGEDEST') if d.getVar('KERNEL_IMAGEDEST') else ''}"

FILES:${PN}:append = "${@' /%s/*.dtb /%s/*.dtbo' % (d.getVar('KERNEL_IMAGEDEST'), d.getVar('KERNEL_IMAGEDEST')) if d.getVar('KERNEL_IMAGEDEST') else ''}"
