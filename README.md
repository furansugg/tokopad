# Tokopad

A small offline Android shop notebook: checkout, physical/digital products, customers, dashboard, dynamic thermal receipts, and local backups. English by default; Indonesian available in Settings.

## Download

Open [Android Actions](https://github.com/furansugg/tokopad/actions/workflows/android.yml), choose a successful main-branch run, download `tokopad-debug-apk`, unzip, install the APK on Android 8.0 or newer. Android may ask permission to install from your file manager.

Debug APKs are for evaluation. `tokopad-release-unsigned` contains the optimized release build but cannot be installed before signing. Do not distribute debug builds as production releases. Debug signing keys can change between CI runs: export a backup before reinstalling if Android rejects an update.

### Production signing

The manual **Signed release** workflow builds an APK and AAB from `main`, using the `production` environment. Configure required reviewers for that environment, then add secrets `TOKOPAD_KEYSTORE_BASE64`, `TOKOPAD_KEYSTORE_PASSWORD`, `TOKOPAD_KEY_ALIAS`, and `TOKOPAD_KEY_PASSWORD`. The first is the base64 encoding of your existing production keystore. Keep a private, durable copy of that keystore: losing it can prevent future app updates. Never commit it. Signing files are decoded only on the GitHub runner and removed when the build step exits. No production key is generated or uploaded by this project setup.

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
