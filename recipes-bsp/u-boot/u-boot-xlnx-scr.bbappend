do_install:append() {
    rm -fr ${D}/boot/pxeboot
    rm -fr ${D}/boot/${UBOOTPXE_CONFIG}/
}
