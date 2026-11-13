require recipes-core/images/voltumna-sre.inc
require include/runtime.inc
VARIANT="Equipment Controller (Runtime)"

IMAGE_INSTALL:append = " users"
PACKAGE_EXCLUDE += "python3-core"
