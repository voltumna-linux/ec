IMAGE_INSTALL_append += "libmodbus libusb1 libgpiod libximc python3-pytango"

ROOTFS_POSTPROCESS_COMMAND_append += " append_to_osrelease;"
