SUMMARY = "Extra firmware for the k26 starter kits"

DESCRIPTION = "Bundle of base PL bitstreams, device-tree overlays and \
shell JSON files that ship on the AMD Kria K26 Starter Kits (KR260, \
KV260) out-of-the-box."
include kria-apps-firmware.inc
inherit update-alternatives

FW_DIR = "boards/kr260/bist"
