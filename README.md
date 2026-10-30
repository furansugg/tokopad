# Tokopad

A small offline Android shop notebook: checkout, physical/digital products, customers, dashboard, dynamic thermal receipts, and local backups. English by default; Indonesian available in Settings.

## Download

Open [Android Actions](https://github.com/furansugg/tokopad/actions/workflows/android.yml), choose a successful main-branch run, download `tokopad-signed-release`, unzip, install `app-release.apk` on Android 8.0 or newer. Android may ask permission to install from your file manager. Future releases use the same signing key and increasing version codes: install over the existing app without uninstalling; local data stays intact.

Older `tokopad-debug-apk` builds used temporary signing keys. Switching from those builds to the signed release usually requires **one migration**: export a backup, save it outside the app, uninstall the old debug app, install the signed release, restore the backup. Do not uninstall before saving the backup; uninstall removes local data. An APK cannot bypass Android's certificate check, and the old private key cannot be recovered from an APK. Older `tokopad-release-unsigned` artifacts cannot be installed. Debug builds now stay in test jobs, not downloadable installation artifacts.

### Production signing

Main-branch CI requires repository secrets `TOKOPAD_KEYSTORE_BASE64`, `TOKOPAD_KEYSTORE_PASSWORD`, `TOKOPAD_KEY_ALIAS`, and `TOKOPAD_KEY_PASSWORD`; missing secrets fail the release rather than silently generating another identity. The first is the base64 encoding of the fixed production keystore. Keep a private, durable copy of the keystore and passwords: losing the key can prevent future updates. Never commit them or rotate the key for routine releases. Signing files are decoded only on the GitHub runner and removed when the build step exits. Pull requests build unsigned releases without signing secrets.

The manual **Signed release** workflow additionally builds an AAB, using the `production` environment. Configure required reviewers if desired; any environment signing secrets must match the repository key, not introduce a different identity. CI verifies APK signatures and tests installation over the previous signed main-branch artifact on an emulator, checking the app UID, private file, and database survive. The first signed build checks same-version reinstall because no signed predecessor exists. Expired artifacts also fall back to same-version reinstall, explicitly noted in the run summary.

## Use

1. Add products in **Products**. Enable stock tracking for physical items if needed. Configure provider/package/nominal and code requirements for digital items.
2. Open **Checkout**, select a product. For PLN, choose a saved customer to fill the name/meter, or enter a new buyer; enter kWh and the token manually. New digital buyers are saved only when the sale completes successfully, together with the sale. Review payment, complete the sale.
3. In **Settings → General**, enter shop details, choose English/Indonesian and System/Light/Dark theme. In **Receipt**, choose shop name, logo, or both; edit header/footer and paper width, then save. Logo images are copied into app data, reduced to monochrome (maximum 384 × 192), and included in backups. No broad photo-library permission is required.
4. Open the printer shortcut in the header or **Settings → Printer**. Pair an ESC/POS Bluetooth Classic printer in Android settings. Refresh paired printers, select one (saved automatically), test the connection, then test print. Nearby-device permission and Bluetooth enablement are requested when needed. Connection tests close the socket afterward; printing establishes a fresh connection.
5. View sales in **History**. Reprint without duplicating sales; cancel mistakes with a reason. Cancellation adjusts records/stock, not external payments.
6. Export backups regularly. Keep backup files private: they contain customer details, tokens, and transactions, **without encryption**. Import first requires saving a safety backup, then confirmation to replace local data.

Digital sales are manual records, not automatic provider fulfillment. Noncash payment entries are not payment verification. Mixed baskets render each item according to its service type. Printer compatibility depends on actual hardware; test both paper widths before relying on them. Secure SPP connection is attempted first, then insecure SPP only if connection establishment fails before writing. A failed/uncertain write is never automatically retried: inspect the paper before retrying. BLE-only printers are explicitly unsupported. Close other printer apps and disconnect other phones if connection fails.

Printed receipts use short `TP-00000001` transaction numbers, not the internal checkout UUID. PLN-only receipts use centered shop branding/title, aligned buyer/meter/kWh/product fields, `=` section rules, a bold enlarged token grouped four digits at a time, charged line amount, total/payment, and a centered footer. A change row appears only when needed; subtotal/discount appear when applicable. Mixed baskets retain per-product details. Header/logo/footer are sale snapshots: changing branding affects new sales, not historical reprints. ESC/POS raster-logo and enlarged-text support depends on the printer. Printing allows a short connection warm-up and output drain before closing; this is a compatibility measure, not proof that a particular printer physically printed.

## Interface

Launcher and header use a lightweight vector interpretation of the supplied Tokopad mark, including adaptive/themed launcher support. Settings tabs fill the available width at normal text sizes and remain scrollable with larger fonts. Theme and language choices both use accessible radio selection.

Digital checkout previews up to three saved buyers in a bordered panel; search covers all saved names, phones and meter numbers. Selecting a buyer fills the form and collapses results; “Choose another buyer” reopens them. Busy dialogs center the indicator and message, including at larger font sizes.

PLN checkout includes a Copy button beside the meter/customer ID field. It copies the current value (including leading zeros), also after manual edits, and is disabled when empty.

The product form starts with product type and shows only relevant details: stock for tracked physical goods, face value for PLN/airtime, and package details for internet/vouchers/other services. PLN defaults its provider to PLN for new products. Cost and category are optional; code requirements remain configurable for all digital types. Validation uses the same numeric limits as storage. Hidden saved metadata is retained; converting a stocked product to digital requires confirmation before removing stock.

Japanese and other non-ASCII receipt lines are rendered using Android fonts and printed as monochrome ESC/POS raster images; ASCII lines keep the lightweight text path. Printer code-page/Japanese-font support is not required, but raster printing must be supported. Glyph coverage depends on Android's installed fonts; raster-heavy receipts send more data and may print slower. No fonts are downloaded.

The native Compose interface follows [shadcn/ui](https://ui.shadcn.com/docs/components) visual references: zinc neutrals, thin borders, restrained corners, compact tables, segmented tabs, and clear primary actions. No React/WebView runtime or shadcn package is embedded. Light/dark colors follow the saved choice or Android system theme. Checkout separates product selection from the cart/payment step; digital buyer details belong to each line. Settings separate General, Receipt, Printer, and Data rather than one long form.

## Development

- Kotlin, Compose Material 3, platform SQLite, coroutines. No backend, internet permission, analytics, or always-running services.
- SQLite transaction atomically writes a sale and stock changes; immutable JSON snapshots retain original sale details.
- Build, lint, unit tests, and APK packaging run **only in GitHub Actions**. Workflow pins action revisions, Gradle, dependencies, Java, and Android API levels.
- No Android Studio, SDK, emulator, or local Android build required. The workflow provisions Android tools on GitHub's runner, never this computer.
- Gradle Wrapper omitted intentionally: pinned `gradle/actions/setup-gradle` supplies Gradle 8.11.1 in CI.
- Unit checks cover receipt formatting and input/backup validation. GitHub's emulator checks stock rollback, checkout retries, cancellation, snapshots, backup replacement, and the cash/noncash checkout UI. Screenshot artifacts show the running app. Hardware and measured performance acceptance remain separate from a green CI run.

Full requirements: [PRD.md](PRD.md).

## Before production

- Test real Bluetooth printers (58 mm/80 mm), connection loss, denied permissions, long names, token wrapping, and repeated print attempts.
- Test checkout and backup restore on real Android devices, including low-memory/process interruption.
- Measure release cold start/search/save time and memory with 10,000 transactions; PRD targets are not benchmark results.
- Configure the production signing secrets and distribution channel. Do not uninstall the app without exporting a backup; local data is removed on uninstall.

### Deliberate limits

One store/device, whole-unit stock, IDR, manual digital fulfillment, manual payments. History displays up to 500 matches. A transactional 28 MiB data budget keeps JSON backups within the 64 MiB import/export limit; writes exceeding the budget are rejected without partial changes. Export before beginning a new dataset when that limit is reached. No cloud sync, staff accounts, partial returns, provider integration, or automatic refund.
