# Boot-time PL loading mechanisms for `k26-smk-kr-sdt` (meta-kria/meta-xilinx rel-v2026.2)

Research note (facts only, no decision). Ticket: `.scratch/kr260-pl-nic-firmware/issues/01-research-boot-time-pl-loading.md`.
Scope: which mechanisms this stack supports to apply a PL bitstream and its
device-tree overlay **at boot** on the Kria KR260 (K26 SOM, SDT flow) **without
dfx-mgr**, for the goal of having the three PL network interfaces of the
`kria-apps-firmware` `boards/kr260/bist` bundle (`kr260-bist.bit` +
`kr260-bist.dtsi`, overlay on `&fpga_full` with
`firmware-name = "kr260-bist.bit.bin"`) present at boot.

Workspace facts are cited as `path:line` relative to the Yocto workspace
root; build outputs under `build/tmp/` are cited as evidence produced by this
exact configuration. External claims cite the owning primary source.

Note on placement: the repo has no established research-notes directory;
this file is placed under `docs/research/` (created here) per the research
skill convention.

---

## 1. What `MACHINE_FEATURES += "fpga-overlay"` actually does

Complete consumer list (grep across meta-xilinx, meta-kria, meta-voltumna and
the `ec` layer):

| Consumer | Effect when `fpga-overlay` is present |
| --- | --- |
| `meta-xilinx/meta-xilinx-core/recipes-bsp/bootbin/xilinx-bootbin.bb:118` | `BIF_BITSTREAM_ATTR ?= "${@bb.utils.contains('MACHINE_FEATURES','fpga-overlay','','bitstream',d)}"` — the `bitstream` attribute is dropped from `BIF_PARTITION_ATTR` (composed in `recipes-bsp/bootbin/machine-xilinx-zynqmp.inc`), so **BOOT.BIN contains no PL partition and the FSBL does not program the PL at initial boot** |
| `meta-xilinx/meta-xilinx-core/classes-recipe/dfx_user_dts.bbclass:188` | warns if the feature is *absent*: "Using dfx_user_dts.bbclass requires fpga-overlay MACHINE_FEATURE to be enabled" (firmware-recipe packaging expects deferred PL loading) |
| `meta-xilinx/meta-xilinx-core/lib/devtool/boot-jtag.py:61-64` | documents the semantics: *"fpga-overlay MACHINE_FEATURES is enabled, Hence PL bitstream or PDI will not be loaded at initial boot, User can load from u-boot or linux."* |
| `meta-xilinx/docs/README.dfx.user.dts.md:425-438` | pairs the feature with firmware recipes + `dfx-mgr` (or `fpgautil`) loading at Linux runtime |

So `fpga-overlay` is **only a gating flag**: "the PL is *not* programmed by the
boot image; PL firmware + DT overlay are applied later (U-Boot or Linux)".
It installs nothing and implements no overlay mechanism by itself.

Verified against this build: the generated BIF
(`build/tmp/work/k26_smk_kr_sdt-ec-linux/xilinx-bootbin/1.0/sources/bootgen.bif`)
is `fsbl.elf, pmufw.elf, bl31.elf, SMK-zynqmp-sck-kr-g-revB.dtb, u-boot.elf` —
**no bitstream partition**. This matches the Kria design intent (Kria
"Bitstream Management on Kria SOM" doc: the Kria boot firmware is
application-agnostic and "does not include any bitstream"; contrasted there
with the legacy MPSoC flow where "the bitstream is loaded prior to Linux
booting and its device tree was loaded as part of the Linux boot device tree").

Conversely, when `fpga-overlay` is *removed*, `BIF_BITSTREAM_ATTR = "bitstream"`
adds `[destination_device=pl]` with
`BIF_PARTITION_IMAGE[bitstream] = ${RECIPE_SYSROOT}/boot/bitstream/download-${MACHINE}.bit`
(machine-xilinx-zynqmp.inc), provided by `virtual/bitstream`
(`meta-xilinx/meta-xilinx-core/recipes-bsp/bitstream/bitstream_1.0.bb`,
a copy-recipe parameterized by `BITSTREAM_PATH`). That is the FSBL-loads-PL
path (mechanism (c), see §3).

## 2. How the pieces interconnect (SDT flow ↔ pl.dtso ↔ boot.scr/uEnv.txt)

### System device tree flow

- `conf/machine/k26-smk-kr-sdt.conf:72-82` (`ec` layer) sets
  `XILINX_WITH_ESW = "sdt"`, `SDT_URI` (edf.amd.com rel-v2026.2 tarball),
  `SYSTEM_DTFILE_DEPENDS = "sdt-artifacts"`,
  `SYSTEM_DTFILE = ${SYSTEM_DTFILE_DIR}/system-top.dts`.
- `meta-xilinx/meta-xilinx-standalone-sdt/recipes-bsp/sdt-artifacts/sdt-artifacts.bb`
  only downloads/extracts the SDT bundle to `/usr/share/sdt/${MACHINE}` (and
  deploys it under `system-dt/`). It is the *hardware-description source* for
  the multiconfig FSBL/PMU firmware builds; the Linux DT does not consume it
  directly here.
- The Linux domain DT is meta-kria's pre-generated
  `meta-kria/meta-kria-bsp/conf/dts/k26-smk-kr-sdt/cortexa53-linux.dts`
  (selected via `CONFIG_DTFILE`, k26-smk-kr-sdt.conf:23-25). It is compiled by
  the `device-tree` recipe (`meta-xilinx/meta-xilinx-core/recipes-bsp/device-tree/device-tree.bb`,
  which adds `file://cortexa53-linux.dts` from `CONFIG_DTFILE_DIR`'s dir) into
  `devicetree/cortexa53-linux.dtb`, deployed to the boot area
  (`IMAGE_BOOT_FILES =+ "devicetree/cortexa53-linux.dtb"`, k26-smk-kr-sdt.conf:95).
  It contains the PL-relevant skeleton: `fpga_full: fpga-region` with
  `fpga-mgr = <&zynqmp_pcap>` and `zynqmp_pcap: pcap { compatible = "xlnx,zynqmp-pcap-fpga"; }`
  (cortexa53-linux.dts:348-351, 411-418) — i.e. the kernel FPGA-manager
  plumbing, but **no PL device nodes**.
- U-Boot side: `UBOOT_DT_FILES = " zynqmp-sck-kr-g-revA.dtso zynqmp-sck-kr-g-revB.dtso "`
  (carrier-card overlays copied out of the u-boot-xlnx source tree by
  `meta-kria/meta-kria-bsp/recipes-bsp/device-tree/device-tree.bbappend:23-26`).
  With `UBOOT_DTFILES_BUNDLE:kria:linux = "1"` the device-tree recipe
  **merges those overlays at build time** with `fdtoverlay` into
  `SMK-zynqmp-sck-kr-g-revA/B.dtb` (device-tree.bb:124-133). `DTC_FLAGS:kria += "-@"`
  keeps `__symbols__` in the base dtb (needed by any later overlay
  application). `DTB_FILE_PATH` (the SMK-…-revB dtb) is placed in BOOT.BIN as
  the `device-tree` partition (`BIF_PARTITION_IMAGE[device-tree]`,
  k26-smk-kr-sdt.conf:92). This matches the AMD "U-Boot Handoff" doc: the
  U-Boot DT is integrated into BOOT.BIN while the Linux DTs live on the boot
  media, and `boot.scr` selects the SOM+CC DT.

### `pl-overlay-full/pl.dtso` (meta-kria)

`meta-kria/meta-kria-bsp/conf/dts/k26-smk-kr-sdt/pl-overlay-full/pl.dtso` is
AMD SDT-export output (arrives via the "EDF Files update" commits of
meta-kria). It is a `/plugin/` overlay on `&fpga_full` setting
`firmware-name = "kr260_base.bit.bin"` plus `clocking0/1` (xlnx,fclk) and
`afi0` (xlnx,afi-fpga) nodes — the *base* PL design description
(`kr260_base.bit.bin` is the out-of-box design shipped by the
`kria-base-firmware` repo, packaged in this workspace by
`meta-kria/meta-kria-bsp/recipes-firmware/kria-base-firmware/k26-starter-kits_1.0-2023.1.bb`,
a `dfx_user_dts`-based firmware recipe).

**No recipe currently consumes `pl.dtso`** (grep finds no reference to
`pl-overlay-full`/`pl.dtso` anywhere outside the file itself; the
device-tree build dir `build/tmp/work/k26_smk_kr_sdt-ec-linux/device-tree/1.0/build/`
contains only `cortexa53-linux.dtb`, the `zynqmp-sck*.dtbo` and the
`SMK-*` bundles — **no `pl.dtbo`**). The intent is visible in
`device-tree.bbappend:37-39`: `do_install:append` removes the U-Boot carrier
`zynqmp-sck*.dtbo` "these are no usable" but explicitly **"keep pl.dtbo"** —
i.e. the expected pattern is that a `pl.dtso` copied into the device-tree
build gets compiled to `pl.dtbo` (the `devicetree` class compiles any overlay
in `DT_FILES_PATH`, `meta-voltumna/openembedded-core/meta/classes-recipe/devicetree.bbclass:137-151`)
and deployed next to the dtbs. Wiring it in would be done with
`EXTRA_DT_FILES` (compile + deploy as dtbo) or `EXTRA_DT_INCLUDE_FILES`
(build-time merge, see §3c), both supported by device-tree.bb:31-32, 49-50, 92-104.

### ec `boot.scr` / `uEnv.txt`

`recipes-bsp/u-boot/files/k26-smk-kr-sdt/{boot.scr,uEnv.txt}` are consumed by
**`u-boot-dummy`** (`recipes-bsp/u-boot/u-boot-dummy.bb`, via
`FILESEXTRAPATHS` machine-override dirs and `u-boot-script.inc`):
`boot.scr` (plain-text source) is token-substituted (`@ROOT@`, `@ETH@`, …) and
compiled with `mkimage -T script` into `boot.scr.uimg`; `uEnv.txt` gets
`@IMAGE_NAME@`/`@USR@` substituted at deploy (`u-boot-dummy.bb do_deploy`).
On `k26-smk-kr-sdt`, `u-boot-dummy` explicitly does *not* provide
`virtual/bootloader` ("Kria BOOT.bin still needs the real U-Boot ELF. On these
machines this recipe is built explicitly only to deploy boot.scr.uimg and
uEnv.txt", u-boot-dummy.bb:18-21).

The ec `boot.scr` flow: load `/uEnv.txt` → `env import` → load
`${fdtfile}` and `${krnfile}` from `/.osdir/${image}/boot/` →
`bootm ${loadaddr} - ${fdtaddr}`. **It contains no `fpga …` and no
`fdt … apply` step** — today it is a pure kernel/dtb loader. (Its live wiring
into the image is currently commented out at
`conf/machine/k26-smk-kr-sdt.conf:101-113`; the artifacts are nonetheless
deployed: `build/tmp/deploy/images/k26-smk-kr-sdt/{boot.scr.uimg,uEnv.txt}`.)

For contrast, the stock meta-xilinx boot script
(`meta-xilinx/meta-xilinx-core/recipes-bsp/u-boot/u-boot-xlnx-scr/xilinx-default-sd.cmd`)
has a built-in bitstream hook:
`if test -n "@@BITSTREAM@@"; then … fpga @@BITSTREAM_LOAD_TYPE@@ 0 @@BITSTREAM_LOAD_ADDRESS@@ ${filesize}; fi`
(`loadb` for `.bit`, `load` for `.bin` — `u-boot-xlnx-scr.bb:220-238`,
`image-wic-utils.bbclass:30-46` fills the tokens from `IMAGE_BOOT_FILES`);
and the stock `uEnv.txt` generator
(`meta-xilinx/meta-xilinx-core/recipes-bsp/u-boot/u-boot-xlnx-uenv.bb:65-88`)
generates both `loadfpga` (`fatload … && fpga ${bitstream_type} 0 …`) and
`loaddtbo` (`fdt addr … ; fdt resize 8192 ; fdt apply ${devicetree_overlay_load_address}`)
hooks. **The stock meta-xilinx U-Boot flow therefore already implements both
"program PL" and "apply DT overlay" in U-Boot**; the ec boot.scr replaces it.

U-Boot capability in the kernel... in the built U-Boot
(`build/tmp/work/k26_smk_kr_sdt-ec-linux/u-boot-xlnx/2026.01-xilinx-v2026.1+git/build/.config`):
`CONFIG_CMD_FPGA=y` (+ `LOADB/LOADP/LOADBP`, `LOAD_SECURE`), `CONFIG_FPGA=y`,
`CONFIG_FPGA_ZYNQMPPL=y` (ZynqMP PL manager), `CONFIG_OF_LIBFDT_OVERLAY=y`
(`fdt apply`), `CONFIG_OF_BOARD=y`.

## 3. Candidate mechanisms qualified

### (a) U-Boot loads the bitstream and applies the overlay before the kernel

**Supported by this stack**, and demonstrated by the stock meta-xilinx boot
artifacts.

- Intervention point: the ec `boot.scr`/`uEnv.txt` pair (u-boot-dummy). Add
  `fpga load|loadb 0 ${addr} ${filesize}` for the bitstream and
  `fdt addr ${fdtaddr}; fdt resize; fdt apply ${dtboaddr}` for the overlay
  before `bootm` (both commands are compiled in, see §2). A FIT-based boot
  (`bootm ${addr_fit}#conf`) is another U-Boot-side overlay vehicle but is not
  used in this flow (Image + separate dtb).
- Prerequisites: bitstream on the boot media as `.bin` (`fpga load`) or
  `.bit` (`fpga loadb`); dtbo compiled with `-@` (yes — `DTC_OFLAGS`),
  target dtb built with `__symbols__` (yes — `DTC_BFLAGS`/`DTC_FLAGS:kria`
  carry `-@`, padding `DT_PADDING_SIZE:kria = "0x1000"`); the overlay must be
  applied to the **same fdt handed to the kernel** (the ec boot.scr boots
  `${fdtaddr}` after loading `${fdtfile}` from disk — apply to that one; the
  stock xilinx-default-sd.cmd instead boots `${fdtcontroladdr}`).
- Limits: the PL design is fixed at boot (no runtime swap without reboot);
  the boot script becomes design-specific; a boot.scr change requires
  redeploying the boot media; U-Boot-side programming is invisible to the
  Linux fpga-manager state (it boots "unpowered"); Kria QSPI boot means
  BOOT.BIN is not touched by this (good: no QSPI update needed).

### (b) Kernel fpga-manager + overlay during Linux boot (init/systemd)

**Supported by this stack**; this is the AMD Kria-native pattern minus dfx-mgr.

- Kernel config (verified in *both* built kernels,
  `build/tmp/work/k26_smk_kr_sdt-ec-linux/linux-xlnx/{6.12.10+git,6.18.40+git+v2026.2}/linux-k26_smk_kr_sdt-standard-build/.config`):
  `CONFIG_FPGA=y`, `CONFIG_FPGA_MGR_ZYNQMP_FPGA=y`, `CONFIG_FPGA_REGION=y`,
  `CONFIG_FPGA_BRIDGE=y`, `CONFIG_OF_OVERLAY=y`, `CONFIG_OF_CONFIGFS=y`,
  `CONFIG_OF_DYNAMIC=y`. The DT provides `fpga_full`/`zynqmp_pcap`.
- Tooling present in the layers: `meta-xilinx/meta-xilinx-core/recipes-bsp/fpga-manager-script/`
  (`fpgautil`), documented in `meta-xilinx/docs/README.dfx.user.dts.md:430-433`
  as the non-dfx-mgr loader: `fpgautil -b <bin> -o <dtbo>`.
- Mechanics (primary source `fpgautil.c:344-435`): copies `.bin`/`.dtbo` into
  `/lib/firmware`, sets `/sys/class/fpga_manager/fpga0/flags`, then writes the
  dtbo filename to `/configfs/device-tree/overlays/<region|full>/path`
  (kernel configfs OF-overlay). Because the overlay targets `&fpga_full`
  (an fpga-region) and adds children, the kernel's `of-fpga-region`
  (`Documentation/driver-api/fpga/fpga-region.rst`, `drivers/fpga/of-fpga-region.c`)
  then requests the firmware named by the region's `firmware-name` property
  and programs the PL via the fpga-manager (`Documentation/driver-api/fpga/fpga-mgr.rst`)
  before probing the new nodes. That is why fpgautil copies the `.bin` into
  `/lib/firmware/` top level and deletes it afterwards.
- Packaging: `dfx_user_dts.bbclass` (used by `k26-starter-kits`) compiles the
  dtsi/dtso, rewrites `firmware-name` to `${PN}.bin` and installs
  `${PN}.bin` + `${PN}.dtbo` under `/lib/firmware/xilinx/${PN}/`
  (dfx_user_dts.bbclass:184-204, 278-329). The `boards/kr260/bist` bundle
  (`kr260-bist.bit` + `kr260-bist.dtsi` + `shell.json`, verified at
  `/home/alessio.bogani/Sources/git-trees/kria-apps-firmware/boards/kr260/bist/`)
  fits this recipe pattern unchanged. **Caveat**: if the overlay is applied
  via plain configfs (no fpgautil), `request_firmware()` resolves
  `firmware-name` relative to `/lib/firmware/`, so the init hook must
  copy/symlink the `.bin` accordingly (fpgautil does this for you).
- Intervention point: a systemd oneshot (or init script) running early in
  boot — either `fpgautil -b … -o …` or the raw configfs writes. (The AMD
  "default firmware at boot" mechanism is `/etc/dfx-mgrd/default_firmware`,
  which is dfx-mgr and therefore out of scope.)
- Limits: PL netdevs appear only after the unit runs (ordering vs
  systemd-networkd/udev; interfaces may need a networkd restart/udev settle);
  load failures are log-only; the overlay may be applied only once
  (fpgautil errors out if the overlay dir exists); no PL devices at all
  before the hook runs (initramfs stage excluded).

### (c) Overlay merged into the DT at build time + only the bitstream loaded at boot

**Supported by this stack**, with three build-time merge primitives:

1. `EXTRA_DT_INCLUDE_FILES` (device-tree.bb:32, 50, 92-104): appends
   `#include "<file>"` for each dtsi to `BASE_DTS` — merges the PL nodes into
   `cortexa53-linux.dts` before compilation (works with the bist dtsi's
   `&fpga_full { … }` syntax).
2. `EXTRA_DTFILES_BUNDLE` + `EXTRA_DTFILE_PREFIX` (device-tree.bb:113-122):
   `fdtoverlay`-merges compiled `.dtbo`s into the base dtb at build time.
3. Directly extending/copying the `CONFIG_DTFILE` dts (it is a layer file
   under `conf/dts/k26-smk-kr-sdt/`; `CONFIG_DTFILE` can point at an
   ec-layer copy).

Bitstream-at-boot options once the nodes are static:

- **FSBL from BOOT.BIN**: drop `fpga-overlay` → `BIF_BITSTREAM_ATTR="bitstream"`
  → `destination_device=pl` partition fed by `virtual/bitstream`
  (`bitstream_1.0.bb`, `BITSTREAM_PATH`). This is AMD's "legacy MPSoC flow"
  (Kria "Bitstream Management" doc). **Caveat for Kria**: BOOT.BIN lives in
  the QSPI A/B boot partitions and is updated via `xmutil bootfw_update` /
  Boot Image Recovery Tool (Kria BootFW docs) — every PL design change then
  means a QSPI boot-firmware update, defeating the decoupled lifecycle.
- **U-Boot** `fpga load/loadb` (mechanism (a) minus the `fdt apply`).
- **Kernel fpga-manager sysfs** early in boot:
  `echo <name> > /sys/class/fpga_manager/fpga0/firmware` (with flags), as
  `fpgautil -b` does (`fpgautil.c:443-452`).

Limits: the DT permanently claims PL devices — probe/deferred-probe errors
and wrong hardware description if the load fails or the design changes; the
merged `firmware-name` on `fpga_region` must be kept inert (a stray
overlay-apply event on the region would trigger reprogramming); one design
per image; coupling between rootfs image and PL design.

## 4. Cross-check against AMD/Xilinx and kernel primary documentation

- **Kria Bitstream Management** (xilinx.github.io/kria-apps-docs/creating_applications/2022.1/build/html/docs/bitstream_management.html):
  explicit legacy-vs-Kria table — legacy = "bitstream loaded prior to the boot
  of the OS … its device tree was loaded as part of the Linux boot device
  tree" (= mechanism (c)); Kria = "application-agnostic boot firmware that does
  not include any bitstream … managed within the Linux file system …
  accompanied by … a Linux DT-overlay" (= mechanism (b), AMD ships it with
  dfx-mgr/xmutil).
- **Kria SOM Development Overview**
  (…/2022.1/build/html/docs/overview.html): "The bitstream can be loaded at
  boot as part of the device boot firmware, or after the OS boot via a runtime
  library." — both (c) and (b) sanctioned.
- **Yocto Kria Support**
  (xilinx.github.io/kria-apps-docs/yocto/build/html/docs/yocto_kria_support.html):
  "Updating Default Bitstream" — the default PL firmware on Kria is loaded
  *during the Linux boot* from `/lib/firmware/xilinx/k26-starter-kits/` (by
  dfx-mgr), managed by the `kria-base-firmware` recipe; annotated "this is for
  none-SDT flow (XSCT) only".
- **Kria U-Boot Handoff**
  (…/bootfw/build/html/docs/bootfw_uboot_handoff.html): `boot.scr` selects the
  Linux DT from the SOM+CC DTs on the boot media and sets bootargs — matches
  the observed ec boot.scr role (load `${fdtfile}`), and confirms U-Boot is a
  viable intervention point before kernel boot.
- **meta-xilinx `README.dfx.user.dts.md`**: "User can use fpgautil or dfx-mgr
  tool to load .bin/pdi and dtbo at runtime linux" — fpgautil is the blessed
  dfx-mgr-free loader.
- **Kernel docs**: `Documentation/driver-api/fpga/fpga-mgr.rst` (manager
  programming API incl. firmware-name based programming) and
  `fpga-region.rst` + `devicetree/bindings/fpga/fpga-region.txt`
  (of-fpga-region: overlay-driven programming + DT enumeration) — the
  mechanism behind (b)'s configfs flow.

## 5. Fact summary (for the decision ticket 03)

1. `fpga-overlay` only removes the PL from BOOT.BIN (FSBL does not program
   the PL); it is required by `dfx_user_dts` packaging and is the correct
   setting for mechanisms (a) and (b). For (c)+FSBL it must be removed.
2. All three mechanisms are supported by this exact stack:
   (a) U-Boot `fpga load(b)` + `fdt apply` (commands compiled in; stock
   meta-xilinx scripts prove the pattern; the ec boot.scr/uEnv.txt is the
   hook and currently has neither step), (b) kernel fpga-manager + configfs
   overlay applied by an early-boot unit (kernel config fully enabled;
   `fpgautil` in-stack; `dfx_user_dts`-packaged firmware lands at
   `/lib/firmware/xilinx/<pn>/`), (c) build-time DT merge
   (`EXTRA_DT_INCLUDE_FILES` / `EXTRA_DTFILES_BUNDLE`) + bitstream via FSBL
   (QSPI BOOT.BIN update on Kria!), U-Boot, or fpga-mgr sysfs.
3. The bist bundle maps 1:1 onto the `dfx_user_dts` firmware-recipe pattern
   (`k26-starter-kits` is the template); `kr260-bist.dtsi`'s `firmware-name`
   is rewritten to `<pn>.bin` by the class.
4. `pl-overlay-full/pl.dtso` is inert reference material today (nothing
   builds it); it documents the base-design overlay shape (`&fpga_full` +
   `firmware-name` + clocking/afi) that `kr260-bist.dtsi` follows.
5. Constraints that differentiate the options: QSPI BOOT.BIN update needed
   for FSBL-loaded PL on Kria; boot-time (b) makes netdevs appear after the
   init hook (network-manager ordering); (c) makes the DT describe hardware
   unconditionally.