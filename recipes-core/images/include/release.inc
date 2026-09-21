inherit voltumna-release

IMAGE_FSTYPES:append:mvme2500 = " net.tar os.tar"
IMAGE_FSTYPES:append:mvme5100 = " net.tar os.tar"
IMAGE_FSTYPES:append:mvme6100 = " net.tar os.tar"
IMAGE_FSTYPES:append:mvme7100 = " net.tar os.tar"
IMAGE_FSTYPES:append:beaglebone = " net.tar os.tar"

VOLTUMNA_NETBOOT:mvme2500 = "uboot"
VOLTUMNA_NETBOOT:beaglebone = "uboot"
