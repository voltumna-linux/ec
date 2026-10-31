# Convivenza tra load boot-time di `kr260-bist` e caricamento runtime via dfx-mgr

Nota di ricerca (solo fatti, nessuna decisione). Ticket:
`.scratch/kr260-pl-nic-firmware/issues/06-research-dfx-mgr-coexistence.md`.
Scope: con il meccanismo scelto nel ticket 03 (fpga-manager + `of-fpga-region`
applicato da `fpgautil` in un oneshot systemd a boot, load-only, prima di
`network-pre.target`), cosa succede quando a runtime si carica un altro design
via dfx-mgr (`xmutil`/`dfx-mgr-client`)? Si stabiliscono fatti su: (1) cosa fa
dfx-mgr su ZynqMP quando carica un full bitstream mentre una PL è già
programmata via `of-fpga-region`; (2) cosa documenta AMD sulla convivenza tra
load boot-time e DFX runtime; (3) se servono flags/regioni particolari perché i
due mondi non collidano; (4) cosa succede al warm reboot dopo un caricamento
runtime.

Convenzioni di citazione: i fatti dal workspace sono citati come `path:line`
relativi alla root del workspace Yocto (i sorgenti del kernel sono quelli
spediti in `build/tmp/work-shared/k26-smk-kr-sdt/kernel-source/`; i sorgenti
userspace sono quelli ai SRCREV pinnati dalle ricette, clonati da GitHub per
questa nota: Xilinx/dfx-mgr @ `076f8b30` e Xilinx/libdfx @ `2935992c`, identici
a `dfx-mgr_2026.2.bb` e `libdfx_2026.2.bb`). Le affermazioni esterne citano la
fonte primaria proprietaria (URL). Ogni fatto è marcato **[sorgente]** (letto
nel codice), **[AMD]** (documentato da AMD/Xilinx) o **[inferito]** (deduzione
da fatti verificati, non affermata da nessuna fonte). Nessun test su hardware:
tutto deriva da lettura di sorgenti e documentazione.

---

## 1. Il percorso di caricamento di dfx-mgr su ZynqMP

### 1.1 Routing: su ZynqMP dfx-mgr usa libdfx, non il percorso "user load"

**[sorgente]** La famiglia dell'fpga-manager è rilevata all'avvio leggendo
`/sys/class/fpga_manager/fpga0/name` (`src/daemon_helper.c:40-43,2386-2415`) e
`platform.use_user_load_path = is_user_load_platform(...)`
(`src/daemon_helper.c:2436-2438`); `is_user_load_platform()` è vero **solo** per
"Xilinx Zynq FPGA Manager" (Zynq-7000), non per ZynqMP
(`src/user_load.c:501-504`, `src/model.h:132-136`). Quindi su ZynqMP
(KR260/K26) un caricamento di pacchetto (`dfx-mgr-client -load/-loadByName`,
`xmutil loadapp`) passa da `load_accelerator_core()` → `load_accel()` → libdfx
(`src/daemon_helper.c:291-323`, `src/accel.c:90-123` → `src/sys/linux/accel.c:171-197`
→ `dfx_cfg_init()`/`dfx_cfg_load()` di libdfx). Il percorso "user"
(sysfs/configfs diretto, `src/user_load.c`) è usato solo per i comandi
"lightweight" `dfx-mgr-client -b/-o/-n/-R` (`src/daemon_helper.c:1900-2028`) e
su Zynq-7000.

### 1.2 La sequenza di un load flat su ZynqMP (libdfx)

**[sorgente]** Per un design flat (`shell.json` con `shell_type: XRT_FLAT` o
`PL_FLAT` — esattamente il nostro bundle, `{"shell_type": "XRT_FLAT",
"num_slots": "1"}`) `dfx_cfg_load()` esegue nell'ordine
(`libdfx/src/libdfx.c:711-823`):

1. **staging dell'immagine nell'fpga-manager, senza programmare**: apre
   `/dev/fpga0` (il misc device dell'fpga-manager, `drivers/fpga/fpga-mgr.c:1093-1096`),
   scrive i flag (`dfx_set_fpga_flags`, `libdfx.c:432-441` →
   `/sys/class/fpga_manager/fpga0/flags`) e passa il dmabuf fd con
   `ioctl(FPGA_IOCTL_LOAD_DMA_BUFF)` (`libdfx.c:747-762`, definizione ioctl in
   `libdfx.c:35`). Nel kernel l'ioctl si limita a `dma_buf_get()` e
   `mgr->flags = FPGA_MGR_CONFIG_DMA_BUF` (`fpga-mgr.c:932-946`): **nessuna
   programmazione avviene qui**.
2. **applicazione del dtbo via configfs**: `mkdir /sys/kernel/config/device-tree/overlays/<pkg>_image_<id>`
   e scrittura del nome del dtbo nell'attributo `path`
   (`libdfx.c:772-788`, `DTBO_ROOT_DIR` in `libdfx.c:55`).
3. **la programmazione della PL avviene dentro il notifier PRE_APPLY di
   `of-fpga-region`**: quando l'overlay targettato alla regione aggiunge
   `firmware-name`, `of_fpga_region_notify_pre_apply()` →
   `fpga_region_program_fpga()` → `fpga_mgr_load()`; con
   `FPGA_MGR_CONFIG_DMA_BUF` impostato, `fpga_mgr_load()` carica dal dmabuf
   staged (`fpga-mgr.c:625-637` → `fpga_dmabuf_load()` `:536-565`).
4. **verifiche**: stato `operating` e attributo `path` uguale al dtbo
   (`libdfx.c:793-815`); in caso contrario errore `-DFX_IMAGE_CONFIG_ERROR`.

Quindi dfx-mgr su ZynqMP **passa dall'fpga-manager e usa `of-fpga-region`**:
programmazione e enumerazione dei device sono la stessa operazione, innescata
dall'overlay. Non esiste un passo che "programma la PL sopra" senza toccare il
DT nel percorso pacchetto: senza dtbo con `firmware-name` la regione non
programma nulla (`of-fpga-region.c:259-263`: se l'overlay non ha `firmware-name`
viene accettato *senza* programmare).

### 1.3 Il guard della regione: un solo overlay "firmware-name" per regione alla volta

**[sorgente]** `of_fpga_region_parse_ov()` rifiuta qualsiasi overlay che
targetta una regione che già un overlay programmante:

```
if (region->info) {
        dev_err(dev, "Region already has overlay applied.\n");
        return ERR_PTR(-EINVAL);
}
```

(`kernel-source/drivers/fpga/of-fpga-region.c:202-205`; controllo ripetuto in
`of_fpga_region_notify_pre_apply()` `:310-313`). `region->info` viene impostato
quando un overlay con `firmware-name` viene applicato (`:315`) e viene liberato
**solo** alla rimozione di quell'overlay (`of_fpga_region_notify_post_remove()`
`:335-342`). Il notifier intercetta i frammenti il cui target è un nodo
`fpga-region` (`of_fpga_region_find(nd->target)`, `:379`); un errore dal
notifier in PRE_APPLY abortisce l'intero overlay (`overlay.c:162-186,939-941`
— nessun nodo aggiunto, nessun side effect). Il "target" dell'overlay flat di
Kria è il nodo radice `/fpga-region` (label `fpga_full`): il dtbo di
`kr260-bist` contiene `&fpga_full { firmware-name = "k26-starter-kits-extra.bin";
resets = <&zynqmp_reset 116>; }`
(`build/tmp/work/k26_smk_kr_sdt-ec-linux/k26-starter-kits-extra/1.0/build/kr260-bist.dtsi.pp:9-13`)
e **anche** quello di `k26-starter-kits` (il default di fabbrica) fa lo stesso:
`&fpga_full { firmware-name = "k26-starter-kits.bit.bin"; resets = ... }`
(Xilinx/kria-base-firmware @ `d512881`,
https://raw.githubusercontent.com/Xilinx/kria-base-firmware/d512881ad56fcb8ad3bffea543280c5b357bb9ea/k26_starter_kits/k26_starter_kits.dtsi).
Quindi due design flat **si escludono a vicenda sullo stesso nodo regione**,
indipendentemente da chi carica per primo.

Coerente con il modello "a regione" documentato nel binding
(`Documentation/devicetree/bindings/fpga/fpga-region.yaml:112-160`): l'overlay
targettato alla regione fa (1) disable bridge, (2) program FPGA, (3) enable
bridge, (4) accetta l'overlay, (5) popola i device figli; la regione radice
rappresenta la full reconfiguration, le regioni figlie (sotto bridge) le PR
(`:150-158`). Le regioni figlie nell'overlay non possono avere `firmware-name`
(`of-fpga-region.c:152-177`).

### 1.4 Esito concreto con `kr260-bist` già applicato a boot

Scenario: l'oneshot ha applicato `k26-starter-kits-extra.dtbo` (fpgautil →
configfs), la regione radice ha `region->info` impostato per tutta l'uptime.

* **Caricamento di un altro pacchetto flat via dfx-mgr** (`-loadByName`/`xmutil
  loadapp`): dfx-mgr non lo vieta (il suo stato `platform.active_base` è vuoto —
  il design boot-time non è tracciato dal demone, `src/daemon_helper.c:291-300`
  controlla solo i propri carichi) → libdfx staged il dmabuf → applicazione del
  dtbo → **PRE_APPLY rifiutato** (`Region already has overlay applied`) →
  l'intero overlay è abortito → il check `path` di libdfx fallisce
  (`libdfx.c:806-815`) → il load fallisce con errore. **La PL non viene
  riprogrammata** (la programmazione avviene solo dentro il PRE_APPLY) e **le
  NIC PL già probeate restano vive e agganciate**: le interfacce non spariscono,
  i driver non restano agganciati a device morti perché nessun device cambia.
  [sorgente]
* **Comando "lightweight" con dtbo** (`dfx-mgr-client -b <bin> -f Full -o <dtbo>
  -n <region>`): stesso esito, con in più una collisione sul nome della dir
  configfs: fpgautil usa di default `/configfs/device-tree/overlays/full`
  (`fpgautil.c:349-355`) e dfx-mgr-client `-n full` usa la stessa dir
  (`src/user_load.c:175-179`: "Overlay already exists in the live tree").
  Anche con nomi di regione diversi vale il guard di §1.3. [sorgente]
* **Unica eccezione: `dfx-mgr-client -b <bin> -f Full` *senza* dtbo** (percorso
  "user"): scrive il basename in
  `/sys/class/fpga_manager/fpga0/firmware` (`src/user_load.c:370-377` →
  `user_load_sysfs()` `:121-139` → `libdfx.c:407-411`); `firmware_store()` del
  kernel carica senza alcuna precondizione sullo stato corrente
  (`fpga-mgr.c:713-741`, `fpga_mgr_firmware_load()` `:583-614`: flag full/partial
  presi da `flags`, `fpga-mgr.h:86` = `FPGA_MGR_PARTIAL_RECONFIG` bit 0). Qui la
  PL viene riscritta **"sopra" senza toccare il device tree**: i nodi delle NIC
  di `kr260-bist` restano nell'albero vivo, i driver restano agganciati a
  hardware ormai morto, le interfacce restano visibili ma inutilizzabili
  **[sorgente per il meccanismo; l'effetto sui device è inferito]** — lo stato
  torna consistente solo rimuovendo l'overlay boot-time (che però toglie i nodi)
  o riapplicando `kr260-bist`.
* **Unload/teardown**: dfx-mgr/libdfx rimuovono **solo i propri** overlay
  (`dfx_cfg_remove()` fa `rmdir` delle dir `<pkg>_image_<id>`,
  `libdfx.c:905-960`; `user_unload_overlay()` `rmdir` della dir `<region>`,
  `src/daemon_helper.c:2031-2078`). Alla rimozione il POST_REMOVE libera la
  regione (`of-fpga-region.c:335-342`) e i nodi aggiunti spariscono dall'albero
  (device rimossi, driver unbindati). L'overlay creato a boot da `fpgautil` è
  invisibile a dfx-mgr: l'unico modo per toglierlo è `fpgautil -R` (o `rmdir`
  manuale della dir configfs), che — come dichiara lo stesso `fpgautil` —
  "is responsible for only removing the dtbo file from the livetree. it will not
  remove the PL logic from the FPGA region" (`fpgautil.c:129`): i nodi DT
  spariscono ma la PL resta programmata finché non la si riscrive. [sorgente]

**Sintesi Q1**: dfx-mgr non fa nessun "teardown/ricostruzione" della regione e
non "programma sopra" nel flusso pacchetto: il flusso ZynqMP è fpga-manager +
`of-fpga-region` atomici, e il guard `region->info` del kernel **rifiuta il
secondo load finché l'overlay boot-time è applicato** — la PL resta `kr260-bist`
e le NIC restano intatte. La distruzione silente ("PL riscritta, DT vecchio")
avviene solo col comando `-b` senza dtbo.

### 1.5 Corollari utili per la diagnostica

* `fpgautil` in flow overlay **termina con exit 0 anche se l'applicazione
  dell'overlay fallisce**: stampa solo "Failed to apply Overlay"
  (`fpgautil.c:408-412`) e alla fine `return 0` (`:485`); l'unico exit != 0
  rilevante è "Overlay already exists in the live tree" (`:366-370`, return 1).
  Il successo dell'unità oneshot **non** implica che il load sia riuscito. Nel
  dmesg la firma del caso qui studiato è
  `of-fpga-region ...: Region already has overlay applied.`. [sorgente]
* `dfx-mgr-client -R` e `fpgautil -R` tolgono solo l'overlay, mai la logica PL
  (README dfx-mgr, §2.2; `fpgautil.c:129`). [sorgente/AMD]

## 2. Cosa documenta AMD

### 2.1 Flusso legacy MPSoC vs flusso Kria

**[AMD]** "Bitstream Management on Kria SOM"
(https://xilinx.github.io/kria-apps-docs/creating_applications/2022.1/build/html/docs/bitstream_management.html):
il flusso legacy MPSoC è "bitstream loaded prior to the boot of the OS … its
device tree was loaded as part of the Linux boot device tree"; il flusso Kria è
"application-agnostic boot firmware that does not include any bitstream. The
bitstream is instead managed within the Linux file system and swapped
dynamically at runtime using xmutil/dfx-mgr" con DT-overlay allegato. In fondo:
"The dynamic bitstream management described here is how AMD is deploying and
managing the Kria Starter Kit pre-built OS and application designs. **For
production Kria SOM developers, you have full freedom to use the combination of
boot partitioning and bitstream management that matches your application
requirements.**" — AMD quindi non vieta flussi misti boot-time + runtime, ma
descrive solo il caso in cui **dfx-mgr è l'unico caricatore** (anche a boot: il
default è caricato da dfx-mgr stesso, v. §2.3). La nostra combinazione
(`fpgautil` a boot + dfx-mgr a runtime) **non è documentata da AMD**.
[inferito dall'assenza: nessuna delle pagine Kria/dfx-mgr esaminate la menziona]

### 2.2 Il modello "base caricata fuori da dfx-mgr"

**[AMD]** README di Xilinx/dfx-mgr @ `076f8b30`
(https://github.com/Xilinx/dfx-mgr/blob/076f8b30eb0055a3c2f150de18dc05bb7f1f0f16/README.md,
sezione "Lightweight use cases"): "A Partial load normally requires a Full
bitstream loaded through dfx-mgr first. **If the base design was configured
outside dfx-mgr (for example by the boot PDI), add `--force`** to force the
Partial load … While such a Partial is loaded, dfx-mgr refuses a Full bitstream
load and a `-load`/`-loadByName` of a PL package … unload the Partial first with
`-unload <ID>` (`-R` only removes the overlay)." Il codice corrispondente è
`is_base_externally_managed()` (`src/daemon_helper.c:245-248,283-287`) e i
controlli di `user_load()` (`:1973-1980`).

Fatti deducibili:

* dfx-mgr modella esplicitamente il caso "PL già configurata da qualcun altro"
  (boot PDI / caricamento boot-time), ma **solo per i *partial* (`--force`)** e
  solo se l'utente lo dichiara: dfx-mgr non rileva una base caricata via
  fpgautil a boot. [sorgente]
* Per shell **DFX** (`shell_type: PL_DFX`) esiste invece il flag
  `load_base_design: "no"` in `shell.json` per **non** ricaricare la base:
  "Set to 'no' to skip loading base design" (README, sezione `shell.json`;
  usato in `src/daemon_helper.c:382-415`). Questo è l'unico meccanismo
  documentato di convivenza "base caricata altrove + parziali via dfx-mgr".
  [sorgente/AMD]
* Per shell **flat** (`XRT_FLAT`/`PL_FLAT`, il nostro caso) **non esiste alcun
  flag equivalente**: il load flat programma sempre la PL intera e occupa "lo
  slot" unico (`src/daemon_helper.c:291-337`; un secondo flat mentre uno è
  attivo è rifiutato: "Unload previously loaded accelerator, no empty slot").
  [sorgente]

### 2.3 Il default a boot di dfx-mgr

**[sorgente]** La recipe `dfx-mgr_2026.2.bb`
(`meta-xilinx/meta-xilinx-core/dynamic-layers/openembedded-layer/recipes-bsp/dfx-mgr/dfx-mgr_2026.2.bb`)
installa ed **abilita** (`SYSTEMD_AUTO_ENABLE = "enable"`) tre unit:
`dfx-mgr.socket`, `dfx-mgr.service` e `dfx-mgr-fw-load.service`. Quest'ultima è
un oneshot `WantedBy=multi-user.target`, `ExecStart=/usr/bin/xlnx-firmware-load`
(`src/dfx-mgr-fw-load.service`): allo startup carica il pacchetto di default,
prima da `/etc/dfx-mgrd/default_firmware` (`default_accel` in `daemon.conf`) e
in subordine derivato dall'EEPROM board-id: per un SOM K26
`fwname="${boardid}-starter-kits"` → **`k26-starter-kits`**
(`src/scripts/xlnx-firmware-load`). Il README conferma: "Path to a file
containing the package name **to auto-load on daemon startup**".

**[sorgente]** Conseguenza per la convivenza *boot-time vs boot-time*: se
`dfx-mgr` finisse nell'immagine insieme al nostro oneshot, ad ogni boot ci
sarebbero **due caricamenti full in competizione** sulla stessa regione radice
(`kr260-bist` via fpgautil vs `k26-starter-kits` via xlnx-firmware-load). Chi
corre per secondo fallisce (guard di §1.3). L'ordinamento fattuale: il nostro
oneshot è `Before=network-pre.target` (early boot, ticket 03) mentre
`dfx-mgr-fw-load.service` è `multi-user.target` (tardi) → in pratica il nostro
vince; `xlnx-firmware-load` poi fallisce con exit 1 (unità fallita, visibile) e
fa exit 0 solo se il default non è installato (rv 254 "not found"). Questo
ordine non è stato verificato su target. [inferito]

### 2.4 Cosa AMD non documenta

* Nessun documento AMD (Kria wiki/apps-docs, README dfx-mgr, README
  `dfx_user_dts`) descrive la coesistenza di un load boot-time via `fpgautil` e
  load runtime via dfx-mgr; `README.dfx.user.dts.md` presenta i due tool come
  **alternativi**: "User can use fpgautil or dfx-mgr tool to load .bin/pdi and
  dtbo at runtime linux"
  (`meta-xilinx/docs/README.dfx.user.dts.md:428-431`). [sorgente]
* Nessuna prescrizione di "flags/regioni" per far convivere i due mondi oltre a
  `load_base_design: "no"` (solo PL_DFX) e `--force` (solo partial). [AMD]

## 3. Layer e kernel: flags, regioni, punti di collisione

### 3.1 Bundle e `dfx_user_dts.bbclass`: nessun flag di convivenza

**[sorgente]** `dfx_user_dts.bbclass` si occupa solo di packaging (rename a
`${PN}`, patch di `firmware-name`, install di `shell.json` verbatim,
`:378-381`): **non** esistono flag per full/partial o per la regione (grep
sulla classe: nessun riferimento a `partial`/`region`/`flags`). Il contenuto di
`shell.json` del bist è `{"shell_type": "XRT_FLAT", "num_slots": "1"}` (ticket
02): flat shell, slot unico, nessuna regione figlia PR. Il dtbo di `kr260-bist`
non definisce regioni figlie (`kr260-bist.dtsi.pp`, unico blocco
`&fpga_full`): **sopra `kr260-bist` non è caricabile alcun partial** — non c'è
un PRR dove metterlo; l'unica gestione possibile è "togliere l'overlay e
ricaricare un full". [sorgente]

### 3.2 Full vs partial: dove passano i flag

* **fpgautil**: `-f Full` = 0x0, `-f Partial` = 0x1 (`fpgautil.c:188-189`),
  scritti in `/sys/class/fpga_manager/fpga0/flags` (`:391`); il bit 0 è
  `FPGA_MGR_PARTIAL_RECONFIG` (`fpga-mgr.h:86`). Con `-o` (overlay) il flusso è
  sempre regione; i partial richiedono una regione esplicita (`-n PR0`,
  `fpgautil.c:349-354`). [sorgente]
* **libdfx/dfx-mgr**: `user_load_from_dir(..., is_partial)` passa 0 per i flat e
  base, 1 per gli RM di slot (`src/daemon_helper.c:306,382,520`); nel flusso
  dmabuf il bit partial viene però sovrascritto (`fpga_dmabuf_fd_get` imposta
  `mgr->flags = FPGA_MGR_CONFIG_DMA_BUF`, `fpga-mgr.c:944`) e la parzialità
  arriva dalla proprietà DT `partial-fpga-config` del dtbo
  (`of-fpga-region.c:223-224`). Nel flusso sysfs (fpgautil senza overlay) vale
  invece `flags` scritto prima (`fpga-mgr.c:596`). [sorgente]
* **Regione**: nel modello DT la full è la regione **radice** (`fpga-region`),
  i partial vanno su regioni **figlie** sotto bridge (`fpga-region.yaml:150-158`).
  Le regioni figlie nell'overlay non possono avere `firmware-name`
  (`of-fpga-region.c:152-177`). Servono dtbo *diversi* per base e RM. [sorgente]

### 3.3 Punti di collisione concreti (tabella)

| Livello | Meccanismo | Effetto con doppio load |
| --- | --- | --- |
| kernel `of-fpga-region` | guard `region->info` (`of-fpga-region.c:202-205`) | secondo overlay con `firmware-name` su `/fpga-region` **rifiutato** (`-EINVAL`), overlay intero abortito |
| configfs | nome dir = nome regione (`user_load.c:175-179`, `fpgautil.c:349-355`) | `-n full`/`fpgautil` default collisione su `.../overlays/full` ("Overlay already exists") |
| dfx-mgrd | slot unico flat (`daemon_helper.c:296-300`) | secondo *flat caricato da dfx-mgr* rifiutato ("no empty slot"); **non** rileva i load esterni |
| dfx-mgrd | stato in RAM (`base_designs[]`, `daemon_helper.c:47`) | il design boot-time è invisibile a `xmutil listapps/unload` |
| nodi DT sotto `/axi` | due overlay flat con nodi omonimi | non verificato (il guard regione rende il caso impossibile per i flat) |
| boot | `dfx-mgr-fw-load.service` vs oneshot fpgautil | due load full a boot; perde chi arriva secondo |

### 3.4 Nota: discrepanza binding ↔ codice

Il binding descrive un DTO con `firmware-name` come "a request to reprogram the
FPGA" (`fpga-region.yaml:143-147`), ma l'implementazione rifiuta la richiesta se
la regione ha già un overlay applicato (`of-fpga-region.c:202-205`): nella
pratica il "reprogramming" richiede prima la rimozione del DTO precedente.
Coerente con ciò, la documentazione dei tool parla di "unload/R" come prerequisito
per un nuovo load. [sorgente]

## 4. Warm reboot dopo un caricamento runtime

### 4.1 Stato software: ricostruito da zero

**[sorgente]** Al reboot il live tree DT è ricreato dal dtb consegnato da
U-Boot (partizione device-tree di BOOT.BIN, ticket 02), gli overlay configfs
sono vuoti (configfs è volatil), `region->info` è NULL, lo stato dell'fpga-manager
è fresco, e lo stato di dfx-mgrd è solo in memoria (`base_designs[]`) →
dimenticato. L'oneshot di ticket 03 riapplica `k26-starter-kits-extra.dtbo`:
PRE_APPLY pulito → full reconfiguration → `kr260-bist` **riparte pulito**. La
full reconfiguration non ha prerequisiti sullo stato precedente della PL
(`zynqmp-fpga.c:93-125` traduce solo i flag full/partial in comandi PM; il
percorso `firmware_store` non controlla lo stato corrente, `fpga-mgr.c:713-741`),
quindi funziona sia che la PL conservi o meno il design caricato a runtime.

### 4.2 Stato hardware della PL al reset

**[AMD]** UG1085 (Zynq UltraScale+ Device TRM, v1.8, cap. 12 "PL Clearing",
p. 273): "**A POR or soft reset, by default, clears the PL.**" Il comportamento
è governato dal circuito PROG_GATE (`PMU_GLOBAL.PS_CNTRL.PROG_GATE/PROG_ENABLE`,
Table 12-10): allo stato di power-on "the PS [is] able to reset the PL"; il
software può configurare il circuito perché "a soft reset to the PS does not
clear the PL"; gli eFUSE `PROG_GATE[2:0]` possono forzare la cancellazione
sempre. La Table 38-1 "System Reset Conditions" (cap. 38, p. 1126-1127) elenca
External/Internal SRST e `PMU_GLOBAL.ERROR_SRST` come "Same as POR" per gli
effetti, con colonna "Subject to the PROG_GATE effect for PL? Yes"; il
PS-only reset (`GLOBAL_RESET[PS_ONLY_RST]`) lascia "PL and LPD unaffected".
(Fonte: UG1085 v1.8, mirror https://0x04.net/~mwk/xidocs/ug/ug1085-zynq-ultrascale-trm.pdf;
la versione corrente è su docs.amd.com, non estraibile via HTTP semplice.)

**[inferito/non verificato]** Un `reboot` Linux su ZynqMP cade nella classe
soft/SRST → per default la PL viene resettata/cancellata, ma **non è stato
verificato** se FSBL/TF-A/PMU firmware di questa piattaforma programmino
PROG_GATE per trattenere la PL. In ogni caso, per il punto §4.1 la risposta non
cambia: il load boot-time riscrive la PL incondizionatamente.

### 4.3 Il default di dfx-mgr al boot

Se dfx-mgr è installato, anche il *suo* caricamento boot-time riparte ad ogni
boot (`dfx-mgr-fw-load.service`, §2.3) ed entra di nuovo in competizione con il
nostro oneshot: dopo un reboot "pulito" per `kr260-bist` vale solo se il default
dfx-mgr non è presente o non è installato (nell'immagine attuale non lo è,
ticket 02). [sorgente/inferito]

## 5. Limiti e cose non stabilite

* **Nessuna verifica su hardware o su immagine con dfx-mgr installato**: tutti i
  fatti sono da lettura di sorgenti (ai SRCREV pinnati) e documentazione.
* **Ordine reale dei due oneshot al boot** (nostro vs `dfx-mgr-fw-load.service`)
  dedotto dalle unit, non osservato; eventuali `After=`/`Conflicts=` aggiuntivi
  nell'immagine finale potrebbero cambiarlo.
* **PROG_GATE**: non verificato se il boot firmware di questa piattaforma
  configuri il trattenimento della PL al warm reset (UG1085 cap. 12/38).
* **Collisione di nodi omonimi sotto `/axi`** tra due overlay flat: il guard
  della regione la previene per i flat, quindi non è stato analizzato il
  comportamento di `of_changeset` per nomi duplicati nel caso generale.
* **Versioni**: i fatti sui sorgenti userspace valgono per dfx-mgr @ `076f8b30`
  e libdfx @ `2935992c` (rel-v2026.2, come pinnati dalle ricette del workspace);
  il kernel è quello costruito in `tmp/work-shared` (commit `414cefae`).
* Non è stato verificato se qualche bundle di `kria-apps-firmware` usi dtbo che
  *non* targettano `&fpga_full` (verificati: `kr260-bist`, `k26_starter_kits`).

---

## Fatti stabiliti (sintesi)

1. **Su ZynqMP dfx-mgr programma la PL solo tramite fpga-manager +
   `of-fpga-region`** (percorso libdfx: staging dmabuf su `/dev/fpga0` +
   dtbo via configfs; la programmazione avviene nel PRE_APPLY della regione).
   Non esiste "programmazione sopra senza toccare il DT" nel flusso pacchetto;
   esiste solo nel comando `dfx-mgr-client -b <bin>` senza dtbo (scrittura
   diretta di `fpga0/firmware`).
2. **Il kernel ammette un solo overlay "firmware-name" per regione alla volta**
   (`Region already has overlay applied`, `of-fpga-region.c:202-205`). Con
   l'overlay boot-time di `kr260-bist` applicato, un load runtime di un altro
   design flat via dfx-mgr viene **rifiutato prima di toccare la PL**: la PL
   resta `kr260-bist`, le NIC PL restano probeate e funzionanti, nessun driver
   resta agganciato a device morti. Due flat shell (es. `k26-starter-kits`) si
   escludono a vicenda perché i loro dtbo targettano entrambi `&fpga_full`.
3. **dfx-mgr non fa teardown di ciò che non ha caricato**: l'overlay boot-time è
   invisibile al demone (stato solo in RAM), `xmutil unload` non lo toglie; solo
   `fpgautil -R`/`rmdir` lo rimuove (togliendo i nodi DT, non la logica PL). Il
   caso distruttivo "PL riscritta con DT vecchio" esiste solo col comando `-b`
   senza dtbo.
4. **AMD documenta solo il flusso "dfx-mgr unico caricatore"** (anche a boot:
   `dfx-mgr-fw-load.service` carica `k26-starter-kits` da EEPROM/default_firmware
   ad ogni boot). La convivenza fpgautil-a-boot + dfx-mgr-a-runtime non è
   documentata; gli unici meccanismi di convivenza documentati sono
   `load_base_design: "no"` (solo shell PL_DFX) e `--force` per partial su base
   "configured outside dfx-mgr"; per le flat non esiste alcun flag.
5. **Servono regioni/flags particolari solo nel modello DFX**: full = regione
   radice, partial = regioni figlie con dtbo dedicati e `-f Partial`/`-n <PRR>`.
   `kr260-bist` è flat senza regioni figlie → nessun partial è caricabile sopra;
   l'unica alternativa a runtime è rimuovere l'overlay e caricare un altro full.
   `dfx_user_dts.bbclass` non espone nessun flag pertinente.
6. **Warm reboot: sì, il load boot-time riparte pulito** — stato software
   ricostruito (DT da BOOT.BIN, overlay vuoti, regione libera) e full
   reconfiguration incondizionata; per default UG1085 prevede che POR/soft
   reset cancellino la PL (PROG_GATE). Unica riserva: se dfx-mgr è installato,
   il suo load automatico a boot entra in competizione con l'oneshot e perde
   chi arriva secondo.