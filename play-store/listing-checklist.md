# Pre-submission checklist

Run top to bottom before hitting "Send for review" in Play Console.

## Store listing

- [ ] **App name** matches `metadata/android/en-US/title.txt`
- [ ] **Short description** matches `short_description.txt` (≤ 80 chars)
- [ ] **Full description** matches `full_description.txt` (≤ 4000 chars)
- [ ] **App icon** uploaded: `images/icon/icon.png` (512 × 512, 32-bit PNG)
- [ ] **Feature graphic** uploaded: `images/featureGraphic/featureGraphic.png` (1024 × 500)
- [ ] **Phone screenshots** (min 2, max 8) uploaded from `images/phoneScreenshots/`
- [ ] **Promo video** — upload `images/promoVideo/promo.mp4` to YouTube, paste URL into `video.txt`
- [ ] **Category:** Productivity
- [ ] **Tags:** AI assistant, OCR, organization, tasks, reminders
- [ ] **Contact email** set
- [ ] **Website** set to https://chartmann1590.github.io/Pocket-Assistant/
- [ ] **Privacy policy URL** set to https://chartmann1590.github.io/Pocket-Assistant/privacy.html

## App content

- [ ] **Privacy policy** — URL above
- [ ] **Ads** — No (default open-source build)
- [ ] **App access** — All features available without restriction
- [ ] **Content rating** — filled using `content-rating.md` (expect Everyone)
- [ ] **Target audience and content** — 18+ (or 13+ if you prefer)
- [ ] **News app** — No
- [ ] **COVID-19 contact tracing and status apps** — No
- [ ] **Data safety** — filled using `data-safety.md` (no data collected)
- [ ] **Government apps** — No
- [ ] **Financial features** — No

## Release

- [ ] **Upload keystore** generated and backed up securely (NOT in git)
- [ ] `keystore.properties` present at repo root (NOT in git)
- [ ] `./gradlew bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab`
- [ ] AAB uploaded to Production → Create new release
- [ ] Release notes pulled from `metadata/android/en-US/changelogs/1.txt`
- [ ] Countries / regions selected
- [ ] Release review checklist has no errors

## Technical

- [ ] `targetSdk` = 35 (or current Play requirement)
- [ ] `versionCode` bumped for each upload
- [ ] App bundle signs with the upload key Play Console expects
- [ ] Tested install from the Internal testing track at least once
