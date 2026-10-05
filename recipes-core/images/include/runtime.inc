IMAGE_INSTALL_append += "libmodbus libusb1 libgpiod libximc \
	starter users additional-udev-rules net-snmp-client boost"
IMAGE_INSTALL_append_vme += " kernel-module-adio adio-mod kernel-module-adios \
	adios-mod kernel-module-digio digio-mod kernel-module-mio mio-mod \
        kernel-module-pscip pscip-mod kernel-module-tip501 tip501-mod \
        kernel-module-tip551 tip551-mod kernel-module-tip600 tip600-mod \
        kernel-module-tip675 tip675-mod kernel-module-tip700 tip700-mod"
