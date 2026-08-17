FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://0001-scripts-Makefile.xpl-fix-SPL-alignment-with-GNU-dd.patch"

# device-tree.bbappend stages DTBs alongside the kernel image instead of the
# meta-xilinx default /boot/devicetree directory.
DTB_PATH = "${KERNEL_IMAGEDEST}"
