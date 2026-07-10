require recipes-kernel/linux/linux-production.inc

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

KERNEL_DEVICETREE:remove:armv7a = " \
    ti/omap/AM335X-PRU-UIO-00A0.dtbo \
    ti/omap/BB-ADC-00A0.dtbo \
    ti/omap/BB-BBBW-WL1835-00A0.dtbo \
    ti/omap/BB-BBGG-WL1835-00A0.dtbo \
    ti/omap/BB-BBGW-WL1835-00A0.dtbo \
    ti/omap/BB-BONE-4D5R-01-00A1.dtbo \
    ti/omap/BB-BONE-eMMC1-01-00A0.dtbo \
    ti/omap/BB-BONE-LCD4-01-00A1.dtbo \
    ti/omap/BB-BONE-NH7C-01-A0.dtbo \
    ti/omap/BB-CAPE-DISP-CT4-00A0.dtbo \
    ti/omap/BB-HDMI-TDA998x-00A0.dtbo \
    ti/omap/BB-I2C1-MCP7940X-00A0.dtbo \
    ti/omap/BB-I2C1-RTC-DS3231.dtbo \
    ti/omap/BB-I2C1-RTC-PCF8563.dtbo \
    ti/omap/BB-I2C2-BME680.dtbo \
    ti/omap/BB-I2C2-MPU6050.dtbo \
    ti/omap/BB-LCD-ADAFRUIT-24-SPI1-00A0.dtbo \
    ti/omap/BB-NHDMI-TDA998x-00A0.dtbo \
    ti/omap/BBORG_COMMS-00A2.dtbo \
    ti/omap/BBORG_FAN-A000.dtbo \
    ti/omap/BBORG_RELAY-00A2.dtbo \
    ti/omap/BB-SPIDEV0-00A0.dtbo \
    ti/omap/BB-SPIDEV1-00A0.dtbo \
    ti/omap/BB-UART1-00A0.dtbo \
    ti/omap/BB-UART2-00A0.dtbo \
    ti/omap/BB-UART4-00A0.dtbo \
    ti/omap/BB-W1-P9.12-00A0.dtbo \
    ti/omap/BONE-ADC.dtbo \
    ti/omap/M-BB-BBG-00A0.dtbo \
    ti/omap/M-BB-BBGG-00A0.dtbo \
    ti/omap/PB-MIKROBUS-0.dtbo \
    ti/omap/PB-MIKROBUS-1.dtbo \
"

SRC_URI:append = " \
	file://static_usb_support.cfg \
        file://6.12.39-ti-arm32-r13.patch \
	"

# 6.12.39 version for 32-bit
SRCREV:armv7a = "cdf264c0a5906063a0e2b750d420d77cb992446d"
PV:armv7a = "6.12.39+git"
BRANCH:armv7a = "linux-6.12.y"

KERNEL_GIT_URI = "git://github.com/gregkh/linux.git"

SRC_URI[sha256sum] = "f282d4d33ee9f3d679dd1e6c8290236b395ddda051346bb10e71e50c6bab2e7e"

KERNEL_CONFIG_FRAGMENTS:append = " \
	${UNPACKDIR}/static_usb_support.cfg \
	"

KERNEL_FEATURES:remove = " features/security/security.scc"

RDEPENDS:${KERNEL_PACKAGE_NAME}-base:remove:ti33x = "prueth-fw pruhsr-fw pruprp-fw"
KERNEL_DTBDEST = "${KERNEL_IMAGEDEST}"
