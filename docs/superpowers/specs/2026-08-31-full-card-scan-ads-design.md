# PAULO PORTE CARTE — Full Card Scan & Advertising Design

## Goal
Improve Scan mode so a loyalty card is captured as a complete visual card while its barcode/QR is read, and add unobtrusive advertising without exposing administration controls to normal users.

## Scan mode
- Scanner opens a card-shaped camera framing experience.
- Capture and retain the complete visible card image (logo, colors and design), not only barcode data.
- Detect barcode or QR data and associate it with the captured card.
- Attempt merchant-name recognition; user can correct/complete the merchant name before saving.
- Show a preview before save.
- Saved card detail shows the complete captured card image plus a usable generated barcode/QR.
- Existing saved cards remain compatible.

## Advertising
- Client UI may show one lightweight banner and one horizontally scrolling text message.
- Advertising must not obstruct cards or scanning.
- Normal users must never see an Admin button or advertising-management controls.
- Advertising configuration includes banner image/reference, scrolling text, target/affiliate URL, enabled state and optional start/end dates.

## Administration
- Advertising administration is separated from the normal client UI and protected by separate administrator authentication.
- A centralized server/API is required for one administration change to propagate to all installed clients. Local-only administration is not sufficient for centralized campaigns.
- Client applications consume only the public active-advertising configuration.

## Security
- Existing encrypted local card storage, password/biometric unlock and screenshot protection remain intact.
- Admin credentials must not be embedded in the APK.
- Affiliate links accept only http/https destinations.

## Acceptance criteria
1. Scan mode captures a complete card visual and reads barcode/QR data.
2. User sees a preview before saving.
3. Saved cards retain their visual card image.
4. Client sees banner + scrolling text only when enabled.
5. Client cannot access or discover advertising administration from the normal app navigation.
6. Centralized advertising management is implemented through a server/API, not a shared password hard-coded in the APK.
