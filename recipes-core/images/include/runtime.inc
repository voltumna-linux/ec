IMAGE_INSTALL_append += "libmodbus libusb1 libgpiod libximc python3-pytango \
	starter users additional-udev-rules net-snmp-client"
IMAGE_INSTALL_append_vme += " kernel-module-adio adio-mod kernel-module-adios \
	adios-mod kernel-module-digio digio-mod kernel-module-tip501 tip501-mod"
