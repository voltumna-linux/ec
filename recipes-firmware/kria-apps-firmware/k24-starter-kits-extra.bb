SUMMARY = "Extra firmware for the k24 starter kits"

DESCRIPTION = "Bundle of base PL bitstreams, device-tree overlays and \
shell JSON files that ship on the AMD Kria K24 Starter Kit (KD240) \
out-of-the-box."
include kria-apps-firmware.inc
inherit update-alternatives

FW_DIR = "boards/kd240/bist"
