require recipes-core/images/voltumna-sde.bb
require include/runtime.inc
require include/development.inc
VARIANT="Equipment Controller (Development)"

IMAGE_INSTALL_append = " users-development"
