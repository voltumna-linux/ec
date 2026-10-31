# Packaging/install facts for the `boards/kr260/bist` bundle under the current workspace flow

Research note (facts only, no decisions). Ticket:
`.scratch/kr260-pl-nic-firmware/issues/02-research-packaging-install-paths.md`.
Scope: what the recipes `recipes-firmware/kria-apps-firmware/` plus
`dfx_user_dts.bbclass` actually build and install for the upstream
`kria-apps-firmware` bundle `boards/kr260/bist` (`kr260-bist.bit`,
`kr260-bist.dtsi` with `firmware-name = "kr260-bist.bit.bin"`, `shell.json`),
where the files land at runtime, who resolves them, and whether the overlay's
label requirements are satisfied by the `k26-smk-kr-sdt` base device tree.

Workspace facts are cited as `path:line` relative to the Yocto workspace root;
build outputs under `build/tmp/` are cited as evidence produced by this exact
configuration (previous builds of `k26-starter-kits-extra` and `device-tree`
are present in `tmp/work` and `tmp/deploy`; nothing new was built for this
note). External claims cite the owning primary source (upstream repo + commit,
kernel source shipped in `tmp/work-shared`, Xilinx userspace source at the
SRCREV pinned by the recipes).

---

## 1. How `firmware-name` is reconciled with the bootgen output

**Mechanism: the DT source is patched in-place and the bootgen output is named
to match — both end up using `${PN}` as the base name.**

The recipe chain is: `k26-starter-kits-extra.bb` → `kria-apps-firmware.inc`
(git fetch of `kria-apps-firmware` @ `c111436`, `inherit dfx_user_dts`) →
`dfx_user_dts.bbclass` (`meta-xilinx/meta-xilinx-core/classes-recipe/`),
which itself does `inherit devicetree bootgen-bif`
(`dfx_user_dts.bbclass:10`) with `DEPENDS = "dtc-native bootgen-native"`
(`:12`).

Step by step for this bundle (`PN = "k26-starter-kits-extra"`,
`FW_DIR = "boards/kr260/bist"`; `S` becomes
`${UNPACKDIR}/${BP}/boards/kr260/bist` for a git SRC_URI,
`dfx_user_dts.bbclass:44-54`):

1. **Locate the file carrying `firmware-name`** — `find_firmware_file()`
   (`dfx_user_dts.bbclass:143-180`) scans `S` for `*.dts*` containing the
   literal `firmware-name`, priority `.dtso > .dtsi > .dts`. Here the only
   match is `kr260-bist.dtsi` → `FIRMWARE_NAME_DT_FILE = "kr260-bist.dtsi"`.
2. **Patch the DT source** — `do_configure` (`:184-204`) rewrites the property
   line with `re.sub('firmware-name.*\".*\"', 'firmware-name = "<PN>.bin"')`
   for zynq/zynqmp (`:200`). So `firmware-name = "kr260-bist.bit.bin"` becomes
   **`firmware-name = "k26-starter-kits-extra.bin"`**. The comment at `:191`
   states the reason: the bitstream is going to be renamed to `${PN}` when the
   bin is generated. Verified in the built tree:
   `build/tmp/work/k26_smk_kr_sdt-ec-linux/k26-starter-kits-extra/1.0/sources/k26-starter-kits-extra-1.0/boards/kr260/bist/kr260-bist.dtsi:15`.
3. **Compile the overlay** — the `devicetree` class preprocesses the dtsi with
   `gcc -E` and runs `dtc -R 8 -b 0 -p 0 -@ -H epapr ... -o kr260-bist.dtbo`
   (note `-@`, so the dtbo carries `__fixups__`; see
   `.../k26-starter-kits-extra/1.0/temp/log.do_compile`). Output in `${B}`:
   `kr260-bist.dtbo` (named after the source, not `${PN}`).
4. **Convert the bitstream** — `devicetree_do_compile:append`
   (`dfx_user_dts.bbclass:208-245`) writes a one-line bif and runs
   `bootgen -arch zynqmp -w -image <bif> -o k26-starter-kits-extra.bin`
   (BOOTGEN_FLAGS `:19`). Output in `${B}`: **`k26-starter-kits-extra.bin`**
   (i.e. upstream's `kr260-bist.bin` name is not used at all).
   Verified: `.../k26-starter-kits-extra/1.0/build/` contains
   `k26-starter-kits-extra.bif`, `k26-starter-kits-extra.bin` (6 592 044 B),
   `kr260-bist.dtbo` (11 302 B), `kr260-bist.dtsi.pp`.
5. **Install** — `do_install` (`:278-382`) then packages, **renaming to `${PN}`**:
   * dtbo: since exactly one dtbo was *built* in `${B}` (not shipped as input),
     `:295-296` installs `${B}/kr260-bist.dtbo` as **`${PN}.dtbo`** =
     `k26-starter-kits-extra.dtbo`.
   * bin: `:324-325` installs `${B}/${PN}.bin` as **`${PN}.bin`** =
     `k26-starter-kits-extra.bin` (this branch is taken because a dtbo exists
     in `${B}`; the raw `.bit` is *not* installed — `:316-319` only installs a
     `.bit` when no dtbo was built).
   * json: `:378-381` installs `shell.json` verbatim (unchanged name).
   * `do_deploy[noexec] = "1"` (`:406`) — nothing is deployed to
     `tmp/deploy/images`; the whole output is packaged in `${PN}`
     (`FILES:${PN}` `:408`).

**Final installed names** (verified in the previous build's image root,
`.../k26-starter-kits-extra/1.0/image/`):

```
/usr/lib/firmware/xilinx/k26-starter-kits-extra/k26-starter-kits-extra.dtbo
/usr/lib/firmware/xilinx/k26-starter-kits-extra/k26-starter-kits-extra.bin
/usr/lib/firmware/xilinx/k26-starter-kits-extra/shell.json
```

So relative to upstream (`boards/Makefile` builds `kr260-bist.bin` /
`kr260-bist.dtbo` and installs to `/lib/firmware/xilinx/kr260-bist/`): the
**file names are re-derived from `${PN}`** for both the bin and the dtbo (the
dtsi property is patched to agree), and only `shell.json` keeps its name.
The dtbo is self-consistent: its `firmware-name` is
`k26-starter-kits-extra.bin`, which is exactly the name of the installed bin
in the same directory.

`shell.json` content (upstream, unmodified):
`{"shell_type" : "XRT_FLAT", "num_slots": "1"}`.

## 2. Runtime location and lookup paths

**Where they land.** `FW_INSTALL_DIR ?= "${PN}"`
(`dfx_user_dts.bbclass:23`) and the install prefix is
`${D}/${nonarch_base_libdir}/firmware/xilinx/${FW_INSTALL_DIR}`
(`:279`) → `/usr/lib/firmware/xilinx/k26-starter-kits-extra/`. Neither
`k26-starter-kits-extra.bb`, `k24-starter-kits-extra.bb` nor
`kria-apps-firmware.inc` overrides `FW_INSTALL_DIR` (they only set `FW_DIR`,
the *source* directory); the same holds for meta-kria's
`k26-starter-kits_1.0-2023.1.bb` + `kria-base-firmware.inc`. So the runtime
directory is named after **`${PN}`**, not after upstream's
`<board>-<app>` (`kr260-bist`) convention. The target rootfs is usr-merged
(`lib -> usr/lib` in the built cpio), so `/usr/lib/firmware/xilinx/...`
resolves as `/lib/firmware/xilinx/...`, the same tree AMD documents
(`meta-xilinx/docs/README.dfx.user.dts.md:447`:
`<target_rootfs>/lib/firmware/xilinx/${FW_INSTALL_DIR}`; `:401-422` documents
`FW_INSTALL_DIR`, e.g. `FW_INSTALL_DIR = "zcu111/pl"`).

**Who applies the overlay and looks the files up.** Two lookup layers:

* **Kernel firmware class** (`request_firmware`): the search path is
  `fw_path[]` = `firmware_class.path` module param, then
  `/lib/firmware/updates/$UTS_RELEASE`, `/lib/firmware/updates`,
  `/lib/firmware/$UTS_RELEASE`, `/lib/firmware`
  (kernel source shipped with this build:
  `build/tmp/work-shared/k26-smk-kr-sdt/kernel-source/drivers/base/firmware_loader/main.c:472-478`).
  It does **not** recurse into `xilinx/<dir>/`. The bare name
  `k26-starter-kits-extra.bin` from the overlay would therefore *not* be found
  by the kernel on its own.
* **dfx-mgr** (the Kria loader; `xmutil` `RDEPENDS` on `dfx-mgr`,
  `meta-kria/meta-kria-core/recipes-utils/xmutil/xmutil_1.5.bb`) is what makes
  it work. Its `daemon.conf` declares
  `"firmware_location": ["/lib/firmware/xilinx"]` (Xilinx/dfx-mgr @
  `076f8b30`, `src/daemon.conf`) — each *app* is a **subdirectory** of that
  root containing `shell.json`; the directory name is the load name
  (`dfx-mgr-client -loadByName <name>`, `src/scripts/xlnx-firmware-load`,
  which also auto-selects `<boardid>-starter-kits` from the EEPROM board-ID
  via `daemon.conf`'s `eeprom_name_map`, e.g. `K26 → k26`, `SCK-KR → kr260`).
  For flat shells (`shell_type: XRT_FLAT`, exactly our `shell.json`) it calls
  `user_load_from_dir(base->base_path, ...)`
  (`src/daemon_helper.c:291-320`) which:
  1. finds the first `*.bin|*.bit|*.pdi` and `*.dtbo` **inside the app dir**
     (`src/user_load.c:271-300`);
  2. points the kernel firmware search path at that dir by writing it to
     `/sys/module/firmware_class/parameters/path`
     (Xilinx/libdfx `src/libdfx.c:1873-1888`, `dfx_set_firmware_search_path()`;
     called from `src/user_load.c:372-379`);
  3. applies the dtbo through configfs
     `/sys/kernel/config/device-tree/overlays/<region>`
     (`src/user_load.h:22-23`, `src/user_load.c:170-212`), and
  4. programs the PL by writing the **basename** to
     `/sys/class/fpga_manager/fpga0/firmware`
     (`src/user_load.c:110-139`, `src/user_load.c:354`;
     libdfx `src/libdfx.c:393-409`).

  AMD's own README shows the same contract via `fpgautil` with explicit full
  paths (`meta-xilinx/docs/README.dfx.user.dts.md:524-526`), and its dmesg
  shows the overlay adding `firmware-name` to `/fpga-region`.
* Independently of the loader, the kernel's OF FPGA-region notifier programs
  the FPGA when an overlay adds a `firmware-name` property to an fpga-region
  (`drivers/fpga/of-fpga-region.c:350` ff., `:242` reads the property and
  `drivers/fpga/fpga-mgr.c:600` does `request_firmware(&fw, image_name, ...)`);
  that request also lands in `fw_path[]` above, so the libdfx search-path
  trick is what makes the bare `${PN}.bin` resolvable in both flows.

Kernel config for this machine includes `CONFIG_OF_OVERLAY=y`,
`CONFIG_OF_CONFIGFS=y`, `CONFIG_FPGA=y`, `CONFIG_FPGA_REGION=y`,
`CONFIG_FPGA_MGR_ZYNQMP_FPGA=y`, `CONFIG_FW_LOADER=y` (built `.config` under
`build/tmp/work/k26_smk_kr_sdt-ec-linux/linux-xlnx/`).

**Current image status (fact):** the built `ec-sde` image manifest
(`build/tmp/deploy/images/k26-smk-kr-sdt/ec-sde-k26-smk-kr-sdt.manifest`)
contains `device-tree`, `kernel`, `grub-bootconf` etc. but **none** of
`k26-starter-kits`, `k26-starter-kits-extra`, `dfx-mgr`, `xmutil` — so today
the image ships neither the bundle nor a loader.

## 3. Roles of `k26-starter-kits` / `board-id-data-k26` / `k26-starter-kits-extra`

* **`k26-starter-kits`** (`meta-kria/meta-kria-bsp/recipes-firmware/kria-base-firmware/k26-starter-kits_1.0-2023.1.bb`
  + `kria-base-firmware.inc`): fetches Xilinx/kria-base-firmware @ `d512881`
  (branch `xlnx_rel_v2023.1`) with `FW_DIR = "k26_starter_kits"` and
  `inherit dfx_user_dts` + `inherit update-alternatives`. The upstream tree at
  that SRCREV contains exactly `k26_starter_kits/{k26_starter_kits.bit,
  k26_starter_kits.dtsi, shell.json}` (and the k24 equivalent) — i.e. the
  **factory "base" flat shell** that ships preloaded on the K26 starter kits.
  The packaged directory is `/usr/lib/firmware/xilinx/k26-starter-kits/`
  (`FW_INSTALL_DIR = ${PN}`), which matches dfx-mgr's boot-time default
  firmware name `k26-starter-kits` derived from the SOM EEPROM
  (`xlnx-firmware-load`: `fwname="${boardid}-starter-kits"`, `boardid = k26`).
* **`board-id-data-k26`** (`meta-kria/meta-kria-bsp/recipes-firmware/board-id-data/board-id-data-k26_1.0.bb`):
  not firmware for the PL at all. It fetches Xilinx/xlnx-board-id-data and
  `do_deploy`s pre-formatted **I2C EEPROM images** (SOM + carrier
  `*-eeprom*.bin`) used at manufacturing / by boot tooling;
  `kria-common.inc:12-15` forces it to build as an `EXTRA_IMAGEDEPENDS` of
  `xilinx-bootbin`. `nopackages`, deploy-only.
* **`k26-starter-kits-extra`** (layer `ec`,
  `recipes-firmware/kria-apps-firmware/k26-starter-kits-extra.bb`): the
  *extra* bundle from Xilinx/kria-apps-firmware @ `c111436`,
  `FW_DIR = "boards/kr260/bist"` — the KR260 **BIST** flat shell (PL design
  with the three Ethernet IPs: 10G XXV + two AXI Ethernet GEMs, plus camera
  ISP nodes) used as generic "extra" PL firmware for the KR260. Same
  `dfx_user_dts` machinery; its load name is `k26-starter-kits-extra`
  (`loadByName` / directory name). `k24-starter-kits-extra.bb` is the KD240
  analogue (`boards/kd240/bist`).
* **`inherit update-alternatives`** in `k26-starter-kits-extra.bb` (and in the
  k24 variant and meta-kria's `k26-starter-kits`): the class
  (`meta-voltumna/openembedded-core/meta/classes-recipe/update-alternatives.bbclass`)
  only creates symlink alternatives for names declared via
  `ALTERNATIVE:<pkg>` / `ALTERNATIVE_LINK_NAME[...]`. **No `ALTERNATIVE*`
  variable is defined anywhere in these recipes** (grep over
  `recipes-firmware/` and `meta-kria/.../recipes-firmware/`), so the inherit
  currently registers **no** alternatives and is functionally inert — each
  package installs into its own `xilinx/${PN}` directory and there are no
  file conflicts to arbitrate. The identical inherit in meta-kria's recipe
  suggests it was copied along the recipe lineage (inference, not fact).

## 4. Label availability in the `k26-smk-kr-sdt` base device tree

**The overlay needs exactly 10 external labels; the base tree used at runtime
provides all 10, but only in the carrier-merged dtb — the bare
`cortexa53-linux.dtb` would fail.**

Required labels — from `__fixups__` of the built overlay (decompiled with the
recipe's native dtc; `.../k26-starter-kits-extra/1.0/build/kr260-bist.dtbo`):

| label | used for |
| --- | --- |
| `fpga_full` | overlay target (`&fpga-full`: `firmware-name`, `resets`) |
| `zynqmp_reset` | `resets = <&zynqmp_reset 116>` |
| `amba` | overlay target for all PL nodes |
| `zynqmp_clk` | `pl0_ref_100M` parent clock (index 71) |
| `clk_25_0` | gem2 clock wizard parents |
| `clk_25_1` | gem3 clock wizard parent |
| `clk_74` | SLVS_EC rd_clk parent (237M derivation) |
| `clk_156` | 10G DMA/XXV clocks |
| `gic` | `interrupt-parent` of PL nodes |
| `gpio` | sensor/reset GPIOs |

Provided labels — `__symbols__` of the built base dtbs (decompiled from
`build/tmp/deploy/images/k26-smk-kr-sdt/devicetree/`):

| label | `cortexa53-linux.dtb` | `SMK-zynqmp-sck-kr-g-revB.dtb` |
| --- | --- | --- |
| `fpga_full` → `/fpga-region` | yes | yes |
| `zynqmp_clk` | yes | yes |
| `zynqmp_reset` | yes | yes |
| `gic` | yes | yes |
| `gpio` → `/axi/gpio@ff0a0000` | yes | yes |
| `amba` → `/axi` | yes | yes |
| `clk_25_0` | **missing** | yes → `/clock4` |
| `clk_25_1` | **missing** | yes → `/clock5` |
| `clk_156` | **missing** | yes → `/clock3` |
| `clk_74` | **missing** | yes → `/clock6` |

Where the clocks come from: `cortexa53-linux.dts` (meta-kria
`conf/dts/k26-smk-kr-sdt/`, the SDT domain DT from `CONFIG_DTFILE`) has no
trace of `clk_25_0/clk_25_1/clk_156/clk_74` (grep: no match in the whole
`conf/dts/k26-smk-kr-sdt/` set). They are modelled by the **KR260 carrier
board overlay** `zynqmp-sck-kr-g-revB.dtso` — as the upstream dtsi comments say
("modelled in board dts") — which `device-tree.bb` merges at build time with
`fdtoverlay -o ${UBOOT_DTFILE_PREFIX}-zynqmp-sck-kr-g-revB.dtb -i <base.dtb>
<dtbo>` (`meta-xilinx/meta-xilinx-core/recipes-bsp/device-tree/device-tree.bb:118-134`,
`UBOOT_DT_FILES` from `k26-smk-kr-sdt.conf:61`; deployed artifacts
`devicetree/{zynqmp-sck-kr-g-revB.dtbo,SMK-zynqmp-sck-kr-g-revB.dtb}`). The
merged dtb's root `compatible` is `xlnx,zynqmp-sk-kr260-revB` (the carrier's),
while the bare base is `xlnx,zynqmp-smk-k26*` (the SOM's).

Which tree is live at boot: the machine feeds
`BIF_PARTITION_IMAGE[device-tree] = ${DTB_FILE_PATH}` =
`.../SMK-zynqmp-sck-kr-g-revB.dtb` into BOOT.BIN
(`k26-smk-kr-sdt.conf:27,94`), and `device-tree.bb` installs the same file as
`/boot/system.dtb` (`device-tree.bb:145-155`). The ec boot path is U-Boot →
GRUB (`boot/EFI/BOOT/grub.cfg` in the built rootfs: `linux /Image LABEL=boot
root=/dev/sda2`, **no `devicetree` directive**), so the kernel receives
U-Boot's EFI control FDT = the BOOT.BIN device-tree partition = the
**carrier-merged** dtb. The standalone `cortexa53-linux.dtb` is also staged
(`/.osdir/<image>/boot/cortexa53-linux.dtb`) but is not what GRUB hands to the
kernel here.

**Verdict:** with the runtime base tree actually used by this build
(`SMK-zynqmp-sck-kr-g-revB.dtb`), **all 10 labels resolve and the
`kr260-bist` overlay can apply**. The risk is real but conditional: if the
base tree were ever the bare `cortexa53-linux.dtb`, the overlay would fail to
apply (`__fixups__` for `clk_25_0`, `clk_25_1`, `clk_156`, `clk_74` would not
resolve), because those four clocks exist only in the carrier overlay's
`/clock3..6` nodes.

## 5. Does the current build of `k26-starter-kits-extra` produce valid artifacts?

Yes — verified from the previous build left in `tmp/work` (no new build was
run). `build/tmp/work/k26_smk_kr_sdt-ec-linux/k26-starter-kits-extra/1.0/`
contains:

* `build/k26-starter-kits-extra.bin` — 6 592 044 B, produced by bootgen from
  `kr260-bist.bit` via the generated `k26-starter-kits-extra.bif`;
* `build/kr260-bist.dtbo` — 11 302 B, produced by `dtc -@` from the
  **patched** `kr260-bist.dtsi` (its `firmware-name` is
  `k26-starter-kits-extra.bin`, consistent with the installed bin name);
* `image/usr/lib/firmware/xilinx/k26-starter-kits-extra/` with the three
  final files `k26-starter-kits-extra.dtbo`, `k26-starter-kits-extra.bin`,
  `shell.json` (see §1);
* packaging outputs (`packages-split/`, `pkgdata/`, rpm deploy) — the recipe
  builds and packages cleanly end-to-end.

Note the dtbo's `__fixups__`/`__local_fixups__` structure is intact (decompiles
cleanly), and the upstream `kr260-bist.bit` is *not* shipped in the package —
only the bootgen `.bin` form.

---

## Summary of facts

1. `firmware-name` reconciliation = **patch the dtsi** to `"<PN>.bin"` and
   **name the bootgen output `<PN>.bin`**; the built dtbo is renamed to
   `<PN>.dtbo`. Installed names:
   `/usr/lib/firmware/xilinx/k26-starter-kits-extra/{k26-starter-kits-extra.bin,
   k26-starter-kits-extra.dtbo, shell.json}` (usr-merged → `/lib/firmware/...`).
   Upstream names (`kr260-bist.bin`, `kr260-bist.dtbo`, dir `kr260-bist`) are
   not used.
2. `FW_INSTALL_DIR` is **not** overridden by any of the recipes → the runtime
   dir is `/lib/firmware/xilinx/${PN}`, one dir per package. The kernel
   firmware lookup (`request_firmware`) searches only `fw_path[]`
   (`/lib/firmware[/updates[/…]]` + `firmware_class.path`); the bare
   `firmware-name` resolves because **dfx-mgr/libdfx** temporarily sets
   `firmware_class.path` to the bundle dir while applying the dtbo via
   `/sys/kernel/config/device-tree/overlays` and writing the basename to
   `/sys/class/fpga_manager/fpga0/firmware`. dfx-mgr discovers apps under
   `/lib/firmware/xilinx/<name>/` (`daemon.conf`), keyed by directory name /
   `loadByName` / EEPROM board-id.
3. `k26-starter-kits` = factory base flat shell (kria-base-firmware,
   `k26_starter_kits.{bit,dtsi,shell.json}`) installed as
   `xilinx/k26-starter-kits/`, matching dfx-mgr's EEPROM-derived default
   `k26-starter-kits`; `board-id-data-k26` = deploy-only EEPROM images for
   BOOT.BIN tooling; `k26-starter-kits-extra` = the KR260 BIST bundle
   (kria-apps-firmware `boards/kr260/bist`) installed as
   `xilinx/k26-starter-kits-extra/`. `inherit update-alternatives` defines no
   alternatives (no `ALTERNATIVE*` variables) → currently inert.
4. Base-dtb labels: `zynqmp_clk`, `zynqmp_reset`, `gic`, `gpio`, `fpga_full`,
   `amba` are in the SDT base; `clk_25_0`, `clk_25_1`, `clk_156`, `clk_74`
   exist **only** in the carrier overlay (`/clock3..6`) and are present in the
   runtime base tree (`SMK-zynqmp-sck-kr-g-revB.dtb` = base + carrier merged
   via `fdtoverlay`, shipped in BOOT.BIN and as `/boot/system.dtb`). The bare
   `cortexa53-linux.dtb` lacks them and would break the overlay.
5. The current build of `k26-starter-kits-extra` produces valid artifacts
   (bin + dtbo + shell.json packaged as shown); no build was needed. The
   current `ec-sde` image installs none of these packages and no
   dfx-mgr/xmutil loader.